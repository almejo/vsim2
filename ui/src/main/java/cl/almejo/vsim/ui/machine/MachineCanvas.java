package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContentZoom;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The centre of a machine's tab: its state diagram (spec 2026-11-14 §1 «The machine tab», §2.4), always the model as
 * it is now, with the active state and the next arrow of the copy entered through lit in the active colour. It zooms
 * and pans like the circuit canvas (Ctrl+wheel, the wheel, Mayús+wheel, the middle button, the window's zoom actions
 * through {@link ContentZoom}) and follows the simulation on the same 16 ms frames while it is shown. Diagram units
 * are pixels at 100 %. The gestures that draw and edit come with the diagram's tools.
 */
public final class MachineCanvas extends JComponent implements ContentZoom {

    private static final long serialVersionUID = 1L;

    /** The circuit canvas's frame: how often a shown diagram reads the latest snapshot. */
    static final int FRAME_MILLISECONDS = 16;
    /** One wheel step pans this far, as on the circuit canvas. */
    private static final double WHEEL_SCROLL_PIXELS = 40;
    /** «Ver todo» leaves this much of the canvas around the diagram. */
    private static final double FIT_SHARE = 0.9;

    private final transient OpenDocument openDocument;
    private final String definitionId;
    private final transient MachineSelection selection;
    private final transient MachineStateSource stateSource;
    private final transient Supplier<ColorScheme> scheme;
    private final transient Viewport viewport = new Viewport();
    private final transient FrameTicker frames = FrameTicker.swing(FRAME_MILLISECONDS);
    private final transient Listeners<Runnable> frameListeners = new Listeners<>();
    private final transient Runnable placeListener = this::placeChanged;
    private final transient Runnable stopRepaintingOnSelection;
    private transient Optional<ActiveMachineState> activeState = Optional.empty();
    /** Whether the view was placed on the diagram once (it is, the first time the canvas has a size). */
    private boolean viewPlaced;
    private boolean disposed;
    /** Where the middle button pressed, while it pans the view; null otherwise. */
    private transient Point2D panFrom;

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
        viewport.addChangeListener(this::repaint);
        openDocument.addChangeListener(placeListener);
        stopRepaintingOnSelection = selection.addListener(this::repaint);
        MouseHandler mouse = new MouseHandler();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
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

    /** The active state of the copy entered through, as of the last frame; empty with «Sin valores». */
    public Optional<ActiveMachineState> activeState() {
        return activeState;
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

    /** Stops following the document and the simulation: the tab closed or left machine mode. */
    public void dispose() {
        disposed = true;
        frames.stop();
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
            diagramBounds().ifPresent(bounds -> showCentred(bounds, Math.min(1.0, fitZoom(bounds))));
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
            Optional<StateMachine> machine = machine();
            if (machine.isEmpty()) {
                return;
            }
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.transform(viewport.documentTransform());
            MachineDiagramPainter.paint(canvas, machine.get(), MachineDiagramGeometry.of(machine.get()),
                    new MachineDiagramPainter.Marks(activeState, selection), colours);
        } finally {
            canvas.dispose();
        }
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
