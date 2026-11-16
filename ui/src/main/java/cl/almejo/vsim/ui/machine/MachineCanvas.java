package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineChecks;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineKind;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContentZoom;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.inline.InlineHost;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.problems.MachineProblemText;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The centre of a machine's tab: its state diagram (spec 2026-11-14 §1 «The machine tab», §2.4), always the model as
 * it is now, with the active state and the next arrow of the copy entered through lit in the active colour. It zooms
 * and pans like the circuit canvas (Ctrl+wheel, the wheel, Mayús+wheel, the middle button, the window's zoom actions
 * through {@link ContentZoom}) and follows the simulation on the same 16 ms frames while it is shown. Diagram units
 * are pixels at 100 %. The gestures that draw and edit ({@link MachineTool}) make their edits through
 * {@link #edit} and show what they are doing through {@link #showGestures}.
 */
public final class MachineCanvas extends JComponent implements ContentZoom {

    private static final long serialVersionUID = 1L;

    /** The circuit canvas's frame: how often a shown diagram reads the latest snapshot. */
    static final int FRAME_MILLISECONDS = 16;
    /** One wheel step pans this far, as on the circuit canvas. */
    private static final double WHEEL_SCROLL_PIXELS = 40;
    /** «Ver todo» leaves this much of the canvas around the diagram. */
    private static final double FIT_SHARE = 0.9;
    /** How far from an arrow, on screen, a click or the pointer still takes it. */
    private static final double ARROW_REACH_PIXELS = 6;
    /**
     * A revealed problem blinks this many times (on and off), this fast: longer than a net found from Problemas, so it
     * is seen after the tab opens (hand test of hito 2).
     */
    private static final int REVEAL_BLINKS = 8;
    private static final int REVEAL_BLINK_MILLISECONDS = 220;
    /** Space kept around revealed elements, in diagram units, so a halo is seen whole. */
    private static final double REVEAL_MARGIN = 2 * MachineDiagramGeometry.STATE_RADIUS;
    private static final String HTML_START = "<html>";

    private final transient OpenDocument openDocument;
    private final String definitionId;
    private final transient MachineSelection selection;
    private final transient MachineStateSource stateSource;
    private final transient Supplier<ColorScheme> scheme;
    private final transient Viewport viewport = new Viewport();
    private final transient FrameTicker frames = FrameTicker.swing(FRAME_MILLISECONDS);
    private final transient Listeners<Runnable> frameListeners = new Listeners<>();
    private final transient Listeners<Runnable> changeListeners = new Listeners<>();
    private final transient Runnable placeListener = this::placeChanged;
    private final transient Runnable stopRepaintingOnSelection;
    private transient Optional<ActiveMachineState> activeState = Optional.empty();
    /** Whether the view was placed on the diagram once (it is, the first time the canvas has a size). */
    private boolean viewPlaced;
    private boolean disposed;
    /** Where the middle button pressed, while it pans the view; null otherwise. */
    private transient Point2D panFrom;
    /** What the drawing gestures show while they last (a moved state, a lit border, the rubber band). */
    private transient Gestures gestures = Gestures.NONE;
    /** Where a problem asked to be shown before the canvas had a size: shown there instead of the whole diagram. */
    private transient Optional<Rectangle2D> pendingReveal = Optional.empty();
    /**
     * The problem a reveal shows: it blinks, and the other glows fade while the selection is the one the reveal made
     * (a click elsewhere, a new selection or a fix ends it).
     */
    private transient Optional<MachineFinding> revealed = Optional.empty();
    /** A blink asked for before the canvas had a size (a tab opened from Problemas): it starts once it has one. */
    private boolean blinkWhenSized;
    private int blinksLeft;
    private final transient Timer blinkTimer = new Timer(REVEAL_BLINK_MILLISECONDS, event -> blinkOnce());

    /**
     * What the diagram's gestures show on top of the model while they last: the machine as it would be after a drag
     * (states being moved), and the marks of {@link MachineDiagramPainter.Overlay}.
     */
    interface Gestures {

        Gestures NONE = new Gestures() {
            @Override
            public Optional<StateMachine> preview() {
                return Optional.empty();
            }

            @Override
            public MachineDiagramPainter.Overlay overlay() {
                return MachineDiagramPainter.Overlay.NONE;
            }
        };

        /** The machine to draw instead of the model while a drag lasts; empty to draw the model. */
        Optional<StateMachine> preview();

        /** The marks to draw over the diagram now. */
        MachineDiagramPainter.Overlay overlay();
    }

    /**
     * The diagram of the machine {@code definitionId} for the tab of {@code openDocument} (a body place of that
     * definition, whose context path names the copy entered through), sharing {@code selection} with the tab, in
     * the colours {@code scheme} gives at each paint. The active state comes from the running simulation.
     */
    public MachineCanvas(OpenDocument openDocument, String definitionId, MachineSelection selection,
                         Supplier<ColorScheme> scheme) {
        this(openDocument, definitionId, selection, MachineStateSource.standard(), scheme);
    }

    /** As above, reading the active state through {@code stateSource} (tests). */
    MachineCanvas(OpenDocument openDocument, String definitionId, MachineSelection selection,
                  MachineStateSource stateSource, Supplier<ColorScheme> scheme) {
        this.openDocument = Objects.requireNonNull(openDocument, "openDocument");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.stateSource = Objects.requireNonNull(stateSource, "stateSource");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        setOpaque(true);
        setFocusable(true);
        setLayout(null); // the inline editor floats over the diagram where it is asked to
        viewport.addChangeListener(this::repaint);
        openDocument.addChangeListener(placeListener);
        stopRepaintingOnSelection = selection.addListener(this::selectionChanged);
        MouseHandler mouse = new MouseHandler();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                followFrames(isShowing());
            }
        });
    }

    /** The machine definition this diagram shows. */
    public String definitionId() {
        return definitionId;
    }

    /** The machine as it is now in the circuit; empty once its definition left (its tab closes then). */
    public Optional<StateMachine> machine() {
        return circuit().document().definition(definitionId).flatMap(ComponentDefinition::machine);
    }

    /** What is selected in this diagram (shared with the tab's right panel and gestures). */
    public MachineSelection selection() {
        return selection;
    }

    /**
     * Makes {@code change} of the machine as one undo step of the circuit, executed through this tab's place so the
     * step belongs to the machine's tab (its undo is undone here, its name says where); nothing when it changes
     * nothing. Returns whether it made the edit.
     */
    public boolean edit(UnaryOperator<StateMachine> change) {
        Optional<StateMachine> current = machine();
        if (current.isEmpty()) {
            return false;
        }
        StateMachine changed = change.apply(current.get());
        if (changed.equals(current.get())) {
            return false;
        }
        openDocument.execute(new EditMachineCommand(definitionId, changed));
        return true;
    }

    /** Shows what {@code shown} gives on top of the diagram from now on (the drawing gestures). */
    void showGestures(Gestures shown) {
        gestures = Objects.requireNonNull(shown, "shown");
        repaint();
    }

    /** {@code pixels} on screen as diagram units at the zoom shown now. */
    double toDiagramLength(double pixels) {
        return pixels / viewport.zoom();
    }

    /** This canvas as the surface an {@link InlineTextEditor} opens over (names and conditions). */
    InlineHost inlineHost() {
        return new InlineHost() {
            @Override
            public JComponent component() {
                return MachineCanvas.this;
            }

            @Override
            public Runnable addFloatingComponent(JComponent editor) {
                add(editor, 0);
                repaint();
                return () -> {
                    remove(editor);
                    repaint();
                };
            }

            @Override
            public Runnable addViewportListener(Runnable listener) {
                return viewport.addChangeListener(listener);
            }

            @Override
            public Runnable addChangeListener(Runnable listener) {
                openDocument.addChangeListener(listener);
                return () -> openDocument.removeChangeListener(listener);
            }

            @Override
            public AffineTransform documentTransform() {
                return viewport.documentTransform();
            }

            @Override
            public double zoom() {
                return viewport.zoom();
            }
        };
    }

    /**
     * The ids of {@code machine}'s states in the order the diagram paints them (selected and active ones on top), so
     * a point where circles overlap goes to the state the user sees.
     */
    List<String> drawingOrder(StateMachine machine) {
        return MachineDiagramPainter.drawingOrderIds(machine, activeState, selection);
    }

    /** The active state of the copy entered through, as of the last frame; empty with «Sin valores». */
    public Optional<ActiveMachineState> activeState() {
        return activeState;
    }

    /** Which states and arrows glow for the machine's problems now (its model's, never a gesture's preview). */
    MachineGlows glows() {
        return machine().map(machine -> MachineGlows.of(MachineChecks.check(machine))).orElse(MachineGlows.NONE);
    }

    /**
     * The problems this tab shows (its Propiedades, F8): the machine's own, then the ones of the running copy entered
     * through (an input or the clock in Z): the same rows Problemas lists for them.
     */
    public List<MachineProblem> problems() {
        Optional<StateMachine> machine = machine();
        if (machine.isEmpty()) {
            return List.of();
        }
        List<MachineProblem> problems = new ArrayList<>();
        MachineChecks.check(machine.get()).forEach(finding ->
                problems.add(MachineProblem.of(definitionId, Optional.empty(), finding)));
        openDocument.contextPath().ifPresent(path -> runtimeFindings(path).forEach(finding ->
                problems.add(MachineProblem.of(definitionId, Optional.of(path), finding))));
        return List.copyOf(problems);
    }

    /** What the running copy at {@code path} reports, from the circuit's latest snapshot of its current netlist. */
    private List<MachineFinding> runtimeFindings(String path) {
        OpenDocument circuit = circuit();
        SimulationSnapshot latest = circuit.latestSnapshot();
        if (latest.token() != circuit.netlistToken()) {
            return List.of();
        }
        return latest.componentStates().get(path) instanceof VisibleState.MachineVisibleState state
                ? MachineChecks.runtime(state) : List.of();
    }

    /**
     * Selects what {@code finding} is about (the arrows or states that glow for it), brings them into view and, with
     * {@code blink}, makes them blink a few times; nothing is selected for a problem about neither (no initial state,
     * an unused input), whose words the machine's Propiedades list.
     */
    void reveal(MachineFinding finding, boolean blink) {
        revealed = Optional.empty();
        selection.set(new LinkedHashSet<>(MachineGlows.statesOf(finding)),
                new LinkedHashSet<>(MachineGlows.transitionsOf(finding)));
        boolean sized = getWidth() > 0 && getHeight() > 0;
        machine().flatMap(machine -> boundsOf(machine, finding)).ifPresent(bounds -> {
            if (sized) {
                showCentred(bounds, Math.min(viewport.zoom(), fitZoom(bounds)));
            } else {
                pendingReveal = Optional.of(bounds);
            }
        });
        blinkTimer.stop();
        revealed = Optional.of(finding);
        blinksLeft = blink ? REVEAL_BLINKS : 0;
        blinkWhenSized = blink && !sized;
        if (blink && sized) {
            blinkTimer.start();
        }
        requestFocusInWindow();
        repaint();
    }

    /** The problem shown now (its glow stands out, the others fade); empty once the selection moved on. */
    Optional<MachineFinding> revealed() {
        return revealed.filter(finding -> machine()
                .filter(machine -> MachineChecks.check(machine).contains(finding)).isPresent());
    }

    /** A selection other than the one a reveal made ends the reveal. */
    private void selectionChanged() {
        revealed.ifPresent(finding -> {
            if (!selection.stateIds().equals(new LinkedHashSet<>(MachineGlows.statesOf(finding)))
                    || !selection.transitionIds().equals(new LinkedHashSet<>(MachineGlows.transitionsOf(finding)))) {
                revealed = Optional.empty();
                blinkTimer.stop();
            }
        });
        repaint();
    }

    /** Whether a revealed problem is blinking now. */
    boolean isBlinking() {
        return blinkTimer.isRunning();
    }

    private void blinkOnce() {
        blinksLeft--;
        if (blinksLeft <= 0) {
            blinkTimer.stop();
        }
        repaint();
    }

    /** The glowing elements of {@code finding} with room around them; empty when it is about none. */
    private static Optional<Rectangle2D> boundsOf(StateMachine machine, MachineFinding finding) {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        Rectangle2D result = null;
        for (String stateId : MachineGlows.statesOf(finding)) {
            if (machine.state(stateId).isPresent()) {
                result = union(result, geometry.circle(stateId).getBounds2D());
            }
        }
        for (String transitionId : MachineGlows.transitionsOf(finding)) {
            if (machine.transition(transitionId).isPresent()) {
                result = union(result, geometry.arrow(transitionId).path().getBounds2D());
            }
        }
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(new Rectangle2D.Double(result.getX() - REVEAL_MARGIN, result.getY() - REVEAL_MARGIN,
                result.getWidth() + 2 * REVEAL_MARGIN, result.getHeight() + 2 * REVEAL_MARGIN));
    }

    private static Rectangle2D union(Rectangle2D sofar, Rectangle2D more) {
        return sofar == null ? more : sofar.createUnion(more);
    }

    /** The arrow whose condition or line is under {@code point}: labels first, they sit over the lines. */
    Optional<String> transitionAt(StateMachine machine, MachineDiagramGeometry geometry, Point2D point) {
        FontMetrics metrics = getFontMetrics(MachineDiagramPainter.labelFont());
        String labelled = null;
        for (MachineTransition transition : machine.transitions()) {
            if (MachineDiagramPainter.labelBounds(transition, geometry.arrow(transition.id()), metrics)
                    .contains(point)) {
                labelled = transition.id();
            }
        }
        return Optional.ofNullable(labelled)
                .or(() -> geometry.transitionAt(point, toDiagramLength(ARROW_REACH_PIXELS)));
    }

    /** What is wrong with the state or arrow under the pointer, in words; nothing over a plain one. */
    @Override
    public String getToolTipText(MouseEvent event) {
        Optional<String> chipText = machine()
                .flatMap(machine -> MachineKindChips.tooltipAt(event.getPoint(), MachineKind.of(machine),
                        MachineKindChips.defaultFont()));
        return chipText.or(() -> problemTextAt(event.getPoint())).orElse(null);
    }

    private Optional<String> problemTextAt(Point2D screenPoint) {
        Optional<StateMachine> machine = machine();
        if (machine.isEmpty()) {
            return Optional.empty();
        }
        DiagramPoint diagramPoint = toDiagram(screenPoint);
        Point2D at = new Point2D.Double(diagramPoint.x(), diagramPoint.y());
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine.get());
        MachineGlows glows = glows();
        List<MachineFinding> findings = geometry.stateAt(at, drawingOrder(machine.get())).map(glows::findingsOfState)
                .orElseGet(() -> transitionAt(machine.get(), geometry, at).map(glows::findingsOfTransition)
                        .orElse(List.of()));
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        List<String> lines = findings.stream()
                .map(finding -> escaped(MachineProblemText.sentence(finding, machine.get()))).toList();
        return Optional.of(HTML_START + String.join("<br>", lines) + "</html>");
    }

    private static String escaped(String plain) {
        return plain.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The view's zoom and pan: diagram units to screen pixels. */
    public Viewport viewport() {
        return viewport;
    }

    /** Where {@code point} of the diagram is on this canvas now. */
    public Point2D toScreen(DiagramPoint point) {
        return viewport.documentTransform().transform(new Point2D.Double(point.x(), point.y()), null);
    }

    /** The diagram point under {@code screenPoint} of this canvas. */
    public DiagramPoint toDiagram(Point2D screenPoint) {
        double zoom = viewport.zoom();
        Point2D origin = viewport.documentTransform().transform(new Point2D.Double(), null);
        return new DiagramPoint((screenPoint.getX() - origin.getX()) / zoom,
                (screenPoint.getY() - origin.getY()) / zoom);
    }

    /**
     * Reads the copy's state from the latest snapshot and repaints when it changed (one frame does this). The copy is
     * the tab's context path; with «Sin valores» there is none.
     */
    public void showLatestState() {
        SimulationSnapshot snapshot = circuit().latestSnapshot();
        Optional<ActiveMachineState> latest = openDocument.contextPath()
                .flatMap(path -> stateSource.activeState(snapshot, path));
        if (!latest.equals(activeState)) {
            activeState = latest;
            repaint();
        }
    }

    /** Runs {@code listener} after each frame of a shown diagram (the tab feeds its hidden panes); returns removal. */
    public Runnable addFrameListener(Runnable listener) {
        return frameListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} after every change of the machine or of the tab's context; returns its removal. */
    public Runnable addChangeListener(Runnable listener) {
        return changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Stops following the document and the simulation: the tab closed or left machine mode. */
    public void dispose() {
        disposed = true;
        blinkWhenSized = false;
        frames.stop();
        blinkTimer.stop();
        ToolTipManager.sharedInstance().unregisterComponent(this);
        openDocument.removeChangeListener(placeListener);
        stopRepaintingOnSelection.run();
    }

    @Override
    public double zoom() {
        return viewport.zoom();
    }

    @Override
    public void zoomAroundCenter(double zoom) {
        viewport.zoomAt(getWidth() / 2.0, getHeight() / 2.0, zoom);
    }

    @Override
    public void zoomIn() {
        viewport.zoomIn(getWidth() / 2.0, getHeight() / 2.0);
    }

    @Override
    public void zoomOut() {
        viewport.zoomOut(getWidth() / 2.0, getHeight() / 2.0);
    }

    @Override
    public void resetZoom() {
        zoomAroundCenter(1.0);
    }

    /** Zooms until every state and arrow fills nine tenths of the canvas, centred on them. */
    @Override
    public void zoomToFit() {
        diagramBounds().ifPresent(bounds -> showCentred(bounds, fitZoom(bounds)));
    }

    @Override
    public void center() {
        diagramBounds().ifPresent(bounds -> showCentred(bounds, viewport.zoom()));
    }

    @Override
    public Runnable addViewListener(Runnable listener) {
        return viewport.addChangeListener(Objects.requireNonNull(listener, "listener"));
    }

    /** The first time the canvas has a size, it shows the whole diagram (never above 100 %), like an entered tab. */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (!viewPlaced && width > 0 && height > 0) {
            viewPlaced = true;
            Optional<Rectangle2D> waiting = pendingReveal;
            pendingReveal = Optional.empty();
            waiting.or(this::diagramBounds).ifPresent(bounds -> showCentred(bounds, Math.min(1.0, fitZoom(bounds))));
        }
        if (blinkWhenSized && !disposed && width > 0 && height > 0) {
            blinkWhenSized = false;
            blinkTimer.start();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Window window = SwingUtilities.getWindowAncestor(this);
        // added hidden to a window already on screen (a tab's card): the showing event starts it when it is shown
        followFrames(isShowing() || window == null || !window.isShowing());
    }

    @Override
    public void removeNotify() {
        frames.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            ColorScheme colours = scheme.get();
            canvas.setColor(MachineColors.background(colours));
            canvas.fillRect(0, 0, getWidth(), getHeight());
            Optional<StateMachine> machine = gestures.preview().or(this::machine);
            if (machine.isEmpty()) {
                return;
            }
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.transform(viewport.documentTransform());
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine.get());
            MachineDiagramPainter.paint(canvas, machine.get(), geometry,
                    new MachineDiagramPainter.Marks(activeState, selection, glows(), revealed()), colours);
            MachineDiagramPainter.paintOverlay(canvas, geometry, gestures.overlay(), colours);
            if (blinkTimer.isRunning() && blinksLeft % 2 == 1) {
                revealed().ifPresent(finding -> MachineDiagramPainter.paintFlash(canvas, machine.get(), geometry,
                        finding, colours));
            }
            // the type chips stay on screen, not on the diagram: no zoom, no pan
            MachineKindChips.paint((Graphics2D) graphics, MachineKind.of(machine.get()), colours);
        } finally {
            canvas.dispose();
        }
    }

    /** The tab's place this diagram belongs to (its machine's definition lives in the place's circuit). */
    OpenDocument place() {
        return openDocument;
    }

    /** The circuit the machine lives in (the place's document). */
    private OpenDocument circuit() {
        return openDocument.parent().orElse(openDocument);
    }

    private Optional<Rectangle2D> diagramBounds() {
        return machine().flatMap(machine -> MachineDiagramGeometry.of(machine).bounds());
    }

    private double fitZoom(Rectangle2D bounds) {
        if (getWidth() <= 0 || getHeight() <= 0 || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return viewport.zoom();
        }
        return Viewport.clampZoom(Math.min(getWidth() * FIT_SHARE / bounds.getWidth(),
                getHeight() * FIT_SHARE / bounds.getHeight()));
    }

    /** Shows the middle of {@code bounds} (diagram units) in the middle of the canvas at {@code zoom}. */
    private void showCentred(Rectangle2D bounds, double zoom) {
        viewport.showAt(zoom, bounds.getCenterX() / GridPoint.PIXELS_PER_UNIT,
                bounds.getCenterY() / GridPoint.PIXELS_PER_UNIT, getWidth(), getHeight());
    }

    /** The machine or the context changed: a selected thing that left is forgotten, and the diagram repaints. */
    private void placeChanged() {
        machine().ifPresent(selection::retainExisting);
        showLatestState();
        repaint();
        changeListeners.fire(Runnable::run);
    }

    private void followFrames(boolean shown) {
        if (shown && !disposed) {
            frames.start(this::frame);
        } else {
            frames.stop();
        }
    }

    private void frame() {
        showLatestState();
        frameListeners.fire(Runnable::run);
    }

    /** Zoom and pan as on the circuit canvas: Ctrl+wheel zooms at the pointer, the wheel pans, the middle drags. */
    private final class MouseHandler extends MouseAdapter {

        @Override
        public void mouseWheelMoved(MouseWheelEvent event) {
            double rotation = event.getPreciseWheelRotation();
            if (event.isControlDown()) {
                viewport.zoomAt(event.getX(), event.getY(), viewport.zoom() * Math.pow(Viewport.ZOOM_STEP, -rotation));
            } else if (event.isShiftDown()) {
                viewport.panBy(-rotation * WHEEL_SCROLL_PIXELS, 0);
            } else {
                viewport.panBy(0, -rotation * WHEEL_SCROLL_PIXELS);
            }
        }

        @Override
        public void mousePressed(MouseEvent event) {
            requestFocusInWindow();
            if (SwingUtilities.isMiddleMouseButton(event)) {
                panFrom = event.getPoint();
            }
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (panFrom != null) {
                viewport.panBy(event.getX() - panFrom.getX(), event.getY() - panFrom.getY());
                panFrom = event.getPoint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (SwingUtilities.isMiddleMouseButton(event)) {
                panFrom = null;
            }
        }
    }
}
