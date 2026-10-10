package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.problems.Blink;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The waveform panel of one view (spec §3.2): toolbar, channel names with the value at the cursor, the traces and a
 * time ruler, all on one {@link TimeAxis}. A 33 ms timer runs only while the panel is on screen and has channels, and
 * repaints only when the document publishes a new {@link WaveformSnapshot}; nothing restarts after {@link #dispose()}.
 */
public final class WaveformPanel extends JPanel {

    static final int TIMER_MILLISECONDS = 33;
    static final double WHEEL_SCROLL_PIXELS = 60;
    static final double WHEEL_ZOOM_FACTOR = 1.25;

    private final OpenDocument openDocument;
    private final ProbeActions actions;
    private final Supplier<ColorScheme> scheme;
    private final TextPrompt prompt;
    private final Consumer<String> showInCircuit;
    private final TimeAxis axis = new TimeAxis();
    private final JToolBar toolBar = new JToolBar();
    private final JToggleButton followButton;
    private final ChannelNameColumn names;
    private final WaveformPlot plot;
    private final TimeRuler ruler;
    private final Timer timer;
    private final Blink rowBlink;
    private BooleanSupplier showing = this::isShowing;
    private WaveformSnapshot displayed = WaveformSnapshot.EMPTY;
    private String selectedProbe;
    private String blinkingProbe;
    private int repaintCount;
    private boolean disposed;

    public WaveformPanel(OpenDocument openDocument, ProbeActions actions, Supplier<ColorScheme> scheme,
                         TextPrompt prompt, Consumer<String> showInCircuit, Runnable hide) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.actions = actions;
        this.scheme = scheme;
        this.prompt = prompt;
        this.showInCircuit = showInCircuit;
        this.names = new ChannelNameColumn(this);
        this.plot = new WaveformPlot(this);
        this.ruler = new TimeRuler(this);
        this.followButton = new JToggleButton(action("waveform.follow", this::followToggled));
        followButton.setSelected(true);
        toolBar.setFloatable(false);
        toolBar.add(followButton);
        toolBar.add(action("waveform.zoomIn", this::zoomIn));
        toolBar.add(action("waveform.zoomOut", this::zoomOut));
        toolBar.add(action("waveform.fitAll", this::fitAll));
        toolBar.add(action("waveform.showDelays", this::showDelays));
        toolBar.add(action("waveform.clear", openDocument::clearWaveforms));
        toolBar.add(action("waveform.hide", hide));
        JScrollPane scroll = new JScrollPane(plot, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setRowHeaderView(names);
        scroll.setColumnHeaderView(ruler);
        add(toolBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        timer = new Timer(TIMER_MILLISECONDS, event -> timerTick());
        rowBlink = new Blink(names::repaint);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateTimer();
            }
        });
        updateTimer();
    }

    /** The document's probes changed: rows, plot height and timer follow. */
    public void probesChanged() {
        if (disposed) {
            return;
        }
        if (selectedProbe != null && !channelIds().contains(selectedProbe)) {
            selectedProbe = null;
        }
        names.revalidate();
        plot.revalidate();
        updateTimer();
        repaintAll();
    }

    /** Makes the row of {@code probeId} blink for about 1.5 s (an add found its net already shown). */
    public void blinkRow(String probeId) {
        if (disposed) {
            return;
        }
        blinkingProbe = probeId;
        rowBlink.start();
    }

    /** The names the rows show, in order. */
    public List<String> channelNames() {
        return rows().stream().map(ChannelRows.ChannelRow::name).toList();
    }

    public void dispose() {
        disposed = true;
        timer.stop();
        rowBlink.dispose();
        blinkingProbe = null;
    }

    // --- shared with the parts and the tests -------------------------------------------------------------------

    TimeAxis axis() {
        return axis;
    }

    WaveformSnapshot displayed() {
        return displayed;
    }

    /** Shows {@code snapshot}: the axis follows its time and every part repaints. */
    void display(WaveformSnapshot snapshot) {
        displayed = snapshot;
        axis.follow(snapshot.time());
        repaintAll();
    }

    /** One timer step: re-checks that the timer is still needed and shows a new snapshot when there is one. */
    void timerTick() {
        updateTimer();
        if (timer.isRunning()) {
            WaveformSnapshot latest = openDocument.latestWaveforms();
            if (latest != displayed) {
                display(latest);
            }
        }
    }

    /** For tests: the on/off steps of a row blink. */
    Blink rowBlink() {
        return rowBlink;
    }

    /** Replaces the on-screen check, so headless tests can run the timer. */
    void overrideShowing(BooleanSupplier check) {
        showing = check;
        updateTimer();
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    int repaintCount() {
        return repaintCount;
    }

    List<ChannelRows.ChannelRow> rows() {
        return ChannelRows.of(openDocument.document().probes(), openDocument.probeNets());
    }

    List<String> channelIds() {
        return rows().stream().map(ChannelRows.ChannelRow::probeId).toList();
    }

    ColorScheme scheme() {
        return scheme.get();
    }

    Optional<String> selectedProbe() {
        return Optional.ofNullable(selectedProbe);
    }

    void selectProbe(String probeId) {
        selectedProbe = probeId;
        names.repaint();
    }

    boolean isBlinkLit(String probeId) {
        return rowBlink.isLit() && probeId.equals(blinkingProbe);
    }

    /** A click in the plot: the cursor goes to the nearest edge of any channel within 4 px, else under the mouse. */
    void placeCursor(double x) {
        long floor = (long) Math.floor(axis.timeAt(x));
        List<Long> candidates = new ArrayList<>();
        for (Trace trace : displayed.traces().values()) {
            trace.previousEdge(floor + 1).ifPresent(edge -> candidates.add(edge));
            trace.nextEdge(floor).ifPresent(edge -> candidates.add(edge));
        }
        axis.setCursor(axis.snap(x, candidates));
        axisChanged();
    }

    /** «t = 1 237 · Δt = 3»: the time under {@code x} and, with a cursor, the distance from it. */
    String hoverText(double x) {
        long time = Math.round(axis.timeAt(x));
        String text = "t = " + TimeAxis.format(time);
        OptionalLong cursor = axis.cursor();
        return cursor.isPresent() ? text + " · Δt = " + TimeAxis.format(time - cursor.getAsLong()) : text;
    }

    /** ← / →: the cursor jumps to the previous / next edge of the selected row, scrolling it into view. */
    void moveCursorToEdge(int direction) {
        Optional<Trace> trace = selectedProbe().flatMap(displayed::trace);
        if (trace.isEmpty()) {
            return;
        }
        long from = axis.cursor().orElse(displayed.time());
        OptionalLong edge = direction < 0 ? trace.get().previousEdge(from) : trace.get().nextEdge(from);
        if (edge.isEmpty()) {
            return;
        }
        axis.setCursor(edge.getAsLong());
        double x = axis.xOf(edge.getAsLong());
        if (x < 0 || x > axis.width()) {
            axis.centerOn(edge.getAsLong());
        }
        axisChanged();
    }

    /**
     * Wheel = scroll time; Ctrl+wheel = zoom around the pointer, {@link #WHEEL_ZOOM_FACTOR} per notch, in proportion
     * to {@code rotation} (a trackpad sends many fractions of a notch); wheel up zooms in. A rotation of 0 does nothing.
     */
    void wheel(double x, double rotation, boolean zoom) {
        if (rotation == 0) {
            return;
        }
        if (zoom) {
            axis.zoomAround(x, Math.pow(WHEEL_ZOOM_FACTOR, -rotation));
        } else {
            axis.scrollBy(rotation * WHEEL_SCROLL_PIXELS);
        }
        axisChanged();
    }

    /** The plot got {@code width} pixels wide: while following, the current time stays at the right edge. */
    void plotResized(int width) {
        axis.setWidth(width);
        axis.follow(displayed.time());
        repaintAll();
    }

    /** A drag in the ruler. */
    void scrollBy(double pixels) {
        axis.scrollBy(pixels);
        axisChanged();
    }

    /** «historial desde t = …» once old events were dropped, else the current time. */
    String rulerToolTip() {
        OptionalLong complete = displayed.traces().values().stream()
                .filter(Trace::truncated)
                .map(Trace::firstTime)
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .max();
        return complete.isPresent()
                ? Messages.format("waveform.historyFrom", TimeAxis.format(complete.getAsLong()))
                : "t = " + TimeAxis.format(displayed.time());
    }

    void showInCircuit(String probeId) {
        showInCircuit.accept(probeId);
    }

    /** The right-click menu of a row (spec §3.2). */
    JPopupMenu rowMenu(String probeId) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(action("waveform.rename", () -> rename(probeId))));
        menu.addSeparator();
        menu.add(new JMenuItem(action("waveform.moveUp", () -> actions.moveUp(probeId))));
        menu.add(new JMenuItem(action("waveform.moveDown", () -> actions.moveDown(probeId))));
        menu.addSeparator();
        menu.add(new JMenuItem(action("waveform.remove", () -> actions.remove(probeId))));
        menu.add(new JMenuItem(action("waveform.removeAll", actions::removeAll)));
        menu.addSeparator();
        menu.add(new JMenuItem(action("waveform.showInCircuit", () -> showInCircuit(probeId))));
        return menu;
    }

    JToggleButton followButton() {
        return followButton;
    }

    AbstractButton button(String text) {
        for (java.awt.Component child : toolBar.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new IllegalArgumentException("no toolbar button " + text);
    }

    List<String> toolBarTexts() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : toolBar.getComponents()) {
            if (child instanceof AbstractButton button) {
                texts.add(button.getText());
            }
        }
        return texts;
    }

    ChannelNameColumn names() {
        return names;
    }

    WaveformPlot plot() {
        return plot;
    }

    TimeRuler ruler() {
        return ruler;
    }

    // --- private ---------------------------------------------------------------------------------------------

    private void rename(String probeId) {
        Optional<Probe> probe = openDocument.document().probes().stream()
                .filter(candidate -> candidate.id().equals(probeId))
                .findFirst();
        if (probe.isPresent()) {
            prompt.ask(this, Messages.text("waveform.renameTitle"), Messages.text("waveform.renamePrompt"),
                    probe.get().name()).ifPresent(name -> actions.rename(probeId, name));
        }
    }

    private void followToggled() {
        axis.setFollowing(followButton.isSelected());
        axis.follow(displayed.time());
        repaintAll();
    }

    private void zoomIn() {
        axis.zoomIn();
        axisChanged();
    }

    private void zoomOut() {
        axis.zoomOut();
        axisChanged();
    }

    private void fitAll() {
        OptionalLong first = displayed.traces().values().stream()
                .map(Trace::firstTime)
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .min();
        if (first.isPresent()) {
            axis.fit(first.getAsLong(), displayed.time());
            axisChanged();
        }
    }

    private void showDelays() {
        axis.showDelays(axis.cursor().orElse(displayed.time()));
        axisChanged();
    }

    private void axisChanged() {
        followButton.setSelected(axis.isFollowing());
        repaintAll();
    }

    private void updateTimer() {
        boolean needed = !disposed && showing.getAsBoolean() && !openDocument.document().probes().isEmpty();
        if (needed && !timer.isRunning()) {
            timer.start();
        } else if (!needed && timer.isRunning()) {
            timer.stop();
        }
    }

    private void repaintAll() {
        repaintCount++;
        names.repaint();
        plot.repaint();
        ruler.repaint();
    }

    private static Action action(String key, Runnable behavior) {
        return new AbstractAction(Messages.text(key)) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
    }
}
