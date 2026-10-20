package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.canvas.MarchingAnts;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/**
 * The Karnaugh map of one output (spec §3 steps 2 and 3): the variables in the corner, the Gray codes on the headers,
 * each cell's value large and its table row small. Hovering a cell lights its table row (through {@link HoverState})
 * and the row under the mouse in the table lights its cell. With groups on, the minimal cover is drawn as rounded
 * translucent rectangles in the palette colours, the explained group stronger; hovering a group explains it and a
 * click pins its explanation (another click, or a click outside every group, lets it go).
 * <p>
 * In the practice mode (spec §2, {@link PracticeModel#practising()}) the map shows the student's groups of the chosen
 * form instead, in drawing order, and never the solution. A drag from one cell to another draws the rectangle
 * covering both; dragging out of an edge goes on from the opposite edge (a wrap group). A click on a cell adds a
 * one-cell group, a click on a group selects it (the smallest under the pointer), a click off the cells clears the
 * selection, Ctrl+click on a corner adds the four corners, Supr deletes the selected group. From the keyboard the
 * arrows move a cell cursor, Shift+arrows stretch a rectangle from it (wrapping too), Enter adds it (only while the
 * keyboard is in use, else Enter is the wizard's «Siguiente») and Escape drops it, or else lets the selected group go
 * (with neither, Escape reaches the window). The selected group, the rectangle being drawn and the keyboard cursor are outlined with marching ants (along
 * their closed sides), which move only while something is outlined and the map shows. The cells the form still needs
 * get a dotted ring, the hinted cell a stronger one and the hinted group a dashed outline. A map the student gave up
 * on ({@link PracticeModel#givenUp}) keeps their groups drawn but is read-only: no gestures, keys or selection.
 * Colours are read from the theme at paint time.
 */
final class KarnaughMapView extends JComponent {

    static final int MINIMUM_CELL = 36;
    static final int MAXIMUM_CELL = 72;
    private static final int MARGIN = 8;
    private static final int HEADER = 46;
    private static final int BASE_INSET = 4;
    private static final int INSET_STEP = 4;
    /** How often the ants step, like the canvas selection's. */
    private static final int ANTS_MILLISECONDS = 90;
    /** The same width as the canvas selection outline. */
    private static final float ANTS_WIDTH = 1.5f;
    private static final double RING_INSET = 0.18;

    private final KarnaughModel model;
    private final HoverState hover;
    private final int output;
    private final boolean showGroups;
    private boolean followsPractice = true;
    private boolean compact;
    private final MarchingAnts ants = new MarchingAnts();
    private FrameTicker ticker = FrameTicker.swing(ANTS_MILLISECONDS);
    private BooleanSupplier showing = this::isShowing;
    /** The cell a mouse press started on (in practice), or null. */
    private KarnaughLayout.GridCell anchor;
    private boolean dragged;
    private DrawnGroup dragRectangle;
    private KarnaughLayout.GridCell cursor = new KarnaughLayout.GridCell(0, 0);
    /** How far Shift+arrows stretched the rectangle from the cursor (rows, columns), or null when none is. */
    private int[] stretch;
    /** Whether the keyboard is in use (the cursor is outlined) since the last mouse press. */
    private boolean keyboardActive;

    KarnaughMapView(KarnaughModel model, HoverState hover, int output, boolean showGroups) {
        this.model = model;
        this.hover = hover;
        this.output = output;
        this.showGroups = showGroups;
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                pointAt(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover.setRow(-1);
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (editable()) {
                    practicePress(event.getPoint(), (event.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0);
                } else if (!practising()) {
                    clickAt(event.getPoint());
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                practiceDrag(event.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                practiceRelease(event.getPoint());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                keyboardActive = false;
                stretch = null;
                updateAnimation();
                repaint();
            }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateAnimation();
            }
        });
        bindKeys();
        setOpaque(false);
        setFocusable(false);
    }

    int output() {
        return output;
    }

    private KarnaughLayout karnaughLayout() {
        return KarnaughLayout.of(model.inputCount());
    }

