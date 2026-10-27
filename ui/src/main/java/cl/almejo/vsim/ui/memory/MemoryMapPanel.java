package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.standard.MemoryAccessLog.MemoryEvent;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.core.simulation.MemoryMapFrame;
import cl.almejo.vsim.core.simulation.MemoryMapRequest;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The «Mapa de memoria» tab (spec §6, mockup §4): the whole memory last selected as a square-ish grid, one cell per
 * word or, above {@link #MAXIMUM_CELLS} words, per block of words (the wheel zooms in down to single words around the
 * mouse). Three modes: «Reciente» lights each access (blue read, orange write) and fades it in about a second of wall
 * time while running; paused, the accesses of the last step stay lit until the next step. «Acumulado» is a heat map
 * of the counts since the simulation started (log scale). «Valores» colours each word by its value (0 = empty).
 *
 * <p>The data comes only from the simulation thread's {@link MemoryMapFrame}s (through {@link OpenDocument}); the
 * panel asks for a frame only while it is showing. What lit up is worked out once per frame from the change in the
 * counts plus the frame's recent accesses, so a frame the EDT missed loses nothing; painting allocates a few colours
 * per paint, never per cell. EDT only.
 */
public final class MemoryMapPanel extends JPanel {

    /** What the cells show. */
    public enum Mode { RECENT, ACCUMULATED, VALUES }

    /** Memories up to this many words get one cell per word; bigger ones group words in this many cells. */
    static final int MAXIMUM_CELLS = 4096;
    /** How long a lit cell takes to fade while running. */
    static final long FADE_NANOS = 1_000_000_000L;
    /** Shades of a fading or heated cell (colours built once per paint). */
    private static final int LEVELS = 16;
    private static final int VALUE_HUES = 16;
    private static final byte NOT_LIT = 0;
    private static final byte LIT_READ = 1;
    private static final byte LIT_WRITE = 2;
    private static final String EMPTY_CARD = "empty";
    private static final String MAP_CARD = "map";

    private final OpenDocument document;
    private final MemoryMapActions actions;
    private final LongSupplier nanoClock;
    private final BooleanSupplier active;

    private final CardLayout cards = new CardLayout();
    private final JLabel emptyLabel = new JLabel(Messages.text("memory.map.empty"), SwingConstants.CENTER);
    private final JLabel titleLabel = new JLabel();
    private final JLabel cellSizeLabel = new JLabel();
    private final JLabel readLegend = new JLabel(Messages.text("memory.map.legend.read"),
            new Swatch(MemoryAccess.READ), SwingConstants.LEADING);
    private final JLabel writeLegend = new JLabel(Messages.text("memory.map.legend.write"),
            new Swatch(MemoryAccess.WRITE), SwingConstants.LEADING);
    private final JLabel modeLegend = new JLabel();
    private final JButton restoreButton = new JButton(Messages.text("memory.map.restore"));
    private final JButton dumpButton = new JButton(Messages.text("memory.map.dump"));
    private final Map<Mode, JToggleButton> modeButtons;
    private final MapGrid grid = new MapGrid();

    private Mode mode = Mode.RECENT;

    // The memory followed: null when there is none.
    private ElementId followed;
    private MemoryKind kind;
    private long words;
    private int width;
    private int addressBits;

    // The window of the map: cells of wordsPerCell words from firstAddress.
    private long firstAddress;
    private long wordsPerCell = 1;
    private int cells;
    /** What the map wants now; null without a memory. */
    private MemoryMapRequest request;
    /** What was last asked of the document (null: nothing), so it is asked only when it changes. */
    private MemoryMapRequest sentRequest;

    // Per request: the last frame and what lit up (arrays reused while the cell count stays).
    private MemoryMapFrame lastFrame;
    private boolean baselineReady;
    private long[] baselineReads = new long[0];
    private long[] baselineWrites = new long[0];
    private long[] litAtNanos = new long[0];
    private byte[] litAccess = new byte[0];
    private byte[] touched = new byte[0];
    private long maximumCount;
    private boolean running;
    /** While paused, the wall time the fades stopped at. */
    private long frozenNanos;

    public MemoryMapPanel(OpenDocument document, MemoryMapActions actions) {
        this(document, actions, System::nanoTime, null);
    }

