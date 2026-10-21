package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.edges.EdgeController;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.ComponentPlacement;
import cl.almejo.vsim.ui.snap.ContactDots;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.snap.SnappedPlacement;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.wiring.MovePreview;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TooManyListenersException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** The drawing surface of one window. Mouse input goes to the current {@link Tool}. */
public final class CircuitCanvas extends JComponent implements ToolContext {

    private static final int FRAME_MILLISECONDS = 16;
    private static final int ANTS_MILLISECONDS = 90;
    private static final int AUTOSCROLL_MILLISECONDS = 30;
    private static final double WHEEL_SCROLL_PIXELS = 40;
    /**
     * A touchpad's sideways swipe reaches Swing on Linux as presses of these extra mouse buttons (X11 buttons 6 and 7),
     * not as a wheel turn; they scroll like Shift+wheel up and down.
     */
    private static final int SCROLL_LEFT_BUTTON = 4;
    private static final int SCROLL_RIGHT_BUTTON = 5;
    private static final double FIT_FRACTION = 0.9;

    private final OpenDocument openDocument;
    private final CircuitRenderer renderer;
    private final CanvasHost host;
    private final HitTester hitTester;
    private final Selection selection;
    private final Runnable selectionListener = () -> {
        updateAnimation();
        repaint();
    };
    private final List<Runnable> pressObservers = new CopyOnWriteArrayList<>();
    private final Viewport viewport = new Viewport();
    private final Tool defaultTool = new SelectTool();
    private final Timer frameTimer;
    private final MarchingAnts marchingAnts = new MarchingAnts();
    private final Timer antsTimer;
    private final ProblemPulse problemPulse = new ProblemPulse();
    private final Timer problemPulseTimer;
    private final Timer autoscrollTimer;
    private final List<Consumer<Tool>> toolListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SimulationSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> viewListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CircuitFragment>> incomingFragmentListeners = new CopyOnWriteArrayList<>();
    private final List<CanvasOverlay> overlays = new CopyOnWriteArrayList<>();
    private final List<Function<Point2D, Optional<String>>> toolTipOverrides = new CopyOnWriteArrayList<>();
    private final List<Predicate<CanvasMouseEvent>> pressInterceptors = new CopyOnWriteArrayList<>();
    private final EdgeController edgeController = new EdgeController(this::getSize);
    private final DocumentListener documentListener;
    private final SelectionMoves selectionMoves;
    private Consumer<List<Path>> fileDropHandler = files -> {
    };
    private DragOrigin dragOut;
    private String dragOutJson;
    private GridPoint dragOutPreview;
    /** The contacts of the move a drag out brought back previews, and where the pointer is (grid units). */
    private final MovePreview dragOutMoves = new MovePreview();
    private Point2D dragOutPointer;
    private Function<Point2D, Optional<String>> toolTipProvider = gridPoint -> Optional.empty();
    private boolean toolTipProviderSet;
    private boolean pressIntercepted;
    private Tool tool = defaultTool;
    private GridStyle gridStyle = GridStyle.DOTS;
    private int gridLineSpacing = AppPreferences.DEFAULT_GRID_LINE_SPACING;
    private Snapper snapper = Snapper.none();
    private SimulationSnapshot displayedSnapshot = SimulationSnapshot.EMPTY;
    private boolean animationSuspended;
    private BooleanSupplier onScreen = this::isOnScreen;
    private final WindowStateListener windowStateListener = event -> updateAnimation();
    private final WindowAdapter windowDeactivationListener = new WindowAdapter() {
        @Override
        public void windowDeactivated(WindowEvent event) {
            stopAutoscroll(); // the release may never reach this canvas
        }
    };
    private Window watchedWindow;
    private MouseEvent autoscrollEvent;
    private boolean automaticAutoscrollClock = true;
    private Predicate<Point> overAnotherWindow = this::isOverAnotherWindow;
    private boolean spaceDown;
    private Point panAnchor;
    private GridPoint mousePoint;
    /** The pointer is over an edge handle and the tool was already told it left. */
    private boolean toolSeesPointerGone;
    private Cursor toolCursor = Cursor.getDefaultCursor();
    private BooleanSupplier animateViewMoves = () -> false;
    /** True while the view moves in a way that must not stop a travel (a travel frame, a layout compensation). */
    private boolean movingWithTheTravel;
    /**
     * The zoom the user chose (wheel, buttons, a restored view): where {@link #travelTo} rests, even after a travel
     * that zoomed out for its target to fit. Moves made by travels never change it.
     */
    private double userZoom = 1;
    private boolean disposed;
    /** {@link #tunnelColors()} as last worked out, and the document and revision it was worked out for. */
    private Map<String, Integer> tunnelColorsCache = Map.of();
    private CircuitDocument tunnelColorsDocument;
    private long tunnelColorsRevision = -1;
    private final ViewAnimator viewAnimator = new ViewAnimator(FrameTicker.swing(FRAME_MILLISECONDS),
            () -> System.nanoTime() / 1_000_000, this::showTravelFrame, () -> animateViewMoves.getAsBoolean());

    /** A canvas with a selection of its own (a window that is not split, a canvas outside a window). */
    public CircuitCanvas(OpenDocument openDocument, CircuitRenderer renderer, CanvasHost host) {
        this(openDocument, renderer, host, new Selection());
    }