    /** The ticker that moves the ants (a hand-driven one in tests). */
    void setTicker(FrameTicker newTicker) {
        ticker.stop();
        ticker = newTicker;
        updateAnimation();
    }

    /** What tells whether the map shows (tests, which have no screen, say so themselves). */
    void setShowingCheck(BooleanSupplier newShowing) {
        showing = newShowing;
    }

    /** Makes this map show VSim's groups even while practising (the small solution map beside the student's). */
    void showSolutionOnly() {
        followsPractice = false;
        refreshPractice();
    }

    /** Makes this map ask for the smallest cells (the small solution map of the practice). */
    void setCompact() {
        compact = true;
        revalidate();
    }

    /** Whether this map shows the student's groups and takes their gestures. */
    boolean practising() {
        return showGroups && followsPractice && model.practice().practising();
    }

    /**
     * Whether the student can draw on this map: practising, and not given up on this output in the chosen form (a map
     * given up keeps the student's groups drawn but takes no gestures, keys or selection).
     */
    boolean editable() {
        return practising() && !model.practice().givenUp(output, model.options().form());
    }

    /**
     * The model or the mode changed: the map takes the keyboard only while the student can draw on it, a gesture in
     * progress ends when that stops, and the ants run or stop.
     */
    void refreshPractice() {
        fitCursor();
        boolean editable = editable();
        setFocusable(editable);
        if (!editable) {
            anchor = null;
            dragRectangle = null;
            stretch = null;
            keyboardActive = false;
        }
        updateAnimation();
        repaint();
    }

    /** The groups drawn, in legend order (empty without groups or for a constant 0). */
    List<KarnaughGroup> groups() {
        if (!showGroups) {
            return List.of();
        }
        List<KarnaughGroup> groups = new ArrayList<>();
        KarnaughLayout layout = karnaughLayout();
        for (Term term : model.minimization().outputs().get(output).terms()) {
            groups.add(layout.group(term));
        }
        return groups;
    }

    /**
     * What the map draws as groups: the student's groups of the chosen form while practising, else VSim's (as
     * {@link #groups()}).
     */
    List<DrawnGroup> drawnGroups() {
        if (practising()) {
            return model.practice().groups(output, model.options().form());
        }
        return groups().stream().map(group -> new DrawnGroup(group.cells(), group.rectangles())).toList();
    }

    /** The rectangle being drawn by a drag or stretched from the keyboard, if any. */
    Optional<DrawnGroup> pendingRectangle() {
        if (dragRectangle != null) {
            return Optional.of(dragRectangle);
        }
        fitCursor();
        if (stretch != null) {
            return Optional.of(rectangle(cursor, cursor.row() + stretch[0], cursor.column() + stretch[1]));
        }
        return Optional.empty();
    }

    /**
     * VSim's group drawn stronger (the one explained), outside the practice; none on the small solution map, which
     * explains nothing.
     */
    OptionalInt explainedGroup() {
        return followsPractice && hover.group() >= 0 ? OptionalInt.of(hover.group()) : OptionalInt.empty();
    }

    /** The keyboard's cell cursor. */
    KarnaughLayout.GridCell cursor() {
        return cursor;
    }

    double antsPhase() {
        return ants.phase();
    }

    /** The table row lit on the map (the row under the mouse here or in the table), if any. */
    OptionalInt highlightedRow() {
        return hover.row() >= 0 && hover.row() < model.rowCount() ? OptionalInt.of(hover.row()) : OptionalInt.empty();
    }

    /** Where the cells are for the current size: square cells, as large as fit, after the headers. */
    MapGeometry geometry() {
        KarnaughLayout layout = karnaughLayout();
        int header = headerSize();
        int available = Math.min((getWidth() - header - 2 * MARGIN) / layout.columns(),
                (getHeight() - header - 2 * MARGIN) / layout.rows());
        int cell = Math.clamp(available, MINIMUM_CELL, MAXIMUM_CELL);
        int left = MARGIN + header;
        int top = MARGIN + header;
        return new MapGeometry(layout, left, top, cell);
    }

    /** Room for the Gray codes and the corner with the variable names. */
    private static int headerSize() {
        return HEADER;
    }