    /** {@code active} tells whether frames are wanted (null: while the panel is showing). */
    MemoryMapPanel(OpenDocument document, MemoryMapActions actions, LongSupplier nanoClock, BooleanSupplier active) {
        super(new BorderLayout());
        this.document = Objects.requireNonNull(document, "document");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.active = active == null ? this::isShowing : active;
        this.modeButtons = Map.of(Mode.RECENT, modeButton(Mode.RECENT, "memory.map.mode.recent"),
                Mode.ACCUMULATED, modeButton(Mode.ACCUMULATED, "memory.map.mode.accumulated"),
                Mode.VALUES, modeButton(Mode.VALUES, "memory.map.mode.values"));
        modeButtons.get(Mode.RECENT).setSelected(true);

        JPanel content = new JPanel(cards);
        content.add(emptyState(), EMPTY_CARD);
        content.add(mapView(), MAP_CARD);
        add(content, BorderLayout.CENTER);
        restoreButton.setToolTipText(Messages.text("memory.map.restore.tooltip"));
        restoreButton.addActionListener(event -> {
            if (followed != null) {
                document.restoreMemory(followed);
            }
        });
        dumpButton.setToolTipText(Messages.text("memory.map.dump.tooltip"));
        dumpButton.addActionListener(event -> {
            if (followed != null) {
                actions.dump(followed);
            }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                sendRequest();
            }
        });
        showFollowed();
    }

    private JComponent emptyState() {
        JPanel empty = new JPanel(new BorderLayout());
        emptyLabel.setEnabled(false);
        empty.add(emptyLabel, BorderLayout.CENTER);
        return empty;
    }

    private JComponent mapView() {
        JPanel view = new JPanel(new MigLayout("fill, insets 8, gap 6, wrap 1", "[grow, fill]",
                "[][][][grow, fill][][]"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        view.add(titleLabel);
        JPanel modes = new JPanel(new MigLayout("insets 0, gap 0"));
        ButtonGroup group = new ButtonGroup();
        for (Mode each : Mode.values()) {
            group.add(modeButtons.get(each));
            modes.add(modeButtons.get(each));
        }
        view.add(modes);
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 6"));
        buttons.add(restoreButton);
        buttons.add(dumpButton);
        view.add(buttons);
        view.add(grid);
        JPanel legend = new JPanel(new MigLayout("insets 0, gap 10"));
        legend.add(readLegend);
        legend.add(writeLegend);
        view.add(legend);
        view.add(modeLegend);
        view.add(cellSizeLabel);
        return view;
    }

    private JToggleButton modeButton(Mode buttonMode, String key) {
        JToggleButton button = new JToggleButton(Messages.text(key));
        button.addActionListener(event -> setMode(buttonMode));
        return button;
    }

    // --- Following a memory -------------------------------------------------------------------------------------

    /**
     * The selection changed to {@code selected}: when it holds exactly one RAM or ROM the map follows it; otherwise it
     * keeps the memory it showed.
     */
    public void follow(Collection<ElementId> selected) {
        ElementId only = null;
        for (ElementId id : selected) {
            if (memoryComponent(id).isPresent()) {
                if (only != null) {
                    return; // two memories: keep the last one
                }
                only = id;
            }
        }
        if (only != null && !only.equals(followed)) {
            followed = only;
            words = 0; // a new memory: the whole of it, not zoomed
        }
        showFollowed();
    }

    /** The document changed: a deleted memory leaves the map; a renamed or resized one updates it. */
    public void documentChanged() {
        showFollowed();
    }

    /** A new snapshot was shown: takes the latest frame and repaints what moved. */
    public void refresh() {
        sendRequest();
        if (request == null) {
            return;
        }
        boolean wasFading = running && anythingFading();
        boolean changed = document.latestMemoryMap()
                .map(frame -> ingest(frame, document.latestSnapshot().running()))
                .orElse(false);
        if (changed || wasFading) {
            grid.repaint();
        }
    }

    /** Stops asking for frames; the panel is not used afterwards. */
    public void dispose() {
        followed = null;
        request = null;
        sendRequest();
    }

    private Optional<Component> memoryComponent(ElementId id) {
        return document.document().component(id).filter(component -> MemoryTypes.kindOf(component.typeId()).isPresent());
    }

    private void showFollowed() {
        Optional<Component> component = followed == null ? Optional.empty() : memoryComponent(followed);
        if (component.isEmpty()) {
            followed = null;
            words = 0;
            setRequest(null);
            cards.show((JPanel) getComponent(0), EMPTY_CARD);
            return;
        }
        Component memory = component.get();
        Map<String, Object> parameters = document.registry().typeOf(memory)
                .resolveParameters(memory.parameters());
        long newWords = MemoryTypes.words(parameters);
        kind = MemoryTypes.kindOf(memory.typeId()).orElseThrow();
        width = (Integer) parameters.get(MemoryTypes.WIDTH);
        addressBits = MemoryTypes.addressBits(parameters);
        if (newWords != words) {
            words = newWords;
            cells = (int) Math.min(words, MAXIMUM_CELLS);
            wordsPerCell = maximumWordsPerCell();
            firstAddress = 0;
        }
        titleLabel.setText(Messages.format("memory.map.title", displayName(memory), Messages.componentName(kind.id()),
                Messages.choiceName(MemoryTypes.WORDS, String.valueOf(words)), width));
        boolean ram = kind == MemoryKind.RAM;
        restoreButton.setVisible(ram);
        dumpButton.setVisible(ram);
        dumpButton.setEnabled(actions.canDump());
        cards.show((JPanel) getComponent(0), MAP_CARD);
        setRequest(new MemoryMapRequest(followed.value(), firstAddress, wordsPerCell, cells));
        updateLegend();
    }

    /** The name in the title: the label, or RAM1, ROM2… by order of placement among the memories of its kind. */
    private String displayName(Component memory) {
        if (!memory.label().isBlank()) {
            return memory.label();
        }
        int ordinal = 0;
        for (Component each : document.document().components()) {
            if (each.typeId().equals(memory.typeId())) {
                ordinal++;
            }
            if (each.id().equals(memory.id())) {
                break;
            }
        }
        return Messages.componentName(memory.typeId()) + ordinal;
    }

    private long maximumWordsPerCell() {
        return Math.max(1, words / MAXIMUM_CELLS);
    }

    private void setRequest(MemoryMapRequest newRequest) {
        if (!Objects.equals(newRequest, request)) {
            request = newRequest;
            resetFrameState();
            grid.repaint();
        }
        sendRequest();
    }

    /** Asks the document for frames of {@link #request} while the panel is active, for nothing otherwise. */
    private void sendRequest() {
        MemoryMapRequest wanted = request != null && active.getAsBoolean() ? request : null;
        if (!Objects.equals(wanted, sentRequest)) {
            sentRequest = wanted;
            document.setMemoryMapRequest(Optional.ofNullable(wanted));
        }
    }

    private void resetFrameState() {
        lastFrame = null;
        baselineReady = false;
        maximumCount = 0;
        int count = request == null ? 0 : request.cells();
        if (litAccess.length != count) {
            baselineReads = new long[count];
            baselineWrites = new long[count];
            litAtNanos = new long[count];
            litAccess = new byte[count];
            touched = new byte[count];
        } else {
            Arrays.fill(litAccess, NOT_LIT);
        }
    }

    // --- Frames ---------------------------------------------------------------------------------------------------

    /**
     * Takes {@code frame} (ignored unless it answers the current request); {@code running} is whether the simulation
     * runs. Returns whether anything to paint changed.
     */
    boolean ingest(MemoryMapFrame frame, boolean running) {
        long now = nanoClock.getAsLong();
        if (this.running && !running) {
            frozenNanos = now;
        }
        this.running = running;
        if (request == null || !frame.request().equals(request) || frame == lastFrame) {
            return false;
        }
        boolean anyTouched = findTouched(frame);
        if (running) {
            light(now);
        } else if (anyTouched || lastFrame != null && frame.time() != lastFrame.time()) {
            // a step: what the last one touched goes off, what this one touched stays lit until the next
            Arrays.fill(litAccess, NOT_LIT);
            light(now);
            frozenNanos = now;
        }
        long maximum = 0;
        for (int cell = 0; cell < frame.cells(); cell++) {
            baselineReads[cell] = frame.readsAt(cell);
            baselineWrites[cell] = frame.writesAt(cell);
            maximum = Math.max(maximum, baselineReads[cell] + baselineWrites[cell]);
        }
        maximumCount = maximum;
        baselineReady = true;
        lastFrame = frame;
        return true;
    }

    /**
     * Marks in {@link #touched} the cells read or written since the last frame: their counts went up (all counts
     * from zero when some went down: the simulation restarted), or a recent access names them. The first frame of a
     * request has no baseline: only its recent accesses count.
     */
    private boolean findTouched(MemoryMapFrame frame) {
        Arrays.fill(touched, NOT_LIT);
        boolean restarted = false;
        if (baselineReady) {
            for (int cell = 0; cell < frame.cells() && !restarted; cell++) {
                restarted = frame.readsAt(cell) < baselineReads[cell] || frame.writesAt(cell) < baselineWrites[cell];
            }
        }
        boolean any = false;
        if (baselineReady) {
            for (int cell = 0; cell < frame.cells(); cell++) {
                long readsBefore = restarted ? 0 : baselineReads[cell];
                long writesBefore = restarted ? 0 : baselineWrites[cell];
                if (frame.writesAt(cell) > writesBefore) {
                    touched[cell] = LIT_WRITE;
                    any = true;
                } else if (frame.readsAt(cell) > readsBefore) {
                    touched[cell] = LIT_READ;
                    any = true;
                }
            }
        }
        for (MemoryEvent event : frame.recent()) {
            long offset = event.address() - request.firstAddress();
            if (offset < 0) {
                continue;
            }
            long cell = offset / request.wordsPerCell();
            if (cell < request.cells() && touched[(int) cell] != LIT_WRITE) {
                touched[(int) cell] = event.access() == MemoryAccess.WRITE ? LIT_WRITE : LIT_READ;
                any = true;
            }
        }
        return any;
    }

    private void light(long now) {
        for (int cell = 0; cell < touched.length; cell++) {
            if (touched[cell] != NOT_LIT) {
                litAccess[cell] = touched[cell];
                litAtNanos[cell] = now;
            }
        }
    }

    /** True while some cell is still fading (so a running map keeps repainting until they are all off). */
    private boolean anythingFading() {
        long now = fadeClock();
        for (int cell = 0; cell < litAccess.length; cell++) {
            if (litAccess[cell] != NOT_LIT && now - litAtNanos[cell] < FADE_NANOS) {
                return true;
            }
        }
        return false;
    }

    /** The wall time the fades are at: now while running, the moment they stopped while paused. */
    private long fadeClock() {
        return running ? nanoClock.getAsLong() : frozenNanos;
    }

    /** How lit {@code cell} is in «Reciente»: 1 just accessed, 0 off. */
    double litIntensity(int cell) {
        return litIntensity(cell, fadeClock());
    }

    private double litIntensity(int cell, long now) {
        if (cell < 0 || cell >= litAccess.length || litAccess[cell] == NOT_LIT) {
            return 0;
        }
        double intensity = 1.0 - (double) (now - litAtNanos[cell]) / FADE_NANOS;
        return Math.max(0, Math.min(1, intensity));
    }

    /** The access {@code cell} is lit for, empty when it is off. */
    Optional<MemoryAccess> litAccess(int cell) {
        if (litIntensity(cell) <= 0) {
            return Optional.empty();
        }
        return Optional.of(litAccess[cell] == LIT_WRITE ? MemoryAccess.WRITE : MemoryAccess.READ);
    }

    // --- Zoom, click, tooltip -------------------------------------------------------------------------------------

    /**
     * {@code notches} of the wheel over {@code cell}: negative zooms in (half the words per cell each), positive out;
     * the word under the mouse stays under it as far as the memory's ends allow.
     */
    void zoomAt(int cell, int notches) {
        if (request == null || notches == 0) {
            return;
        }
        long address = request.addressOf(cell);
        long newWordsPerCell = wordsPerCell;
        for (int notch = 0; notch < Math.abs(notches); notch++) {
            newWordsPerCell = notches < 0 ? Math.max(1, newWordsPerCell / 2)
                    : Math.min(maximumWordsPerCell(), newWordsPerCell * 2);
        }
        if (newWordsPerCell == wordsPerCell) {
            return;
        }
        long first = address - cell * newWordsPerCell;
        first -= Math.floorMod(first, newWordsPerCell);
        long lastFirst = words - cells * newWordsPerCell;
        wordsPerCell = newWordsPerCell;
        firstAddress = Math.max(0, Math.min(first, lastFirst));
        setRequest(new MemoryMapRequest(followed.value(), firstAddress, wordsPerCell, cells));
        updateLegend();
    }

    private void open(int cell) {
        if (request != null && actions.canOpenEditor()) {
            actions.openEditor(followed, request.addressOf(cell));
        }
    }

    /** «0x01C · leída 412 veces · escrita 0 veces · vale 3A», or the range of a cell of many words. */
    String toolTipAt(int cell) {
        if (request == null || cell < 0 || cell >= request.cells()) {
            return null;
        }
        boolean known = lastFrame != null;
        long reads = known ? lastFrame.readsAt(cell) : 0;
        long writes = known ? lastFrame.writesAt(cell) : 0;
        String value = known ? hex(lastFrame.valueAt(cell), (width + 3) / 4) : "?";
        int addressDigits = Math.max(1, (addressBits + 3) / 4);
        long first = request.addressOf(cell);
        if (request.wordsPerCell() == 1) {
            return Messages.format("memory.map.tooltip.word", hex(first, addressDigits), reads, writes, value);
        }
        return Messages.format("memory.map.tooltip.range", hex(first, addressDigits),
                hex(first + request.wordsPerCell() - 1, addressDigits), reads, writes, value);
    }

    private static String hex(long value, int digits) {
        String text = Long.toHexString(value).toUpperCase(java.util.Locale.ROOT);
        return text.length() >= digits ? text : "0".repeat(digits - text.length()) + text;
    }

    // --- Modes and legend -------------------------------------------------------------------------------------------

    public void setMode(Mode newMode) {
        mode = Objects.requireNonNull(newMode, "newMode");
        modeButtons.get(newMode).setSelected(true);
        updateLegend();
        grid.repaint();
    }

    public Mode mode() {
        return mode;
    }

    private void updateLegend() {
        boolean accesses = mode != Mode.VALUES;
        readLegend.setVisible(accesses);
        writeLegend.setVisible(accesses);
        modeLegend.setText(Messages.text(switch (mode) {
            case RECENT -> "memory.map.legend.recent";
            case ACCUMULATED -> "memory.map.legend.accumulated";
            case VALUES -> "memory.map.legend.values";
        }));
        cellSizeLabel.setText(wordsPerCell == 1 ? Messages.text("memory.map.cellSize.one")
                : Messages.format("memory.map.cellSize.many", wordsPerCell));
    }

    // --- For tests ----------------------------------------------------------------------------------------------------

    Optional<ElementId> followed() {
        return Optional.ofNullable(followed);
    }

    Optional<MemoryMapRequest> request() {
        return Optional.ofNullable(request);
    }

    boolean showsEmptyState() {
        return followed == null;
    }

    String emptyText() {
        return emptyLabel.getText();
    }

    String title() {
        return titleLabel.getText();
    }

    JButton restoreButton() {
        return restoreButton;
    }

    JButton dumpButton() {
        return dumpButton;
    }

    MapGrid grid() {
        return grid;
    }

    // --- Colours, read at paint time ------------------------------------------------------------------------------------

    static Color accessColor(MemoryAccess access) {
        if (access == MemoryAccess.WRITE) {
            // orange, between FlatLaf's action red and yellow
            return blend(uiColor("Actions.Red", new Color(0xDB5860)), uiColor("Actions.Yellow", new Color(0xEDA200)),
                    0.5);
        }
        return uiColor("Actions.Blue", new Color(0x389FD6));
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private static Color blend(Color from, Color to, double amount) {
        double kept = 1 - amount;
        return new Color((int) Math.round(from.getRed() * kept + to.getRed() * amount),
                (int) Math.round(from.getGreen() * kept + to.getGreen() * amount),
                (int) Math.round(from.getBlue() * kept + to.getBlue() * amount));
    }

    /** A small square of an access colour for the legend. */
    private static final class Swatch implements Icon {
        private final MemoryAccess access;

        Swatch(MemoryAccess access) {
            this.access = access;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            graphics.setColor(accessColor(access));
            graphics.fillRect(x, y + 1, 9, 9);
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 11;
        }
    }

    // --- The grid -------------------------------------------------------------------------------------------------------

    /** The cells: square-ish, as big as the room allows. */
    final class MapGrid extends JComponent {

        MapGrid() {
            setPreferredSize(new Dimension(256, 256));
            setMinimumSize(new Dimension(64, 64));
            setToolTipText("");
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1) {
                        cellAt(event.getX(), event.getY()).ifPresent(MemoryMapPanel.this::open);
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    cellAt(event.getX(), event.getY())
                            .ifPresent(cell -> zoomAt(cell, event.getWheelRotation()));
                }
            };
            addMouseListener(mouse);
            addMouseWheelListener(mouse);
        }

        private int columns() {
            int count = Math.max(1, cells);
            int bits = 32 - Integer.numberOfLeadingZeros(count - 1);
            return Math.min(count, 1 << ((bits + 1) / 2));
        }

        private int rows() {
            int columns = columns();
            return Math.max(1, (cells + columns - 1) / columns);
        }

        private int cellSize() {
            return Math.max(1, Math.min(getWidth() / columns(), getHeight() / rows()));
        }

        private int originX() {
            return Math.max(0, (getWidth() - columns() * cellSize()) / 2);
        }

        /** The cell at a point of the grid, empty off the cells. */
        OptionalInt cellAt(int x, int y) {
            if (request == null) {
                return OptionalInt.empty();
            }
            int size = cellSize();
            int column = Math.floorDiv(x - originX(), size);
            int row = Math.floorDiv(y, size);
            int columns = columns();
            int cell = row * columns + column;
            if (column < 0 || column >= columns || row < 0 || cell >= request.cells()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(cell);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            OptionalInt cell = cellAt(event.getX(), event.getY());
            return cell.isPresent() ? toolTipAt(cell.getAsInt()) : null;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (request == null) {
                return;
            }
            Color background = getBackground() != null ? getBackground() : uiColor("Panel.background", Color.WHITE);
            Color foreground = getForeground() != null ? getForeground() : uiColor("Label.foreground", Color.BLACK);
            Color empty = blend(background, foreground, 0.10);
            Color[] readShades = shades(empty, accessColor(MemoryAccess.READ));
            Color[] writeShades = shades(empty, accessColor(MemoryAccess.WRITE));
            Color[] valueColors = mode == Mode.VALUES ? valueColors(background) : null;
            double logMaximum = Math.log1p(maximumCount);
            long now = fadeClock();
            int size = cellSize();
            int gap = size >= 6 ? 1 : 0;
            int columns = columns();
            int originX = originX();
            for (int cell = 0; cell < request.cells(); cell++) {
                Color color = switch (mode) {
                    case RECENT -> recentColor(cell, now, empty, readShades, writeShades);
                    case ACCUMULATED -> heatColor(cell, empty, readShades, writeShades, logMaximum);
                    case VALUES -> valueColor(cell, empty, valueColors);
                };
                graphics.setColor(color);
                graphics.fillRect(originX + cell % columns * size, cell / columns * size, size - gap, size - gap);
            }
        }

        private Color recentColor(int cell, long now, Color empty, Color[] readShades, Color[] writeShades) {
            double intensity = litIntensity(cell, now);
            if (intensity <= 0) {
                return empty;
            }
            int level = (int) Math.ceil(intensity * LEVELS);
            return litAccess[cell] == LIT_WRITE ? writeShades[level] : readShades[level];
        }

        private Color heatColor(int cell, Color empty, Color[] readShades, Color[] writeShades, double logMaximum) {
            if (lastFrame == null || logMaximum <= 0) {
                return empty;
            }
            long reads = lastFrame.readsAt(cell);
            long writes = lastFrame.writesAt(cell);
            if (reads + writes == 0) {
                return empty;
            }
            int level = (int) Math.max(1, Math.round(Math.log1p(reads + writes) / logMaximum * LEVELS));
            return writes > reads ? writeShades[level] : readShades[level];
        }

        private Color valueColor(int cell, Color empty, Color[] valueColors) {
            if (lastFrame == null) {
                return empty;
            }
            long value = lastFrame.valueAt(cell);
            return value == 0 ? empty : valueColors[(int) ((value * 0x9E3779B97F4A7C15L) >>> 60)];
        }

        /** {@link #LEVELS} + 1 steps from the empty colour to {@code full}. */
        private static Color[] shades(Color empty, Color full) {
            Color[] shades = new Color[LEVELS + 1];
            for (int level = 0; level <= LEVELS; level++) {
                shades[level] = blend(empty, full, (double) level / LEVELS);
            }
            return shades;
        }

        private static Color[] valueColors(Color background) {
            boolean dark = (background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114)
                    / 1000 < 128;
            Color[] colors = new Color[VALUE_HUES];
            for (int hue = 0; hue < VALUE_HUES; hue++) {
                colors[hue] = Color.getHSBColor((float) hue / VALUE_HUES, dark ? 0.55f : 0.45f, dark ? 0.75f : 0.88f);
            }
            return colors;
        }
    }
}