    /**
     * A canvas showing {@code selection}, which other canvases may show too (the panes of one window share it). The
     * canvas prunes it when elements are deleted and stops listening to it when disposed.
     */
    public CircuitCanvas(OpenDocument openDocument, CircuitRenderer renderer, CanvasHost host, Selection selection) {
        this.openDocument = openDocument;
        this.selection = Objects.requireNonNull(selection, "selection");
        this.renderer = renderer;
        this.host = host;
        this.selectionMoves = host.selectionMoves();
        this.hitTester = new HitTester(openDocument.registry());
        this.documentListener = this::documentChanged;
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(900, 650));
        openDocument.document().addListener(documentListener);
        selection.addListener(selectionListener);
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        KeyHandler keyHandler = new KeyHandler();
        addKeyListener(keyHandler);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                keyHandler.forgetModifiers();
                spaceDown = false;
                panAnchor = null;
                showCursor();
                stopAutoscroll(); // a modal dialog or another window took the release
            }
        });
        setTransferHandler(new CanvasTransferHandler(this));
        listenForDragExit();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateAnimation();
            }
        });
        frameTimer = new Timer(FRAME_MILLISECONDS, event -> refreshSnapshot());
        antsTimer = new Timer(ANTS_MILLISECONDS, event -> animationTick());
        problemPulseTimer = new Timer(ProblemPulse.TICK_MILLISECONDS, event -> problemPulseTick());
        autoscrollTimer = new Timer(AUTOSCROLL_MILLISECONDS, event -> autoscrollTick());
        viewport.addChangeListener(() -> {
            if (!movingWithTheTravel) {
                viewAnimator.stop(); // the user (or another move) took the view: it stays where it is
                userZoom = viewport.zoom();
            }
        });
    }

    /** Places the palette entry encoded in {@code transferText} at {@code screenPoint}; false when it is not one. */
    public boolean dropComponent(String transferText, Point screenPoint) {
        Optional<ComponentType> type = PaletteTransfer.decode(transferText)
                .flatMap(typeId -> openDocument.registry().find(typeId));
        if (type.isEmpty()) {
            return false;
        }
        GridPoint raw = viewport.snap(screenPoint.x, screenPoint.y);
        ComponentPlacement.place(this, type.get(),
                ComponentPlacement.snapped(this, type.get(), raw, Rotation.R0).position(), Rotation.R0);
        useDefaultTool();
        requestFocusInWindow();
        return true;
    }

    /**
     * Shows the fragment encoded in {@code fragmentJson} floating at {@code screenPoint} (its top-left there), ready
     * to be fixed with a click; false, changing nothing, when the text is not a usable fragment. A drag that started
     * on this canvas and comes back here moves the dragged elements instead.
     */
    public boolean dropFragment(String fragmentJson, Point screenPoint) {
        return dropFragment(fragmentJson, Optional.empty(), screenPoint);
    }

    /** Like {@link #dropFragment(String, Optional, Point, boolean)} without Ctrl. */
    boolean dropFragment(String fragmentJson, Optional<DragOrigin> origin, Point screenPoint) {
        return dropFragment(fragmentJson, origin, screenPoint, false);
    }

    /**
     * Drops a fragment at {@code screenPoint}, with the {@code origin} the drag carried (or this canvas's own drag out
     * in progress). From a view of this document and without {@code copyRequested}: the dragged elements move by the
     * distance from the press to the drop (one step). With {@code copyRequested} (Ctrl): a copy is pasted at once so
     * the grabbed point lands under the pointer, and selected (one step). Otherwise the fragment floats with its
     * top-left at the drop point. False, changing nothing, when the text is not a usable fragment.
     */
    boolean dropFragment(String fragmentJson, Optional<DragOrigin> origin, Point screenPoint, boolean copyRequested) {
        GridPoint at = viewport.snap(screenPoint.x, screenPoint.y);
        Optional<DragOrigin> source = origin.or(() -> Optional.ofNullable(dragOut));
        if (source.isPresent() && !copyRequested && source.get().canvas().openDocument() == openDocument) {
            moveHere(source.get(), at);
            return true;
        }
        Optional<CircuitFragment> fragment = readFragment(fragmentJson);
        if (fragment.isEmpty()) {
            return false;
        }
        source.ifPresent(drag -> drag.canvas().endDragOut());
        announceIncomingFragment(fragment.get());
        if (copyRequested) {
            GridPoint offset = source.map(DragOrigin::grabOffset).orElse(GridPoint.ORIGIN);
            useDefaultTool();
            GridPoint landing = SnappedPlacement.ofFragment(this, fragment.get(),
                    fragment.get().registryFor(openDocument.registry()), at.translate(-offset.x(), -offset.y()))
                    .position();
            PasteCommand paste = new PasteCommand(fragment.get(), landing);
            openDocument.execute(paste);
            selection.set(paste.pastedIds());
        } else {
            setTool(new FloatingSelectionTool(fragment.get(), at));
        }
        requestFocusInWindow();
        return true;
    }

    private Optional<CircuitFragment> readFragment(String fragmentJson) {
        try {
            CircuitFragment fragment = new FragmentFormat(openDocument.registry()).read(fragmentJson);
            return fragment.isEmpty() ? Optional.empty() : Optional.of(fragment);
        } catch (CircuitFormatException | RuntimeException exception) {
            // Dropped text comes from anywhere: a fragment the reader cannot handle is just not accepted.
            return Optional.empty();
        }
    }

    /** Where dropped circuit files go (the window opens them). */
    public void setFileDropHandler(Consumer<List<Path>> handler) {
        fileDropHandler = handler;
    }

    Consumer<List<Path>> fileDropHandler() {
        return fileDropHandler;
    }

    /**
     * Switches a move of the selection in progress into a drag out of the canvas; false, leaving the move alone, when
     * nothing is moving or the selection gives no fragment to transfer.
     */
    boolean beginDragOut() {
        if (!(tool instanceof SelectTool select) || selection.isEmpty()) {
            return false;
        }
        Optional<GridPoint> pressed = select.movingFrom();
        if (pressed.isEmpty()) {
            return false;
        }
        Optional<String> fragmentJson = selectionFragmentJson();
        Optional<GridPoint> topLeft = SelectionBounds.topLeft(openDocument, selection.effectiveIds());
        if (fragmentJson.isEmpty() || topLeft.isEmpty()) {
            return false;
        }
        select.cancelMove(this);
        GridPoint pressPoint = pressed.get();
        dragOut = new DragOrigin(this, selection.effectiveIds(), pressPoint,
                new GridPoint(pressPoint.x() - topLeft.get().x(), pressPoint.y() - topLeft.get().y()));
        dragOutJson = fragmentJson.get();
        return true;
    }

    void endDragOut() {
        dragOut = null;
        dragOutJson = null;
        dragOutLeft();
    }

    Optional<DragOrigin> dragOrigin() {
        return Optional.ofNullable(dragOut);
    }

    /** What a drag out carries: the fragment taken when it began, or the current selection otherwise. */
    Optional<String> transferFragmentJson() {
        return dragOutJson != null ? Optional.of(dragOutJson) : selectionFragmentJson();
    }

    boolean isDraggingOut() {
        return dragOut != null;
    }

    /** The drag out is over this canvas again at {@code point} (canvas pixels): preview the move it would make. */
    void dragOutOver(Point point) {
        if (dragOut == null) {
            return;
        }
        GridPoint at = viewport.snap(point.x, point.y);
        dragOutPointer = viewport.screenToGrid(point.x, point.y);
        SnappedMove snapped = SnappedMove.of(this, dragOut.ids(), false, at.x() - dragOut.pressPoint().x(),
                at.y() - dragOut.pressPoint().y());
        GridPoint delta = new GridPoint(snapped.deltaX(), snapped.deltaY());
        if (!delta.equals(dragOutPreview)) {
            dragOutPreview = delta;
            repaint();
        }
    }

    /** The drag out left this canvas (or ended): no preview. */
    void dragOutLeft() {
        dragOutMoves.clear();
        dragOutPointer = null;
        if (dragOutPreview != null) {
            dragOutPreview = null;
            repaint();
        }
    }

    /**
     * The new contacts of the move being previewed (spec §3.7), with the wires following as the drop will move them;
     * none when no move is previewed.
     */
    List<NewContact> dragOutContacts() {
        if (dragOut == null || dragOutPreview == null) {
            return List.of();
        }
        return dragOutMoves.contacts(this, dragOut.ids(), dragOutPreview.x(), dragOutPreview.y(),
                selectionMoves.followMode(), false);
    }

    /** The move being previewed, as a delta in grid units. */
    Optional<GridPoint> dragOutPreview() {
        return Optional.ofNullable(dragOutPreview);
    }

    /** True while a move or a rubber band is held at the edge and the view scrolls under it. */
    boolean isAutoscrolling() {
        return autoscrollEvent != null;
    }

    /** Tests call {@link #autoscrollTick()} themselves instead of the Swing timer, so positions are exact. */
    void useManualAutoscrollClock() {
        automaticAutoscrollClock = false;
        autoscrollTimer.stop();
    }

    /** Replaces the "this point (canvas coordinates) is over another window" check, for headless tests. */
    void overrideWindowProbe(Predicate<Point> check) {
        overAnotherWindow = check;
    }

    /** One autoscroll step: scrolls the view and replays the last drag, so the move or the band follows the pointer. */
    void autoscrollTick() {
        if (autoscrollEvent == null || !(tool instanceof SelectTool select) || !select.autoscrolls()) {
            stopAutoscroll();
            return;
        }
        Point step = EdgeAutoscroll.step(getWidth(), getHeight(), autoscrollEvent.getPoint());
        if (step.x == 0 && step.y == 0) {
            stopAutoscroll();
            return;
        }
        viewport.panBy(step.x, step.y);
        tool.mouseDragged(this, convert(autoscrollEvent));
        repaint();
    }

    /** Starts or stops the autoscroll after a drag to {@code event}. */
    private void followEdge(MouseEvent event) {
        Point step = EdgeAutoscroll.step(getWidth(), getHeight(), event.getPoint());
        boolean wanted = tool instanceof SelectTool select && select.autoscrolls() && (step.x != 0 || step.y != 0);
        if (!wanted) {
            stopAutoscroll();
            return;
        }
        autoscrollEvent = event;
        if (automaticAutoscrollClock && !autoscrollTimer.isRunning()) {
            autoscrollTimer.start();
        }
    }

    private void stopAutoscroll() {
        autoscrollEvent = null;
        autoscrollTimer.stop();
    }

    /**
     * The point is over a showing window of this application that is neither this canvas's window nor owned by it
     * (popups, tooltips). Windows of other programs are invisible here, so dragging over them keeps the move. A VSim
     * window overlapping this canvas's own frame is not detected there: AWT gives no z-order between windows.
     */
    private boolean isOverAnotherWindow(Point canvasPoint) {
        if (!isShowing()) {
            return false;
        }
        Point screenPoint = new Point(canvasPoint);
        SwingUtilities.convertPointToScreen(screenPoint, this);
        Window own = SwingUtilities.getWindowAncestor(this);
        if (own == null || own.getBounds().contains(screenPoint)) {
            return false;
        }
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && !isOwnedBy(window, own) && window.getBounds().contains(screenPoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOwnedBy(Window window, Window own) {
        for (Window current = window; current != null; current = current.getOwner()) {
            if (current == own) {
                return true;
            }
        }
        return false;
    }

    Optional<String> selectionFragmentJson() {
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        CircuitFragment fragment = CircuitFragment.of(openDocument.document(), selection.effectiveIds(),
                openDocument.registry());
        return fragment.isEmpty() ? Optional.empty()
                : Optional.of(new FragmentFormat(openDocument.registry()).write(fragment));
    }

    /** A drag out of {@code origin}'s canvas ends here: a move by the distance from its press to {@code at}. */
    private void moveHere(DragOrigin origin, GridPoint at) {
        List<ElementId> existing = origin.ids().stream()
                .filter(id -> openDocument.document().element(id).isPresent())
                .toList();
        // the magnet as in the preview of dragOutOver: the moved elements are not targets
        SnappedMove snapped = SnappedMove.of(this, existing, false, at.x() - origin.pressPoint().x(),
                at.y() - origin.pressPoint().y());
        int deltaX = snapped.deltaX();
        int deltaY = snapped.deltaY();
        origin.canvas().endDragOut();
        if (!existing.isEmpty()) {
            // selected before the move, so a wire the clean-up merges is replaced by its survivor, not put back
            selection.set(existing);
        }
        if ((deltaX != 0 || deltaY != 0) && !existing.isEmpty()) {
            selectionMoves.move(openDocument, selection, existing, deltaX, deltaY, selectionMoves.followMode());
        }
        requestFocusInWindow();
    }

    private void paintDragOutPreview(Graphics2D graphics) {
        if (dragOut == null || dragOutPreview == null || dragOutPreview.equals(GridPoint.ORIGIN)) {
            return;
        }
        for (ElementId id : dragOut.ids()) {
            openDocument.document().element(id).ifPresent(element ->
                    paintGhost(graphics, element.translate(dragOutPreview.x(), dragOutPreview.y())));
        }
        ContactDots.paint(graphics, this, dragOutContacts(), Optional.ofNullable(dragOutPointer));
    }

    /** Hides the preview when the drag leaves the canvas; Swing's drop target takes extra listeners. */
    private void listenForDragExit() {
        DropTarget dropTarget = getDropTarget();
        if (dropTarget == null) {
            return; // headless: there is no drop target and no drag ever enters
        }
        try {
            dropTarget.addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragExit(DropTargetEvent event) {
                    dragOutLeft();
                }

                @Override
                public void drop(DropTargetDropEvent event) {
                    // The transfer handler takes the drop.
                }
            });
        } catch (TooManyListenersException exception) {
            // A plain DropTarget takes one listener only; the preview then stays until the drag ends.
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        frameTimer.start();
        animationSuspended = false;
        watchWindow(SwingUtilities.getWindowAncestor(this));
        updateAnimation();
    }

    @Override
    public void removeNotify() {
        stopAutoscroll();
        frameTimer.stop();
        animationSuspended = true;
        antsTimer.stop();
        problemPulseTimer.stop();
        watchWindow(null);
        viewAnimator.finish();
        super.removeNotify();
    }

    public void dispose() {
        disposed = true;
        edgeController.cancel(); // the pane closes: a guide being pulled goes with it
        viewAnimator.stop();
        stopAutoscroll();
        frameTimer.stop();
        animationSuspended = true;
        antsTimer.stop();
        problemPulseTimer.stop();
        watchWindow(null);
        openDocument.document().removeListener(documentListener);
        selection.removeListener(selectionListener);
    }

    public Tool tool() {
        return tool;
    }

    /** The snapped grid point under the mouse while the mouse is over the canvas. */
    public Optional<GridPoint> mouseGridPoint() {
        return Optional.ofNullable(mousePoint);
    }

    public Tool defaultTool() {
        return defaultTool;
    }

    public void bindKey(KeyStroke stroke, String name, Action action) {
        getInputMap(WHEN_FOCUSED).put(stroke, name);
        getActionMap().put(name, action);
    }

    /** Removes the binding made by {@link #bindKey}. */
    public void unbindKey(KeyStroke stroke, String name) {
        getInputMap(WHEN_FOCUSED).remove(stroke);
        getActionMap().remove(name);
    }

    /** Told about each fragment dropped or pasted on this canvas, before it is pasted or starts floating. */
    public void addIncomingFragmentListener(Consumer<CircuitFragment> listener) {
        incomingFragmentListeners.add(listener);
    }

    public void removeIncomingFragmentListener(Consumer<CircuitFragment> listener) {
        incomingFragmentListeners.remove(listener);
    }

    /** Tells the incoming-fragment listeners that {@code fragment} is about to be placed (a drop or a paste). */
    public void announceIncomingFragment(CircuitFragment fragment) {
        incomingFragmentListeners.forEach(listener -> listener.accept(fragment));
    }

    /** Runs {@code listener} with every tool this canvas switches to. Returns its removal. */
    public Runnable addToolListener(Consumer<Tool> listener) {
        toolListeners.add(listener);
        return () -> toolListeners.remove(listener);
    }

    public void addSnapshotListener(Consumer<SimulationSnapshot> listener) {
        snapshotListeners.add(listener);
    }

    public void removeSnapshotListener(Consumer<SimulationSnapshot> listener) {
        snapshotListeners.remove(listener);
    }

    /** For tests: features must remove the snapshot listeners they add. */
    public int snapshotListenerCount() {
        return snapshotListeners.size();
    }

    /** Runs {@code listener} whenever the view (zoom, centre) changes through the canvas. Returns its removal. */
    public Runnable addViewListener(Runnable listener) {
        viewListeners.add(listener);
        return () -> viewListeners.remove(listener);
    }

    /**
     * Runs {@code observer} at every mouse press on this canvas, before anything else handles it (the window makes
     * this pane the active one before the tool sees the press). Returns its removal.
     */
    public Runnable addPressObserver(Runnable observer) {
        pressObservers.add(observer);
        return () -> pressObservers.remove(observer);
    }

    public void zoomIn() {
        viewport.zoomIn(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void zoomOut() {
        viewport.zoomOut(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void resetZoom() {
        viewport.resetZoom(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void centerCircuit() {
        CircuitBounds.of(openDocument.document(), openDocument.registry())
                .ifPresent(bounds -> viewport.center(bounds, getWidth(), getHeight()));
        viewChanged();
    }

    /** The zoom and the grid point in the middle of the canvas, to restore later or to copy into another view. */
    public ViewState viewState() {
        Point2D center = viewport.screenToGrid(getWidth() / 2.0, getHeight() / 2.0);
        return new ViewState(viewport.zoom(), center.getX(), center.getY());
    }

    /** Shows {@code state}: its zoom (clamped to the viewport limits) with its center in the middle of the canvas. */
    public void applyViewState(ViewState state) {
        viewport.showAt(state.zoom(), state.centerX(), state.centerY(), getWidth(), getHeight());
        viewChanged();
    }

    /**
     * Centers the view on {@code bounds} (grid units) at once, without changing the zoom. For moves that must not
     * animate; the ones that take the user somewhere use {@link #travelTo}.
     */
    public void centerOn(Bounds bounds) {
        viewport.center(bounds, getWidth(), getHeight());
        viewChanged();
    }

    /**
     * Takes the view to {@code bounds} (grid units): centred on them at the user's zoom, zooming out only as far as
     * needed for them to fit. With view moves animated ({@link #setAnimateViewMoves}) it travels there: an eased pan
     * when the target is near, a «fly to» (out and back in) when it is far; a call during a travel continues from
     * where the view is. Otherwise, before the canvas has a size or once it is disposed, it jumps. The user's zoom is
     * the last one the user chose, so after a zoom-out to fit, the next target comes back to it.
     */
    public void travelTo(Bounds bounds) {
        int width = getWidth();
        int height = getHeight();
        ViewState target = new ViewState(
                Math.max(Viewport.MINIMUM_ZOOM, Math.min(userZoom, fittingZoom(bounds, width, height))),
                bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0);
        if (disposed || width <= 0 || height <= 0 || !animateViewMoves.getAsBoolean()) {
            viewAnimator.stop();
            movingWithTheTravel = true; // a jump to a target is not the user's zoom either
            try {
                viewport.showAt(target.zoom(), target.centerX(), target.centerY(), width, height);
            } finally {
                movingWithTheTravel = false;
            }
            viewChanged();
            return;
        }
        viewAnimator.travel(viewState(), target, width, height);
    }

    /** The largest zoom at which {@code bounds} fill at most nine tenths of the canvas each way. */
    private static double fittingZoom(Bounds bounds, int width, int height) {
        double fitting = Double.MAX_VALUE;
        if (bounds.width() > 0 && width > 0) {
            fitting = Math.min(fitting, width * FIT_FRACTION / (bounds.width() * (double) GridPoint.PIXELS_PER_UNIT));
        }
        if (bounds.height() > 0 && height > 0) {
            fitting = Math.min(fitting, height * FIT_FRACTION / (bounds.height() * (double) GridPoint.PIXELS_PER_UNIT));
        }
        return fitting;
    }

    private void showTravelFrame(ViewState state) {
        movingWithTheTravel = true;
        try {
            viewport.showAt(state.zoom(), state.centerX(), state.centerY(), getWidth(), getHeight());
        } finally {
            movingWithTheTravel = false;
        }
        viewChanged();
    }

    /**
     * Pans the view by ({@code deltaX}, {@code deltaY}) pixels because the canvas itself moved in its window (a header
     * above it showed or went), so the circuit stays where it was on screen. It is not a user move: a travel in
     * progress goes on (its frames derive the view from the centre and the current size).
     */
    public void compensateCanvasMove(double deltaX, double deltaY) {
        movingWithTheTravel = true;
        try {
            viewport.panBy(deltaX, deltaY);
        } finally {
            movingWithTheTravel = false;
        }
        viewChanged();
    }

    /** Whether {@link #travelTo} animates; read at each call and each frame (the preference can change mid-flight). */
    public void setAnimateViewMoves(BooleanSupplier animate) {
        animateViewMoves = animate;
    }

    /** True while the view travels to a target. */
    public boolean isTravelling() {
        return viewAnimator.isTravelling();
    }

    /** Drives the travels with {@code ticker} and {@code clock} (milliseconds) instead of a Swing timer: for tests. */
    public void useViewTicker(FrameTicker ticker, LongSupplier clock) {
        viewAnimator.use(ticker, clock);
    }

    public void addOverlay(CanvasOverlay overlay) {
        overlays.add(overlay);
        repaint();
    }

    public void removeOverlay(CanvasOverlay overlay) {
        overlays.remove(overlay);
        repaint();
    }

    /**
     * Puts {@code component} on top of the canvas (M6.5: the inline text editor); it is a real Swing child, so it
     * takes the mouse and the keyboard, and never shows in an exported image. The caller sets its bounds, in canvas
     * pixels. The returned runnable takes it away again.
     */
    public Runnable addFloatingComponent(JComponent component) {
        add(component, 0);
        repaint();
        return () -> {
            Rectangle last = component.getBounds();
            remove(component);
            repaint(last);
        };
    }

    /** Runs {@code listener} after every pan or zoom, whoever made it. Returns its removal. */
    public Runnable addViewportListener(Runnable listener) {
        return viewport.addChangeListener(listener);
    }

    /** {@code provider} receives the mouse position in grid units; empty means no tooltip. */
    public void setToolTipProvider(Function<Point2D, Optional<String>> provider) {
        toolTipProvider = provider;
        toolTipProviderSet = true;
        updateToolTipRegistration();
    }

    /** Drops the tooltip provider; the canvas leaves the tooltip manager unless an override is still installed. */
    public void clearToolTipProvider() {
        toolTipProvider = gridPoint -> Optional.empty();
        toolTipProviderSet = false;
        updateToolTipRegistration();
    }

    /**
     * Adds a tooltip source consulted before the provider (M6.1-E: the waveform markers): the first override that
     * answers wins. {@code override} receives the mouse position in grid units. The returned runnable removes it.
     */
    public Runnable addToolTipOverride(Function<Point2D, Optional<String>> override) {
        toolTipOverrides.add(override);
        updateToolTipRegistration();
        return () -> {
            toolTipOverrides.remove(override);
            updateToolTipRegistration();
        };
    }

    /**
     * Adds a hook consulted on every primary-button press before the tool (M6.1-E: a click on a waveform marker);
     * when one returns true the press, and the drag and release that follow it, never reach the tool. The returned
     * runnable removes it.
     */
    public Runnable addPressInterceptor(Predicate<CanvasMouseEvent> interceptor) {
        pressInterceptors.add(interceptor);
        return () -> pressInterceptors.remove(interceptor);
    }

    /**
     * For tests (every extension in every pane): how many press interceptors, tooltip overrides and incoming-fragment
     * listeners this canvas holds, and whether a tooltip provider is set.
     */
    public PaneHooks paneHooks() {
        return new PaneHooks(pressInterceptors.size(), toolTipOverrides.size(), incomingFragmentListeners.size(),
                toolTipProviderSet);
    }

    /** What {@link #paneHooks()} counts. */
    public record PaneHooks(int pressInterceptors, int toolTipOverrides, int incomingFragmentListeners,
                            boolean toolTipProvider) {
    }

    /**
     * Adds a handle at the canvas edges (a ruler; later a split grip) that takes presses before the press interceptors
     * and the tool. The returned runnable removes it.
     */
    public Runnable addEdgeHandle(EdgeHandle handle) {
        Runnable remover = edgeController.add(handle);
        updateToolTipRegistration();
        repaint();
        return () -> {
            remover.run();
            updateToolTipRegistration();
            showCursor();
            repaint();
        };
    }

    /** For tests: the edge handles installed now. */
    public List<EdgeHandle> edgeHandles() {
        return edgeController.handles();
    }

    /** True while a gesture started on an edge handle (pulling or moving a guide) is running. */
    public boolean isEdgeGestureActive() {
        return edgeController.isActive();
    }

    /**
     * Cancels the edge gesture in progress, if any (Escape, a guide list changed elsewhere, the guides hidden); its
     * release then reaches nobody. False when none was running.
     */
    public boolean cancelEdgeGesture() {
        boolean cancelled = edgeController.cancel();
        if (cancelled) {
            showCursor();
            repaint();
        }
        return cancelled;
    }

    /** For tests: features must remove the overlays they add. */
    public int overlayCount() {
        return overlays.size();
    }

    /** For tests: the overlays installed now, in paint order. */
    public List<CanvasOverlay> overlays() {
        return List.copyOf(overlays);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Optional<String> edge = edgeController.tooltip(convert(event));
        if (edge.isPresent()) {
            return edge.get();
        }
        Point2D gridPoint = viewport.screenToGrid(event.getX(), event.getY());
        for (Function<Point2D, Optional<String>> override : toolTipOverrides) {
            Optional<String> text = override.apply(gridPoint);
            if (text.isPresent()) {
                return text.get();
            }
        }
        return toolTipProvider.apply(gridPoint).orElse(null);
    }

    private void updateToolTipRegistration() {
        if (toolTipProviderSet || !toolTipOverrides.isEmpty() || !edgeController.handles().isEmpty()) {
            ToolTipManager.sharedInstance().registerComponent(this);
        } else {
            ToolTipManager.sharedInstance().unregisterComponent(this);
        }
    }

    private boolean interceptsPress(CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return false;
        }
        for (Predicate<CanvasMouseEvent> interceptor : pressInterceptors) {
            if (interceptor.test(event)) {
                return true;
            }
        }
        return false;
    }

    // ToolContext

    @Override
    public OpenDocument openDocument() {
        return openDocument;
    }

    @Override
    public Selection selection() {
        return selection;
    }

    @Override
    public double selectionDashPhase() {
        return marchingAnts.phase();
    }

    /**
     * True while the marching-ants clock runs: the canvas is on screen and a selection exists or the tool is dragging
     * a band.
     */
    boolean isAnimatingSelection() {
        return antsTimer.isRunning();
    }

    /** True while the pulse clock runs: the canvas is on screen and a short-circuited or mismatched wire is shown. */
    boolean isAnimatingProblems() {
        return problemPulseTimer.isRunning();
    }

    double problemPulsePhase() {
        return problemPulse.phase();
    }

    /** Replaces the on-screen check (showing and not minimized), so headless tests can show or hide the canvas. */
    void overrideOnScreen(BooleanSupplier check) {
        onScreen = check;
        updateAnimation();
    }

    /** Showing, and its window is not minimized: only then are the marching ants worth a repaint. */
    private boolean isOnScreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        boolean minimized = window instanceof Frame frame && (frame.getExtendedState() & Frame.ICONIFIED) != 0;
        return isShowing() && !minimized;
    }

    /**
     * Follows minimize and restore of {@code window} (null: of none), which do not change {@code isShowing}, and its
     * deactivation, which ends an autoscroll whose release will not arrive.
     */
    private void watchWindow(Window window) {
        if (watchedWindow != null) {
            watchedWindow.removeWindowStateListener(windowStateListener);
            watchedWindow.removeWindowListener(windowDeactivationListener);
        }
        watchedWindow = window;
        if (window != null) {
            window.addWindowStateListener(windowStateListener);
            window.addWindowListener(windowDeactivationListener);
        }
    }

    /** One clock tick: re-checks that the animation is still needed, so it stops by itself when nothing is left. */
    void animationTick() {
        updateAnimation();
        if (antsTimer.isRunning()) {
            marchingAnts.advance();
            repaint();
        }
    }

    /** One pulse tick: re-checks that a problem wire is still shown, so the clock stops by itself when none is. */
    void problemPulseTick() {
        updateProblemPulse();
        if (problemPulseTimer.isRunning()) {
            problemPulse.advance();
            repaint();
        }
    }

    /**
     * The highlights, bus labels and merged top-level nets of this canvas's frames, shared by its renderer, its
     * overlays and its tooltips so a frame is worked out once.
     */
    public HighlightCache highlights() {
        return renderer.highlights();
    }

    /** Whether a wire of a short circuit or a width mismatch is on the canvas now (what the renderer glows). */
    private boolean problemWireShown() {
        HighlightCache highlights = renderer.highlights();
        return !highlights.shortCircuits(openDocument.document(), openDocument.netlist(), openDocument.netlistToken(),
                displayedSnapshot).wires().isEmpty()
                || !highlights.widthMismatches(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), displayedSnapshot).wires().isEmpty();
    }

    private void updateProblemPulse() {
        boolean needed = !animationSuspended && onScreen.getAsBoolean() && problemWireShown();
        if (needed && !problemPulseTimer.isRunning()) {
            problemPulseTimer.start();
        } else if (!needed && problemPulseTimer.isRunning()) {
            problemPulseTimer.stop();
        }
    }

    private void updateAnimation() {
        updateProblemPulse();
        boolean needed = !animationSuspended && onScreen.getAsBoolean()
                && (!selection.isEmpty() || tool.animatesOutline());
        if (needed && !antsTimer.isRunning()) {
            antsTimer.start();
        } else if (!needed && antsTimer.isRunning()) {
            antsTimer.stop();
        }
    }

    @Override
    public HitTester hitTester() {
        return hitTester;
    }

    /** How the background grid is drawn (Ver ▸ Cuadrícula); dots until the grid extension says otherwise. */
    public GridStyle gridStyle() {
        return gridStyle;
    }

    public void setGridStyle(GridStyle style) {
        GridStyle chosen = Objects.requireNonNull(style, "style");
        if (chosen != gridStyle) {
            gridStyle = chosen;
            repaint();
        }
    }

    /** The units between the grid lines (Ver ▸ Cuadrícula ▸ Separación); 2 until the grid extension says otherwise. */
    public int gridLineSpacing() {
        return gridLineSpacing;
    }

    public void setGridLineSpacing(int spacing) {
        if (spacing < 1) {
            throw new IllegalArgumentException("grid line spacing " + spacing);
        }
        if (spacing != gridLineSpacing) {
            gridLineSpacing = spacing;
            repaint();
        }
    }

    @Override
    public Viewport viewport() {
        return viewport;
    }

    /** How the tools and the drops of this canvas snap (Ajustar); nothing snaps until the snap extension says so. */
    @Override
    public Snapper snapper() {
        return snapper;
    }

    /** Makes the tools and drops of this canvas snap with {@code next}; the returned runnable puts back none. */
    public Runnable useSnapper(Snapper next) {
        snapper = Objects.requireNonNull(next, "next");
        return () -> {
            if (snapper == next) {
                snapper = Snapper.none();
            }
        };
    }

    @Override
    public ColorScheme scheme() {
        return renderer.scheme();
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        paintGhost(graphics, element, renderer.registry());
    }

    /** A salida's ghost takes the colour of the entrada of its name in this circuit (placing, moving it). */
    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry) {
        renderer.paintGhost(graphics, element, registry, tunnelColorsFor(element));
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry,
                           Map<String, Integer> tunnelColors) {
        renderer.paintGhost(graphics, element, registry, tunnelColors);
    }

    /** The circuit's tunnel colours when {@code element} is a salida (the only ghost that needs them), else none. */
    private Map<String, Integer> tunnelColorsFor(DocumentElement element) {
        return element instanceof Component component
                && Tunnels.kindOf(component).orElse(null) == TunnelKind.EXIT ? tunnelColors() : Map.of();
    }

    /**
     * The circuit's tunnel colours by name, worked out again only when the document changed: a moved block paints one
     * ghost per salida in every frame, and each would otherwise walk the whole circuit.
     */
    Map<String, Integer> tunnelColors() {
        CircuitDocument document = openDocument.document();
        if (document != tunnelColorsDocument || document.revision() != tunnelColorsRevision) {
            tunnelColorsCache = Tunnels.colorsByName(document);
            tunnelColorsDocument = document;
            tunnelColorsRevision = document.revision();
        }
        return tunnelColorsCache;
    }

    @Override
    public void setTool(Tool next) {
        cancelEdgeGesture();
        tool.deactivated(this);
        tool = next;
        next.activated(this);
        toolCursor = next.cursor();
        showCursor();
        updateAnimation();
        toolListeners.forEach(listener -> listener.accept(next));
        repaint();
    }

    @Override
    public void useDefaultTool() {
        setTool(defaultTool);
    }

    /**
     * Gives the current tool a key pressed while another part of the window has the focus (Escape cancelling a
     * placement from the palette); true when the tool used it.
     */
    public boolean pressKeyOnTool(int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_ESCAPE && cancelEdgeGesture()) {
            return true;
        }
        boolean used = tool.keyPressed(this, keyCode, shiftDown);
        updateAnimation();
        return used;
    }

    @Override
    public void setCursor(CanvasCursor cursor) {
        toolCursor = awtCursor(cursor);
        showCursor();
    }

    /** The tool's cursor, or MOVE while Espacio is held or the middle button pans, or an edge handle's over it. */
    private void showCursor() {
        if (spaceDown || panAnchor != null) {
            setCursor(awtCursor(CanvasCursor.MOVE));
            return;
        }
        setCursor(edgeController.cursor().orElse(toolCursor));
    }

    /**
     * The AWT cursor of {@code cursor} (AWT keeps one instance of each predefined cursor; the selection cursors are
     * drawn once, lazily).
     */
    private static Cursor awtCursor(CanvasCursor cursor) {
        return switch (cursor) {
            case DEFAULT -> Cursor.getDefaultCursor();
            case MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            case CROSSHAIR -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            case HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            case RESIZE_NORTH -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case RESIZE_SOUTH -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case RESIZE_EAST -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case RESIZE_WEST -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case RESIZE_NORTH_EAST -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case RESIZE_NORTH_WEST -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case RESIZE_SOUTH_EAST -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            case RESIZE_SOUTH_WEST -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case SLIDE_VERTICAL -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case SLIDE_HORIZONTAL -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case ADD_TO_SELECTION -> SelectionCursors.adding();
            case REMOVE_FROM_SELECTION -> SelectionCursors.removing();
        };
    }

    @Override
    public void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition) {
        host.showContextMenu(this, target, screenPosition);
    }

    @Override
    public SelectionMoves selectionMoves() {
        return selectionMoves;
    }

    @Override
    public void configure(Component component) {
        host.configure(component);
    }

    @Override
    public void editLabel(DocumentElement element) {
        host.editLabel(element);
    }

    @Override
    public void editValue(Component component) {
        host.editValue(component);
    }

    @Override
    public void editValue(Component component, Value startingValue) {
        host.editValue(component, startingValue);
    }

    // Painting and snapshots

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            renderer.paintBackground(local, viewport, getWidth(), getHeight(), gridStyle, gridLineSpacing);
            local.transform(viewport.documentTransform());
            RenderState state = new RenderState(openDocument.document(), openDocument.netlist(),
                    openDocument.netlistToken(), displayedSnapshot, selection.ids(), marchingAnts.phase(),
                    problemPulse.phase(), tool.hiddenWires(this));
            renderer.paint(local, state);
            for (CanvasOverlay overlay : overlays) {
                overlay.paint(local, state);
            }
            paintDragOutPreview(local);
            tool.paintOverlay(local, this);
        } finally {
            local.dispose();
        }
        Graphics2D screen = (Graphics2D) graphics.create(); // logical pixels: the device transform only
        try {
            edgeController.paint(screen);
        } finally {
            screen.dispose();
        }
    }

    SimulationSnapshot displayedSnapshot() {
        return displayedSnapshot;
    }

    /** Called every frame: publishes a new snapshot and repaints only if something visible changed. */
    void refreshSnapshot() {
        display(openDocument.latestSnapshot());
    }

    /** Shows {@code latest}, whatever its token, and hands it to the snapshot listeners (they check the token). */
    void display(SimulationSnapshot latest) {
        if (latest == displayedSnapshot) {
            return;
        }
        boolean visibleChange = latest.token() != displayedSnapshot.token()
                || !latest.netValues().equals(displayedSnapshot.netValues())
                || !latest.componentStates().equals(displayedSnapshot.componentStates())
                || !latest.problems().equals(displayedSnapshot.problems());
        displayedSnapshot = latest;
        if (visibleChange) {
            updateProblemPulse();
            repaint();
        }
        snapshotListeners.forEach(listener -> listener.accept(latest));
    }

    private void documentChanged(CircuitDocument document, DocumentChange change) {
        selection.retain(change);
        updateProblemPulse();
        repaint();
    }

    private void viewChanged() {
        viewListeners.forEach(Runnable::run);
        repaint();
    }

    private CanvasMouseEvent convert(MouseEvent event) {
        int button = SwingUtilities.isRightMouseButton(event) ? CanvasMouseEvent.SECONDARY
                : SwingUtilities.isMiddleMouseButton(event) ? CanvasMouseEvent.MIDDLE
                : CanvasMouseEvent.PRIMARY;
        return new CanvasMouseEvent(viewport.screenToGrid(event.getX(), event.getY()),
                viewport.snap(event.getX(), event.getY()), new Point2D.Double(event.getX(), event.getY()),
                button, event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.getClickCount());
    }

    private final class MouseHandler extends MouseAdapter {

        private void track(MouseEvent event) {
            mousePoint = contains(event.getPoint()) ? viewport.snap(event.getX(), event.getY()) : null;
        }

        /** Buttons past the third (sideways scrolling, back/forward) never reach the tool as a click. */
        private static boolean isExtraButton(MouseEvent event) {
            return event.getButton() > MouseEvent.BUTTON3;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            pressObservers.forEach(Runnable::run);
            if (isExtraButton(event)) {
                scrollSideways(event.getButton());
                return;
            }
            track(event);
            endDragOut(); // a drag out that never reported its end must not turn a later drop into a move
            requestFocusInWindow();
            if (SwingUtilities.isMiddleMouseButton(event) || (spaceDown && SwingUtilities.isLeftMouseButton(event))) {
                panAnchor = event.getPoint();
                showCursor();
                return;
            }
            CanvasMouseEvent converted = convert(event);
            if (edgeController.press(converted)) {
                showCursor();
                repaint();
                return;
            }
            pressIntercepted = interceptsPress(converted);
            if (pressIntercepted) {
                return;
            }
            tool.mousePressed(CircuitCanvas.this, converted);
            updateAnimation();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            track(event);
            if (panAnchor != null) {
                viewport.panBy(event.getX() - panAnchor.x, event.getY() - panAnchor.y);
                panAnchor = event.getPoint();
                repaint();
                return;
            }
            if (edgeController.drag(convert(event))) {
                repaint();
                return;
            }
            if (pressIntercepted) {
                return;
            }
            if (!CircuitCanvas.this.contains(event.getPoint()) && overAnotherWindow.test(event.getPoint())
                    && beginDragOut()) {
                stopAutoscroll();
                getTransferHandler().exportAsDrag(CircuitCanvas.this, event, TransferHandler.MOVE);
                return;
            }
            tool.mouseDragged(CircuitCanvas.this, convert(event));
            followEdge(event);
            updateAnimation();
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (isExtraButton(event)) {
                return;
            }
            stopAutoscroll();
            if (panAnchor != null) {
                panAnchor = null;
                showCursor();
                return;
            }
            CanvasMouseEvent converted = convert(event);
            if (edgeController.release(converted)) {
                edgeController.hover(converted);
                showCursor();
                repaint();
                return;
            }
            if (pressIntercepted) {
                pressIntercepted = false;
                return;
            }
            tool.mouseReleased(CircuitCanvas.this, converted);
            updateAnimation();
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            track(event);
            CanvasMouseEvent converted = convert(event);
            if (edgeController.hover(converted)) {
                repaint();
            }
            if (edgeController.hovering()) {
                // over a ruler (or a guide the select tool would grab) a press is the handle's: for the tool the
                // pointer is gone, so a placement ghost or a hover ring never shows there
                if (!toolSeesPointerGone) {
                    toolSeesPointerGone = true;
                    tool.mouseExited(CircuitCanvas.this);
                }
            } else {
                toolSeesPointerGone = false;
                tool.mouseMoved(CircuitCanvas.this, converted);
            }
            showCursor();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            mousePoint = null;
            toolSeesPointerGone = false;
            if (edgeController.clearHover()) {
                showCursor();
                repaint();
            }
            tool.mouseExited(CircuitCanvas.this);
        }

        /** One sideways step, as far as one Shift+wheel step: left like wheel up, right like wheel down. */
        private void scrollSideways(int button) {
            if (button == SCROLL_LEFT_BUTTON) {
                viewport.panBy(WHEEL_SCROLL_PIXELS, 0);
            } else if (button == SCROLL_RIGHT_BUTTON) {
                viewport.panBy(-WHEEL_SCROLL_PIXELS, 0);
            } else {
                return;
            }
            repaint();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent event) {
            double rotation = event.getPreciseWheelRotation();
            if (event.isControlDown()) {
                viewport.zoomAt(event.getX(), event.getY(), viewport.zoom() * Math.pow(Viewport.ZOOM_STEP, -rotation));
                viewChanged();
            } else if (event.isShiftDown()) {
                viewport.panBy(-rotation * WHEEL_SCROLL_PIXELS, 0);
                repaint();
            } else {
                viewport.panBy(0, -rotation * WHEEL_SCROLL_PIXELS);
                repaint();
            }
        }
    }

    private final class KeyHandler extends KeyAdapter {

        /** The (Shift, Ctrl, Alt) last told to the tool; null when unknown. A held key repeats its press. */
        private List<Boolean> lastModifiers;

        /** The keys may change while another window has the focus: the next change is always told. */
        void forgetModifiers() {
            lastModifiers = null;
        }

        @Override
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                spaceDown = true;
                showCursor();
                event.consume();
                return;
            }
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE && cancelEdgeGesture()) {
                event.consume(); // Escape goes to the edge gesture first
                return;
            }
            modifierChanged(event, true);
            if (tool.keyPressed(CircuitCanvas.this, event.getKeyCode(), event.isShiftDown())) {
                event.consume();
            }
            updateAnimation();
        }

        @Override
        public void keyReleased(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                spaceDown = false;
                showCursor();
            }
            modifierChanged(event, false);
        }

        /**
         * Tells the tool about a Shift, Ctrl or Alt press or release, with the keys held after it (the changed key is
         * taken from the event itself, as some platforms still report a released key as down). Other keys: nothing.
         */
        private void modifierChanged(KeyEvent event, boolean pressed) {
            int keyCode = event.getKeyCode();
            if (keyCode != KeyEvent.VK_SHIFT && keyCode != KeyEvent.VK_CONTROL && keyCode != KeyEvent.VK_ALT) {
                return;
            }
            boolean shiftDown = keyCode == KeyEvent.VK_SHIFT ? pressed : event.isShiftDown();
            boolean controlDown = keyCode == KeyEvent.VK_CONTROL ? pressed : event.isControlDown();
            boolean altDown = keyCode == KeyEvent.VK_ALT ? pressed : event.isAltDown();
            List<Boolean> modifiers = List.of(shiftDown, controlDown, altDown);
            if (modifiers.equals(lastModifiers)) {
                return; // the key repeat of a held modifier
            }
            lastModifiers = modifiers;
            if (tool.modifiersChanged(CircuitCanvas.this, shiftDown, controlDown, altDown)) {
                updateAnimation();
            }
        }
    }
}