    @Override
    public Dimension getPreferredSize() {
        KarnaughLayout layout = karnaughLayout();
        if (compact) {
            return new Dimension(2 * MARGIN + headerSize() + layout.columns() * MINIMUM_CELL,
                    2 * MARGIN + headerSize() + layout.rows() * MINIMUM_CELL);
        }
        int cell = (MINIMUM_CELL + MAXIMUM_CELL) / 2;
        return new Dimension(2 * MARGIN + headerSize() + layout.columns() * cell + cell,
                2 * MARGIN + headerSize() + layout.rows() * cell + cell);
    }

    /** The mouse at {@code point}: lights its cell's row and, with groups, explains the group under it. */
    void pointAt(Point point) {
        MapGeometry geometry = geometry();
        hover.setRow(geometry.tableRowAt(point).orElse(-1));
        if (showGroups && followsPractice && !practising()) {
            groupAt(geometry, point).ifPresent(hover::setGroup);
        }
    }

    /**
     * A click at {@code point}, with groups: pins the group under it (or lets it go), unpins outside every group. The
     * small solution map explains nothing, so it ignores clicks.
     */
    void clickAt(Point point) {
        if (!showGroups || !followsPractice) {
            return;
        }
        OptionalInt group = groupAt(geometry(), point);
        if (group.isPresent()) {
            hover.togglePin(group.getAsInt());
        } else {
            hover.unpin();
        }
    }

    /** The topmost group under {@code point}: groups are painted largest first, so the smallest one wins. */
    private OptionalInt groupAt(MapGeometry geometry, Point point) {
        if (!geometry.grid().contains(point)) {
            return OptionalInt.empty();
        }
        List<DrawnGroup> groups = drawnGroups();
        List<Integer> order = paintOrder(groups);
        for (int position = order.size() - 1; position >= 0; position--) {
            int index = order.get(position);
            for (Rectangle2D piece : geometry.pieces(groups.get(index), inset(position))) {
                if (piece.contains(point)) {
                    return OptionalInt.of(index);
                }
            }
        }
        return OptionalInt.empty();
    }

    /** Group indices in painting order: largest first, ties by index. */
    private static List<Integer> paintOrder(List<DrawnGroup> groups) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingInt((Integer index) -> -groups.get(index).cells().size())
                .thenComparingInt(index -> index));
        return order;
    }

    /** A press while practising: Ctrl on a corner adds the corners, on a cell it starts a drag, elsewhere it clears. */
    private void practicePress(Point point, boolean control) {
        requestFocusInWindow();
        keyboardActive = false;
        stretch = null;
        MapGeometry geometry = geometry();
        anchor = null;
        dragRectangle = null;
        dragged = false;
        if (!geometry.grid().contains(point)) {
            model.practice().clearSelection();
        } else {
            KarnaughLayout.GridCell cell = new KarnaughLayout.GridCell(geometry.rowAt(point.y),
                    geometry.columnAt(point.x));
            if (control) {
                if (isCorner(cell)) {
                    karnaughLayout().corners().ifPresent(this::addGroup);
                }
            } else {
                anchor = cell;
            }
        }
        updateAnimation();
        repaint();
    }

    private void practiceDrag(Point point) {
        if (anchor == null || !editable()) {
            return;
        }
        MapGeometry geometry = geometry();
        int row = geometry.rowAt(point.y);
        int column = geometry.columnAt(point.x);
        if (row != anchor.row() || column != anchor.column()) {
            dragged = true;
        }
        if (dragged) {
            dragRectangle = rectangle(anchor, row, column);
            updateAnimation();
            repaint();
        }
    }

    /** The end of a drag adds its rectangle; a click selects the group under it or adds a one-cell group. */
    private void practiceRelease(Point point) {
        if (anchor == null || !editable()) {
            return;
        }
        KarnaughLayout.GridCell start = anchor;
        DrawnGroup drawn = dragRectangle;
        anchor = null;
        dragRectangle = null;
        if (dragged) {
            addGroup(drawn);
        } else {
            OptionalInt group = groupAt(geometry(), point);
            if (group.isPresent()) {
                model.practice().select(group.getAsInt());
            } else {
                addGroup(karnaughLayout().drawn(start.row(), start.column(), 1, 1));
            }
        }
        dragged = false;
        updateAnimation();
        repaint();
    }

    /** Adds a group of this map's output in the chosen form; the selection goes (the new group is not selected). */
    private void addGroup(DrawnGroup group) {
        PracticeModel practice = model.practice();
        practice.clearSelection();
        practice.add(output, model.options().form(), group);
    }

    private boolean isCorner(KarnaughLayout.GridCell cell) {
        KarnaughLayout layout = karnaughLayout();
        return (cell.row() == 0 || cell.row() == layout.rows() - 1)
                && (cell.column() == 0 || cell.column() == layout.columns() - 1);
    }

    /**
     * The rectangle from {@code from} to the pointer cell {@code (row, column)}, which may lie past the map: past the
     * end the rectangle runs on over the edge, before the start its first cell moves to the other end; it never gets
     * larger than the map.
     */
    private DrawnGroup rectangle(KarnaughLayout.GridCell from, int row, int column) {
        KarnaughLayout layout = karnaughLayout();
        int[] rows = axis(layout.rows(), from.row(), row);
        int[] columns = axis(layout.columns(), from.column(), column);
        return layout.drawn(rows[0], columns[0], rows[1], columns[1]);
    }

    /** The first position and the span of the cyclic run from {@code anchor} to {@code pointer} on an axis. */
    private static int[] axis(int size, int anchor, int pointer) {
        int first = pointer >= anchor ? anchor : Math.floorMod(pointer, size);
        int span = Math.min(Math.abs(pointer - anchor) + 1, size);
        return new int[]{span == size ? 0 : first, span};
    }

    private void bindKeys() {
        bind("practiceUp", () -> moveCursor(-1, 0), KeyEvent.VK_UP, 0);
        bind("practiceDown", () -> moveCursor(1, 0), KeyEvent.VK_DOWN, 0);
        bind("practiceLeft", () -> moveCursor(0, -1), KeyEvent.VK_LEFT, 0);
        bind("practiceRight", () -> moveCursor(0, 1), KeyEvent.VK_RIGHT, 0);
        bind("practiceStretchUp", () -> stretchRectangle(-1, 0), KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchDown", () -> stretchRectangle(1, 0), KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchLeft", () -> stretchRectangle(0, -1), KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchRight", () -> stretchRectangle(0, 1), KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        // Enter adds only while the keyboard is in use (the cursor shows); after a click it reaches «Siguiente».
        bindKeys("practiceAdd", this::addKeyboardRectangle, () -> keyboardActive || stretch != null,
                KeyEvent.VK_ENTER);
        bindKeys("practiceDelete", this::deleteSelected, () -> model.practice().selected().isPresent(),
                KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE);
        bindKeys("practiceCancel", this::cancelKeyboardRectangle,
                () -> stretch != null || model.practice().selected().isPresent(), KeyEvent.VK_ESCAPE);
    }

    private void bind(String name, Runnable action, int keyCode, int modifiers) {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        putAction(name, action, () -> true);
    }

    /** Binds {@code keyCodes} (no modifiers) to {@code action}, on only while practising and {@code condition}. */
    private void bindKeys(String name, Runnable action, BooleanSupplier condition, int... keyCodes) {
        for (int keyCode : keyCodes) {
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        }
        putAction(name, action, condition);
    }

    /**
     * Off outside the practice mode (and when there is nothing to do), so the keys reach the step and the window as
     * before: Escape still lets a pinned group go or closes the wizard.
     */
    private void putAction(String name, Runnable action, BooleanSupplier condition) {
        getActionMap().put(name, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return editable() && condition.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                if (isEnabled()) {
                    keyboardActive = true;
                    action.run();
                    updateAnimation();
                    repaint();
                }
            }
        });
    }

    private void moveCursor(int rowDelta, int columnDelta) {
        fitCursor();
        KarnaughLayout layout = karnaughLayout();
        stretch = null;
        cursor = new KarnaughLayout.GridCell(Math.floorMod(cursor.row() + rowDelta, layout.rows()),
                Math.floorMod(cursor.column() + columnDelta, layout.columns()));
    }

    private void stretchRectangle(int rowDelta, int columnDelta) {
        fitCursor();
        KarnaughLayout layout = karnaughLayout();
        if (stretch == null) {
            stretch = new int[2];
        }
        stretch[0] = Math.clamp(stretch[0] + rowDelta, 1 - layout.rows(), layout.rows() - 1);
        stretch[1] = Math.clamp(stretch[1] + columnDelta, 1 - layout.columns(), layout.columns() - 1);
    }

    /** Enter: adds the stretched rectangle, or the cursor's cell when none is stretched. */
    private void addKeyboardRectangle() {
        DrawnGroup group = pendingRectangle()
                .orElse(karnaughLayout().drawn(cursor.row(), cursor.column(), 1, 1));
        stretch = null;
        addGroup(group);
    }

    /** Esc: drops the stretched rectangle, else lets the selected group go (only then Esc reaches the window). */
    private void cancelKeyboardRectangle() {
        if (stretch != null) {
            stretch = null;
        } else {
            model.practice().clearSelection();
        }
    }

    /**
     * Fewer inputs make a smaller map while this view stays: a cursor left outside it goes back to the first cell and
     * the stretched rectangle goes.
     */
    private void fitCursor() {
        KarnaughLayout layout = karnaughLayout();
        if (cursor.row() >= layout.rows() || cursor.column() >= layout.columns()) {
            cursor = new KarnaughLayout.GridCell(0, 0);
            stretch = null;
        }
    }

    private void deleteSelected() {
        OptionalInt selected = model.practice().selected();
        if (selected.isPresent()) {
            model.practice().remove(output, model.options().form(), selected.getAsInt());
        }
    }

    /** Whether the ants have something to outline: the selected group, a rectangle being drawn or the cursor. */
    private boolean outlining() {
        if (!editable()) {
            return false;
        }
        return pendingRectangle().isPresent() || keyboardActive || selectedHere().isPresent();
    }

    /** The selected group, when this map's output is the one shown (the selection belongs to it). */
    private OptionalInt selectedHere() {
        if (model.selectedOutput() != output) {
            return OptionalInt.empty();
        }
        OptionalInt selected = model.practice().selected();
        if (selected.isPresent() && selected.getAsInt() >= drawnGroups().size()) {
            return OptionalInt.empty();
        }
        return selected;
    }

    /** Starts the ants when something is outlined on a showing map, stops them otherwise. */
    private void updateAnimation() {
        boolean wanted = outlining() && showing.getAsBoolean();
        if (wanted && !ticker.isRunning()) {
            ticker.start(this::antsTick);
        } else if (!wanted && ticker.isRunning()) {
            ticker.stop();
        }
    }

    private void antsTick() {
        updateAnimation();
        if (ticker.isRunning()) {
            ants.advance();
            repaint();
        }
    }

    /** Overlapping groups sit at different insets so every outline stays visible. */
    private static int inset(int paintPosition) {
        return BASE_INSET + (paintPosition % 3) * INSET_STEP;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            MapGeometry geometry = geometry();
            paintHeaders(graphics2D, geometry);
            paintCellBackgrounds(graphics2D, geometry);
            if (showGroups) {
                paintGroups(graphics2D, geometry);
            }
            paintCellTexts(graphics2D, geometry);
            if (practising() && model.problem().isEmpty()) {
                paintPracticeOverlays(graphics2D, geometry);
            }
            paintHighlight(graphics2D, geometry);
        } finally {
            graphics2D.dispose();
        }
    }

    private void paintHeaders(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
        Rectangle grid = geometry.grid();
        int cell = geometry.cellSize();
        Font base = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        Font codes = base.deriveFont(Font.PLAIN, base.getSize2D());
        Font names = base.deriveFont(Font.BOLD, base.getSize2D());
        graphics.setColor(WizardColors.muted());
        graphics.setFont(codes);
        List<String> columnCodes = layout.columnCodes();
        for (int column = 0; column < columnCodes.size(); column++) {
            centered(graphics, columnCodes.get(column),
                    new Rectangle(grid.x + column * cell, grid.y - 20, cell, 18));
        }
        List<String> rowCodes = layout.rowCodes();
        for (int row = 0; row < rowCodes.size(); row++) {
            centered(graphics, rowCodes.get(row), new Rectangle(grid.x - 24, grid.y + row * cell, 22, cell));
        }
        // The corner: the column variables above the diagonal, the row variables below it.
        graphics.setColor(WizardColors.gridLine());
        graphics.drawLine(grid.x - 44, grid.y - 44, grid.x, grid.y);
        graphics.setColor(WizardColors.foreground());
        graphics.setFont(names);
        centered(graphics, variables(layout.columnVariables()), new Rectangle(grid.x - 22, grid.y - 44, 40, 20));
        centered(graphics, variables(layout.rowVariables()), new Rectangle(grid.x - 50, grid.y - 24, 28, 20));
    }

    private String variables(List<Integer> inputs) {
        List<String> names = new ArrayList<>();
        boolean allShort = true;
        for (int input : inputs) {
            String name = model.inputNames().get(input).strip();
            names.add(name);
            allShort &= name.length() <= 1;
        }
        return String.join(allShort ? "" : ",", names);
    }

    private void paintCellBackgrounds(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Rectangle cell = geometry.cell(row, column);
                graphics.setColor(WizardColors.cellBackground());
                graphics.fill(cell);
                graphics.setColor(WizardColors.gridLine());
                graphics.draw(cell);
            }
        }
    }

    /** The values over the groups, so they stay readable; with groups, the values never grouped are muted. */
    private void paintCellTexts(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
        boolean sumOfProducts = model.options().form() == Minimization.Form.SUM_OF_PRODUCTS;
        LogicValue neverGrouped = sumOfProducts ? LogicValue.ZERO : LogicValue.ONE;
        Font base = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        Font value = base.deriveFont(Font.BOLD, geometry.cellSize() * 0.38f);
        Font small = base.deriveFont(Font.PLAIN, Math.max(9f, geometry.cellSize() * 0.2f));
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Rectangle cell = geometry.cell(row, column);
                int tableRow = layout.tableRow(row, column);
                LogicValue cellValue = model.value(output, tableRow);
                graphics.setColor(showGroups && cellValue == neverGrouped ? WizardColors.muted()
                        : WizardColors.foreground());
                graphics.setFont(value);
                centered(graphics, valueText(cellValue), cell);
                graphics.setColor(WizardColors.muted());
                graphics.setFont(small);
                graphics.drawString(String.valueOf(tableRow), cell.x + 4, cell.y + 3
                        + graphics.getFontMetrics().getAscent());
            }
        }
    }

    static String valueText(LogicValue value) {
        return switch (value) {
            case ZERO -> "0";
            case ONE -> "1";
            case DONT_CARE -> "X";
        };
    }

    /**
     * The groups: VSim's (the explained one stronger, colours shared between maps like the legend's), or while
     * practising the student's in drawing-order colours (the selected one stronger).
     */
    private void paintGroups(Graphics2D graphics, MapGeometry geometry) {
        boolean practice = practising();
        List<DrawnGroup> groups = drawnGroups();
        OptionalInt selected = practice ? selectedHere() : OptionalInt.empty();
        List<Integer> order = paintOrder(groups);
        boolean dark = WizardColors.dark();
        Shape clip = graphics.getClip();
        graphics.clip(geometry.grid());
        float arc = geometry.cellSize() * 0.45f;
        for (int position = 0; position < order.size(); position++) {
            int index = order.get(position);
            boolean explained = practice ? selected.isPresent() && selected.getAsInt() == index
                    : explainedGroup().equals(OptionalInt.of(index));
            int colour = practice ? index : model.colorIndex(output, index);
            for (Rectangle2D piece : geometry.pieces(groups.get(index), inset(position))) {
                RoundRectangle2D shape = new RoundRectangle2D.Double(piece.getX(), piece.getY(), piece.getWidth(),
                        piece.getHeight(), arc, arc);
                graphics.setColor(GroupPalette.fill(colour, dark, explained));
                graphics.fill(shape);
                graphics.setColor(GroupPalette.color(colour, dark));
                graphics.setStroke(new BasicStroke(explained ? 3.5f : 2f));
                graphics.draw(shape);
            }
        }
        graphics.setClip(clip);
    }

    /**
     * The practice marks over the values: a dotted ring on each cell the form still needs, a strong ring on the hinted
     * cell and a dashed outline around the hinted group (shown output only), then the ants around the selected group,
     * the rectangle being drawn and the keyboard cursor.
     */
    private void paintPracticeOverlays(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
        PracticeModel practice = model.practice();
        graphics.setColor(WizardColors.uncoveredRing());
        graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{1.5f, 3.5f}, 0f));
        for (int tableRow : practice.result(output, model.options().form()).uncoveredRows()) {
            graphics.draw(ring(geometry, layout.cell(tableRow)));
        }
        if (model.selectedOutput() == output) {
            OptionalInt hintRow = practice.hintRow();
            if (hintRow.isPresent()) {
                graphics.setColor(WizardColors.hintRing());
                graphics.setStroke(new BasicStroke(3f));
                graphics.draw(ring(geometry, layout.cell(hintRow.getAsInt())));
            }
            Optional<Term> hintGroup = practice.hintGroup();
            if (hintGroup.isPresent()) {
                graphics.setColor(WizardColors.hintRing());
                graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{7f, 4f}, 0f));
                graphics.draw(geometry.closedSides(layout.group(hintGroup.get()).rectangles(), BASE_INSET));
            }
        }
        Color antsColour = WizardColors.highlight();
        OptionalInt selected = selectedHere();
        if (selected.isPresent()) {
            List<DrawnGroup> groups = drawnGroups();
            int position = paintOrder(groups).indexOf(selected.getAsInt());
            MarchingAnts.draw(graphics, geometry.closedSides(groups.get(selected.getAsInt()).rectangles(),
                    inset(position)), antsColour, ANTS_WIDTH, ants.phase());
        }
        Optional<DrawnGroup> pending = pendingRectangle();
        if (pending.isPresent()) {
            MarchingAnts.draw(graphics, geometry.closedSides(pending.get().rectangles(), BASE_INSET), antsColour,
                    ANTS_WIDTH, ants.phase());
        } else if (keyboardActive) {
            Rectangle cell = geometry.cell(cursor.row(), cursor.column());
            MarchingAnts.draw(graphics, new Rectangle(cell.x + 2, cell.y + 2, cell.width - 4, cell.height - 4),
                    antsColour, ANTS_WIDTH, ants.phase());
        }
    }

    private static Ellipse2D ring(MapGeometry geometry, KarnaughLayout.GridCell cell) {
        Rectangle bounds = ringBounds(geometry.cell(cell.row(), cell.column()));
        return new Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** Where the ring of an uncovered or hinted cell goes: a circle inside the cell. */
    static Rectangle ringBounds(Rectangle cell) {
        int inset = (int) Math.round(cell.width * RING_INSET);
        return new Rectangle(cell.x + inset, cell.y + inset, cell.width - 2 * inset, cell.height - 2 * inset);
    }

    private void paintHighlight(Graphics2D graphics, MapGeometry geometry) {
        OptionalInt row = highlightedRow();
        if (row.isEmpty()) {
            return;
        }
        KarnaughLayout.GridCell gridCell = karnaughLayout().cell(row.getAsInt());
        Rectangle cell = geometry.cell(gridCell.row(), gridCell.column());
        graphics.setColor(WizardColors.highlight());
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRect(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
    }

    private static void centered(Graphics2D graphics, String text, Rectangle area) {
        FontMetrics metrics = graphics.getFontMetrics();
        int x = area.x + (area.width - metrics.stringWidth(text)) / 2;
        int y = area.y + (area.height - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(text, x, y);
    }
}
