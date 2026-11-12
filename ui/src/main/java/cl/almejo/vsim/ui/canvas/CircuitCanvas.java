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
import cl.almejo.vsim.core.document.Guide;
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
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.ComponentPlacement;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.snap.SnappedPlacement;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
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
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The drawing surface of one window. Mouse input goes to the current {@link Tool}. */
public final class CircuitCanvas extends JComponent implements ToolContext {

    static final int FRAME_MILLISECONDS = 16;
    private static final int ANTS_MILLISECONDS = 90;
    /** Automan's star at about 30 frames a second (spec §8.5); a blink only repaints when it turns. */
    private static final int TERMINAL_FRAME_MILLISECONDS = 33;
    private static final int GLOW_FRAME_MILLISECONDS = 33;
    /** Device pixels added around a moving terminal's area so antialiased edges are repainted too. */
    private static final int TERMINAL_REPAINT_MARGIN = 2;
    private static final double WHEEL_SCROLL_PIXELS = 40;
    /**
     * A touchpad's sideways swipe reaches Swing on Linux as presses of these extra mouse buttons (X11 buttons 6 and 7),
     * not as a wheel turn; they scroll like Shift+wheel up and down.
     */
    private static final int SCROLL_LEFT_BUTTON = 4;
    private static final int SCROLL_RIGHT_BUTTON = 5;

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
    private final PieceGlow pieceGlow = new PieceGlow();
    private FrameTicker glowTicker = FrameTicker.swing(GLOW_FRAME_MILLISECONDS);
    private LongSupplier glowClock = () -> System.nanoTime() / 1_000_000;
    private final Timer problemPulseTimer;
    /** The Terminals' animation clock: runs only while a moving terminal is in view. */
    private FrameTicker terminalTicker = FrameTicker.swing(TERMINAL_FRAME_MILLISECONDS);
    /** Preferencias ▸ «Animaciones»: off, the Terminals stay still (cursor on, a still star, no fading). */
    private BooleanSupplier animations = () -> true;
    private Supplier<Optional<String>> findActionKey = Optional::empty;
    private final CanvasAutoscroll autoscroll = new CanvasAutoscroll(this);
    private final List<Consumer<Tool>> toolListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SimulationSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> viewListeners = new CopyOnWriteArrayList<>();
    /** Told when the view moves by anything but a travel (the user's pan or zoom, a centring). */
    private final List<Runnable> viewTakenListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CircuitFragment>> incomingFragmentListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> placementListeners = new CopyOnWriteArrayList<>();
    private final List<CanvasOverlay> overlays = new CopyOnWriteArrayList<>();
    private final List<ScreenOverlay> screenOverlays = new CopyOnWriteArrayList<>();
    private final List<Function<Point2D, Optional<String>>> toolTipOverrides = new CopyOnWriteArrayList<>();
    private final List<Predicate<CanvasMouseEvent>> pressInterceptors = new CopyOnWriteArrayList<>();
    private final List<BooleanSupplier> escapeClaims = new CopyOnWriteArrayList<>();
    private final EdgeController edgeController = new EdgeController(this::getSize);
    private final DocumentListener documentListener;
    private final Runnable refreshListener = this::bodyRefreshed;
    private final SelectionMoves selectionMoves;
    private FileDropTarget fileDropTarget = (files, gridPosition) -> {
    };
    private final CanvasDragOut canvasDragOut = new CanvasDragOut(this);
    private Function<Point2D, Optional<String>> toolTipProvider = gridPoint -> Optional.empty();
    private boolean toolTipProviderSet;
    private boolean pressIntercepted;
    /** The other panes of this canvas's window (split views); none outside a split window. */
    private Supplier<List<CircuitCanvas>> otherPanes = List::of;
    /** A press reached the tool and its button is still down: the gesture is this canvas's, wherever the pointer is. */
    private boolean gestureUnderWay;
    /** The other pane the pointer is over during that gesture, and what the tool sees there; null over this one. */
    private CrossPaneContext pointerPaneContext;
    /**
     * The other pane whose tool drew between clicks when the primary or secondary button went down here: that press,
     * its drags and its release go to that tool (a continuous wire's corner placed in this pane); null otherwise.
     */
    private CircuitCanvas pressForwardedTo;
    /** The last pointer event over this canvas (a move, a drag, the wheel); null while the pointer is elsewhere. */
    private MouseEvent pointerEvent;
    /** Told when whether this canvas's tool draws between clicks changes (Deshacer drops a corner while it does). */
    private final List<Runnable> drawingListeners = new CopyOnWriteArrayList<>();
    /** What {@link #drawingListeners} were last told about this canvas's tool. */
    private boolean drawingTold;
    /** The gesture phrase the host was last told, so it hears of changes only. */
    private String gesturePhraseTold = "";
    /** A preference the phrase names (the invert key, «Los cables siguen») changed mid-gesture. */
    private final Runnable preferenceListener = this::publishGesturePhrase;
    /** The canvas lost the keyboard focus (another window, a dialog): the phrase's keys do not act here until it is back. */
    private boolean keyboardFocusLost;
    private Tool tool = defaultTool;
    private GridStyle gridStyle = GridStyle.DOTS;
    private boolean paintBusValues = true;
    private int gridLineSpacing = AppPreferences.DEFAULT_GRID_LINE_SPACING;
    private Snapper snapper = Snapper.none();
    private SimulationSnapshot displayedSnapshot = SimulationSnapshot.EMPTY;
    private boolean animationSuspended;
    private BooleanSupplier onScreen = this::isOnScreen;
    private final WindowStateListener windowStateListener = event -> updateAnimation();
    private final WindowAdapter windowDeactivationListener = new WindowAdapter() {
        @Override
        public void windowDeactivated(WindowEvent event) {
            autoscroll.stopAutoscroll(); // the release may never reach this canvas
            tool.focusLost(CircuitCanvas.this);
            host.canvasFocusLost(CircuitCanvas.this, Optional.empty());
            repaint(); // the terminals turn still
        }

        @Override
        public void windowActivated(WindowEvent event) {
            repaint(); // the terminals come back to life; the paint restarts their clock
        }
    };
    private Window watchedWindow;
    private boolean spaceDown;
    /** The view was panned while Espacio was held: its release is no tap (decided point 2 of 2026-11-08). */
    private boolean pannedWithSpace;
    private Point panAnchor;
    private GridPoint mousePoint;
    /** The pointer is over an edge handle and the tool was already told it left. */
    private boolean toolSeesPointerGone;
    private Cursor toolCursor = Cursor.getDefaultCursor();
    private boolean disposed;
    /** {@link #tunnelColors()} as last worked out, and the document and revision it was worked out for. */
    private Map<String, Integer> tunnelColorsCache = Map.of();
    private CircuitDocument tunnelColorsDocument;
    private long tunnelColorsRevision = -1;
    private final CanvasViewTravel viewTravel = new CanvasViewTravel(this, viewport, viewTakenListeners);

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
        openDocument.addRefreshListener(refreshListener);
        selection.addListener(selectionListener);
        selectionMoves.preferences().addListener(preferenceListener);
        viewport.addChangeListener(this::viewMovedUnderThePointer);
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        KeyHandler keyHandler = new KeyHandler();
        addKeyListener(keyHandler);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                keyboardFocusLost = false;
                publishGesturePhrase();
            }

            @Override
            public void focusLost(FocusEvent event) {
                // the focus going to a sibling pane of this window keeps the keys (and the phrase) of a path under way
                keyboardFocusLost = !(event.getOppositeComponent() instanceof CircuitCanvas sibling
                        && otherPanes.get().contains(sibling));
                publishGesturePhrase();
                keyHandler.forgetModifiers();
                spaceDown = false;
                panAnchor = null;
                pressForwardedTo = null; // the release of a press given to another pane's path may never come
                showCursor();
                autoscroll.stopAutoscroll(); // a modal dialog or another window took the release
                tool.focusLost(CircuitCanvas.this);
                host.canvasFocusLost(CircuitCanvas.this, Optional.ofNullable(event.getOppositeComponent()));
            }
        });
        setTransferHandler(new CanvasTransferHandler(this));
        canvasDragOut.listenForDragExit();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                showingChanged(isShowing());
                updateAnimation();
            }
        });
        frameTimer = new Timer(FRAME_MILLISECONDS, event -> refreshSnapshot());
        antsTimer = new Timer(ANTS_MILLISECONDS, event -> animationTick());
        problemPulseTimer = new Timer(ProblemPulse.TICK_MILLISECONDS, event -> problemPulseTick());
        renderer.useMotionGate(() -> animations.getAsBoolean() && windowActive());
        viewTravel.listenForUserMoves();
    }

    /**
     * Places the palette entry encoded in {@code transferText} (a type, or a preset such as NAND with its parameters)
     * at {@code screenPoint}; false when it is not one.
     */
    public boolean dropComponent(String transferText, Point screenPoint) {
        Optional<PaletteEntry> entry = PaletteTransfer.decode(transferText)
                .flatMap(id -> PaletteEntry.forId(id, openDocument.registry()));
        if (entry.isEmpty()) {
            return false;
        }
        ComponentType type = entry.get().type();
        Map<String, Object> presetParameters = entry.get().parameters();
        GridPoint raw = viewport.snap(screenPoint.x, screenPoint.y);
        ComponentPlacement.place(this, type, presetParameters,
                ComponentPlacement.snapped(this, type, presetParameters, raw, Rotation.R0).position(), Rotation.R0);
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
        return dropFragment(fragmentJson, origin, screenPoint, copyRequested, Optional.empty());
    }

    /**
     * Like {@link #dropFragment(String, Optional, Point, boolean)}; {@code paletteTypeId} is the type a palette drag
     * came from (a user component): it counts as placed in the palette's world once it lands (the click that fixes
     * it, or at once with Ctrl).
     */
    boolean dropFragment(String fragmentJson, Optional<DragOrigin> origin, Point screenPoint, boolean copyRequested,
                         Optional<String> paletteTypeId) {
        GridPoint at = viewport.snap(screenPoint.x, screenPoint.y);
        Optional<DragOrigin> source = origin.or(canvasDragOut::dragOrigin);
        if (source.isPresent() && !copyRequested && source.get().canvas().openDocument() == openDocument) {
            moveHere(source.get(), at);
            return true;
        }
        Optional<CircuitFragment> fragment = readFragment(fragmentJson);
        if (fragment.isEmpty()) {
            return false;
        }
        source.ifPresent(drag -> drag.canvas().endDragOut());
        Optional<ComponentType> paletteType = paletteTypeId
                .flatMap(typeId -> fragment.get().registryFor(openDocument.registry()).find(typeId));
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
            paletteType.map(ComponentType::id).ifPresent(this::componentPlaced);
        } else {
            setTool(new FloatingSelectionTool(fragment.get(), at, paletteType));
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

    /** Where files dropped on the canvas go, with the grid point they were dropped on (a memory file lands there). */
    public interface FileDropTarget {
        void drop(List<Path> files, GridPoint gridPosition);
    }

    /** Where dropped files go, with their drop point (pasted files: the middle of the canvas). */
    public void setFileDropTarget(FileDropTarget target) {
        fileDropTarget = Objects.requireNonNull(target, "target");
    }

    /** Where dropped circuit files go (the window opens them), when the drop point does not matter. */
    public void setFileDropHandler(Consumer<List<Path>> handler) {
        Objects.requireNonNull(handler, "handler");
        setFileDropTarget((files, gridPosition) -> handler.accept(files));
    }

    /** The files dropped at {@code screenPoint} (canvas pixels) go to the drop target with its grid point. */
    Consumer<List<Path>> fileDropHandler(Point screenPoint) {
        GridPoint gridPosition = viewport.snap(screenPoint.x, screenPoint.y);
        return files -> fileDropTarget.drop(files, gridPosition);
    }

    /**
     * Switches a move of the selection in progress into a drag out of the canvas; false, leaving the move alone, when
     * nothing is moving or the selection gives no fragment to transfer.
     */
    boolean beginDragOut() {
        return canvasDragOut.beginDragOut();
    }

    void endDragOut() {
        canvasDragOut.endDragOut();
    }

    Optional<DragOrigin> dragOrigin() {
        return canvasDragOut.dragOrigin();
    }

    /** What a drag out carries: the fragment taken when it began, or the current selection otherwise. */
    Optional<String> transferFragmentJson() {
        return canvasDragOut.transferFragmentJson();
    }

    boolean isDraggingOut() {
        return canvasDragOut.isDraggingOut();
    }

    /** The drag out is over this canvas again at {@code point} (canvas pixels): preview the move it would make. */
    void dragOutOver(Point point) {
        canvasDragOut.dragOutOver(point);
    }

    /** The drag out left this canvas (or ended): no preview. */
    void dragOutLeft() {
        canvasDragOut.dragOutLeft();
    }

    /**
     * The new contacts of the move being previewed (spec §3.7), with the wires following as the drop will move them;
     * none when no move is previewed.
     */
    List<NewContact> dragOutContacts() {
        return canvasDragOut.dragOutContacts();
    }

    /** The move being previewed, as a delta in grid units. */
    Optional<GridPoint> dragOutPreview() {
        return canvasDragOut.dragOutPreview();
    }

    /**
     * Tells this canvas which other panes share its window (split views; the pane area sets it): a gesture pressed
     * here goes on over them (the tool gets the pointer's grid point from the pane under it, the release commits once)
     * and each of them paints the gesture's preview. The returned runnable puts back none.
     */
    public Runnable useOtherPanes(Supplier<List<CircuitCanvas>> panes) {
        otherPanes = Objects.requireNonNull(panes, "panes");
        return () -> {
            if (otherPanes == panes) {
                otherPanes = List::of;
            }
        };
    }

    /**
     * What the tool's gesture works with now: this canvas, or while the pointer is over another pane of the window,
     * that pane's view and snapping (with this canvas's cursor and dialogs). A snap refresh passes it to the tool.
     */
    public ToolContext toolContext() {
        return pointerPaneContext != null ? pointerPaneContext : this;
    }

    /**
     * The guides the gestures shown on this canvas lie on (see {@link Tool#snappedGuides}): this canvas's tool's and
     * those of a gesture of another pane of the window that crosses over here.
     */
    public List<Guide> snappedGuides() {
        List<Guide> own = tool.snappedGuides();
        List<CircuitCanvas> sharing = panesSharingAGesture();
        if (sharing.isEmpty()) {
            return own;
        }
        List<Guide> guides = new ArrayList<>(own);
        for (CircuitCanvas pane : sharing) {
            pane.tool.snappedGuides().stream().filter(guide -> !guides.contains(guide)).forEach(guides::add);
        }
        return List.copyOf(guides);
    }

    /**
     * The elements the moves shown on this canvas drag, where their previews show them (see
     * {@link Tool#movingElements}): this canvas's tool's and those of a gesture of another pane of the window that
     * crosses over here. Read-only; empty when nothing is being moved.
     */
    public List<DocumentElement> movingElements() {
        List<DocumentElement> own = tool.movingElements(this);
        List<CircuitCanvas> sharing = panesSharingAGesture();
        if (sharing.isEmpty()) {
            return own;
        }
        List<DocumentElement> moving = new ArrayList<>(own);
        for (CircuitCanvas pane : sharing) {
            moving.addAll(pane.tool.movingElements(new CrossPaneContext(pane, this)));
        }
        return List.copyOf(moving);
    }

    /**
     * True while this canvas's tool holds a gesture that the other panes of the window show: one whose button is down,
     * or one drawn between clicks (a continuous wire's path).
     */
    private boolean sharesGesture() {
        return (gestureUnderWay || tool.drawsBetweenClicks()) && tool.crossesPanes();
    }

    /**
     * The pane of this window whose tool draws between clicks (see {@link Tool#drawsBetweenClicks}): this one, or
     * another pane, which then gets the pointer, the clicks and the keys given to this one.
     */
    private Optional<CircuitCanvas> drawingPane() {
        if (tool.drawsBetweenClicks()) {
            return Optional.of(this);
        }
        return otherPanes.get().stream().filter(pane -> pane != this && pane.tool.drawsBetweenClicks()).findFirst();
    }

    /** {@link #drawingPane()} when it is another pane of the window. */
    private Optional<CircuitCanvas> otherDrawingPane() {
        return drawingPane().filter(pane -> pane != this);
    }

    /** The pane whose tool takes the keys given to this canvas: the one drawing between clicks, or this one. */
    private CircuitCanvas keyPane() {
        return otherDrawingPane().orElse(this);
    }

    /** True while a tool of this window draws between clicks (in this pane or another): Deshacer drops its last step. */
    public boolean drawsBetweenClicks() {
        return drawingPane().isPresent();
    }

    /**
     * Runs {@code listener} whenever this canvas's tool starts or stops drawing between clicks (a continuous wire's
     * path begins or ends). Returns its removal.
     */
    public Runnable addDrawingListener(Runnable listener) {
        drawingListeners.add(listener);
        return () -> drawingListeners.remove(listener);
    }

    /**
     * Deshacer while a tool of this window draws between clicks: that tool drops its last step (a continuous wire's last
     * corner, or the path when only its start is left) instead of the history undoing an earlier change; true when it
     * did, false when nothing is drawn (the history undoes as usual).
     */
    public boolean dropLastToolStep() {
        Optional<CircuitCanvas> drawer = drawingPane();
        if (drawer.isEmpty()) {
            return false;
        }
        CircuitCanvas pane = drawer.get();
        boolean dropped = pane.tool.dropLastStep(pane.toolContext());
        pane.repaint();
        pane.repaintOtherPanes();
        pane.updateAnimation();
        return dropped;
    }

    /**
     * The user was taken elsewhere (the tab changed, the AI started a task on the document): this canvas's tool cancels
     * what must not survive it, as Escape would, and the notice that says so («Cable cancelado») is returned for the
     * caller to show where the user looks. Empty when nothing was cancelled.
     */
    public Optional<String> interruptGesture() {
        boolean shared = sharesGesture();
        Optional<String> notice = tool.interrupt(toolContext());
        repaintOtherPanesIfTheGestureEnded(shared);
        updateAnimation();
        return notice;
    }

    /**
     * The view moved under a pointer that stayed still (wheel, zoom, the middle button, an autoscroll step): a tool
     * drawing between clicks redoes its preview at the grid point now under the pointer, so the path is kept and
     * follows it.
     */
    private void viewMovedUnderThePointer() {
        if (pointerHoversWhileDrawing()) {
            pointerMovedTo(pointerEvent);
        }
    }

    /** The pointer is at {@code event} with no button held: the tool drawing between clicks, or this one, sees it. */
    private void pointerMovedTo(MouseEvent event) {
        CanvasMouseEvent converted = convert(event);
        Optional<CircuitCanvas> drawer = otherDrawingPane();
        if (drawer.isPresent()) {
            drawer.get().tool.mouseMoved(new CrossPaneContext(drawer.get(), this), converted);
            drawer.get().repaint();
            drawer.get().repaintOtherPanes();
            return;
        }
        tool.mouseMoved(this, converted);
        if (sharesGesture()) {
            repaintOtherPanes();
        }
    }

    /**
     * True while the pointer hovers over this canvas, no button down, with a tool of this window drawing between
     * clicks: the view moving under the pointer redoes the preview, and the edge scrolls the view.
     */
    boolean pointerHoversWhileDrawing() {
        return pointerEvent != null && !gestureUnderWay && pressForwardedTo == null && drawingPane().isPresent();
    }

    private List<CircuitCanvas> panesSharingAGesture() {
        List<CircuitCanvas> panes = otherPanes.get();
        if (panes.isEmpty()) {
            return List.of();
        }
        return panes.stream().filter(pane -> pane != this && pane.sharesGesture()).toList();
    }

    /** The other pane of the window under {@code point} (this canvas's coordinates), when the tool's gesture crosses. */
    private Optional<CircuitCanvas> otherPaneUnder(Point point) {
        if (!gestureUnderWay || !tool.crossesPanes() || contains(point)) {
            return Optional.empty();
        }
        for (CircuitCanvas pane : otherPanes.get()) {
            if (pane != this && pane.contains(SwingUtilities.convertPoint(this, point, pane))) {
                return Optional.of(pane);
            }
        }
        return Optional.empty();
    }

    /** {@code event} (this canvas's coordinates) as {@code pane} sees it: its pixels, its grid point. */
    private CanvasMouseEvent convertFor(CircuitCanvas pane, MouseEvent event) {
        Point there = SwingUtilities.convertPoint(this, event.getPoint(), pane);
        return pane.convert(event, there.x, there.y);
    }

    private void repaintOtherPanes() {
        for (CircuitCanvas pane : otherPanes.get()) {
            if (pane != this) {
                pane.repaint();
            }
        }
    }

    /** The gesture under way ends (released, or the tool changed): the other panes stop showing it. */
    private void endGesture() {
        boolean shared = sharesGesture();
        gestureUnderWay = false;
        pointerPaneContext = null;
        if (shared) {
            repaintOtherPanes();
        }
    }

    /** True while a move or a rubber band is held at the edge and the view scrolls under it. */
    boolean isAutoscrolling() {
        return autoscroll.isAutoscrolling();
    }

    /** Tests call {@link #autoscrollTick()} themselves instead of the Swing timer, so positions are exact. */
    void useManualAutoscrollClock() {
        autoscroll.useManualAutoscrollClock();
    }

    /** Replaces the "this point (canvas coordinates) is over another window" check, for headless tests. */
    void overrideWindowProbe(Predicate<Point> check) {
        canvasDragOut.overrideWindowProbe(check);
    }

    /** One autoscroll step: scrolls the view and replays the last drag, so the move or the band follows the pointer. */
    void autoscrollTick() {
        autoscroll.autoscrollTick();
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

    /**
     * The canvas was shown or hidden (a tab's card that is not the shown one is hidden yet stays added): hidden, it
     * stops its frame timer, which would remap the snapshot and call its listeners sixty times a second for nothing;
     * shown, it starts again and catches up with the latest snapshot at once.
     */
    void showingChanged(boolean showing) {
        if (disposed || !isDisplayable()) {
            return;
        }
        if (showing && !frameTimer.isRunning()) {
            frameTimer.start();
            refreshSnapshot();
        } else if (!showing) {
            frameTimer.stop();
        }
    }

    /** Whether the frame timer runs (a hidden tab's does not). */
    boolean framesRunning() {
        return frameTimer.isRunning();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Window window = SwingUtilities.getWindowAncestor(this);
        // added hidden to a window already on screen (a tab's card): the showing event starts it when it is shown
        if (isShowing() || window == null || !window.isShowing()) {
            frameTimer.start();
        }
        animationSuspended = false;
        watchWindow(SwingUtilities.getWindowAncestor(this));
        updateAnimation();
    }

    @Override
    public void removeNotify() {
        autoscroll.stopAutoscroll();
        frameTimer.stop();
        glowTicker.stop();
        animationSuspended = true;
        antsTimer.stop();
        problemPulseTimer.stop();
        terminalTicker.stop();
        watchWindow(null);
        viewTravel.finish();
        super.removeNotify();
    }

    public void dispose() {
        disposed = true;
        edgeController.cancel(); // the pane closes: a guide being pulled goes with it
        viewTravel.stop();
        autoscroll.stopAutoscroll();
        frameTimer.stop();
        glowTicker.stop();
        animationSuspended = true;
        antsTimer.stop();
        problemPulseTimer.stop();
        terminalTicker.stop();
        watchWindow(null);
        openDocument.document().removeListener(documentListener);
        openDocument.removeRefreshListener(refreshListener);
        selection.removeListener(selectionListener);
        selectionMoves.preferences().removeListener(preferenceListener);
    }

    /**
     * A component tab's body followed a change made elsewhere (an undo from the circuit's tab, «Actualizar desde la
     * librería», the AI; spec §10.4): the gesture in progress is cancelled as Escape would, since what it holds may be
     * gone, and the selection keeps the ids that still exist.
     */
    private void bodyRefreshed() {
        if (disposed) {
            return;
        }
        cancelEdgeGesture();
        interruptGesture().ifPresent(this::showNotice);
        if (tool.holdsGesture()) {
            boolean shared = sharesGesture();
            tool.keyPressed(toolContext(), KeyEvent.VK_ESCAPE, false);
            endGesture();
            repaintOtherPanesIfTheGestureEnded(shared);
            updateAnimation();
        }
        Set<ElementId> present = new HashSet<>();
        openDocument.document().elements().forEach(element -> present.add(element.id()));
        selection.retainAll(present);
        repaint();
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

    /**
     * Runs {@code listener} with the palette id (a type id or a preset's, {@code nand}) of every component placed from
     * the palette's world on this canvas (see {@link ToolContext#componentPlaced}). Returns its removal.
     */
    public Runnable addPlacementListener(Consumer<String> listener) {
        placementListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> placementListeners.remove(listener);
    }

    @Override
    public void componentPlaced(String paletteId) {
        placementListeners.forEach(listener -> listener.accept(paletteId));
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

    /** Back to 100 % about the middle of the canvas, travelling there like every {@link #zoomTo}. */
    public void resetZoom() {
        zoomTo(ZoomTargets.atZoom(viewState(), 1.0));
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
        travelTo(bounds, new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
    }

    /**
     * As {@link #travelTo(Bounds)}, but centring {@code bounds} in {@code within} (pane pixels: the part of the pane
     * left free by something floating over it) and zooming out only as far as needed for them to fit there.
     */
    public void travelTo(Bounds bounds, Rectangle2D within) {
        viewTravel.travelTo(bounds, within);
    }

    /**
     * Takes the view to {@code target} (its zoom clamped to the viewport limits): a zoom the user asked for (100 %, the
     * zoom tool's rectangle, «Ver todo el circuito»…), so it becomes the user's zoom at once, where later
     * {@link #travelTo}s rest, and the view-taken listeners hear it once. With view moves animated and the canvas on
     * screen it travels there, keeping one point of the pane still when the zoom changes nearby (no sideways slide);
     * otherwise it jumps. A pan or zoom by hand during the travel stops it where it is; another target replaces it.
     */
    @Override
    public void zoomTo(ViewState target) {
        viewTravel.zoomTo(target);
    }

    /**
     * Zooms to {@code zoom} (clamped) about the middle of the canvas at once, as the user's zoom: for a control that
     * must follow the hand (the status bar's slider). Stops a travel in progress.
     */
    public void zoomAroundCenter(double zoom) {
        viewport.zoomAt(getWidth() / 2.0, getHeight() / 2.0, zoom);
        viewChanged();
    }

    /**
     * Zooms in or out until {@code bounds} (grid units) fill nine tenths of the pane (zoom spec §5: «Ver todo el
     * circuito», «Zoom a la selección»), centred on them, through {@link #zoomTo}: up to 400 %, unlike
     * {@link #travelTo}, which never zooms in.
     */
    public void zoomToFit(Bounds bounds) {
        viewTravel.zoomToFit(bounds);
    }

    /** An instant zoom around a point of this pane (the zoom tool's click): the status bar follows. */
    @Override
    public void zoomAt(Point2D screenPoint, double zoom) {
        viewport.zoomAt(screenPoint.getX(), screenPoint.getY(), zoom);
        viewChanged();
    }

    /** The canvas's size in pixels: the pane a tool zooms. */
    @Override
    public Dimension viewSize() {
        return getSize();
    }

    /** True once {@link #dispose()} ran: a move of the view no longer travels. */
    boolean isDisposed() {
        return disposed;
    }

    /** True when the whole of {@code bounds} (grid units) is on screen now; false before the canvas has a size. */
    public boolean shows(Bounds bounds) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        Point2D corner = viewport.screenToGrid(0, 0);
        Point2D end = viewport.screenToGrid(getWidth(), getHeight());
        return bounds.x() >= corner.getX() && bounds.y() >= corner.getY()
                && bounds.maximumX() <= end.getX() && bounds.maximumY() <= end.getY();
    }

    /**
     * Pans the view by ({@code deltaX}, {@code deltaY}) pixels because the canvas itself moved in its window (a header
     * above it showed or went), so the circuit stays where it was on screen. It is not a user move: a travel in
     * progress goes on (its frames derive the view from the centre and the current size).
     */
    public void compensateCanvasMove(double deltaX, double deltaY) {
        viewTravel.compensateCanvasMove(deltaX, deltaY);
    }

    /** Whether {@link #travelTo} animates; read at each call and each frame (the preference can change mid-flight). */
    public void setAnimateViewMoves(BooleanSupplier animate) {
        viewTravel.setAnimateViewMoves(animate);
    }

    /** True while the view travels to a target. */
    public boolean isTravelling() {
        return viewTravel.isTravelling();
    }

    /** Drives the travels with {@code ticker} and {@code clock} (milliseconds) instead of a Swing timer: for tests. */
    public void useViewTicker(FrameTicker ticker, LongSupplier clock) {
        viewTravel.useViewTicker(ticker, clock);
    }

    /**
     * «Ver la pieza»: makes the piece at {@code bounds} glow for 1.5 s so the eye finds it after the view travelled
     * (spec §5.1). A new glow replaces the one going. With «Animaciones: No» it is steady and goes out at once.
     */
    public void glow(Bounds bounds) {
        pieceGlow.start(Objects.requireNonNull(bounds, "bounds"), glowClock.getAsLong(), !animations.getAsBoolean());
        glowTicker.start(this::glowFrame);
        repaint();
    }

    /** True while a piece glows. */
    public boolean isGlowing() {
        return pieceGlowShows(glowClock.getAsLong());
    }

    /** The glow's strength now, 1 down to 0. */
    double glowStrength() {
        long now = glowClock.getAsLong();
        return pieceGlowShows(now) ? pieceGlow.strengthAt(now) : 0;
    }

    /** Whether the piece glow shows at {@code nowMillis}: a fade cut short by «Animaciones: No» ends at once. */
    private boolean pieceGlowShows(long nowMillis) {
        return pieceGlow.activeAt(nowMillis) && (pieceGlow.steady() || animations.getAsBoolean());
    }

    /** Drives the glow with {@code ticker} and {@code clock} (milliseconds) instead of a Swing timer: for tests. */
    void useGlowTicker(FrameTicker ticker, LongSupplier clock) {
        glowTicker.stop();
        glowTicker = Objects.requireNonNull(ticker, "ticker");
        glowClock = Objects.requireNonNull(clock, "clock");
    }

    /** One frame of the piece glow: repaints while it fades and when it ends (a steady glow looks the same). */
    private void glowFrame() {
        if (!pieceGlowShows(glowClock.getAsLong())) {
            glowTicker.stop();
            pieceGlow.stop();
            repaint();
        } else if (!pieceGlow.steady()) {
            repaint();
        }
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
     * Paints {@code overlay} over the whole pane in logical pixels, after the edge bars and the empty-canvas hint (the
     * accessibility lens); the returned runnable takes it back.
     */
    public Runnable addScreenOverlay(ScreenOverlay overlay) {
        screenOverlays.add(Objects.requireNonNull(overlay, "overlay"));
        repaint();
        return () -> {
            if (screenOverlays.remove(overlay)) {
                repaint();
            }
        };
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

    /**
     * Runs {@code listener} when the view moves by anything but a travel ({@link #travelTo}) or a layout
     * compensation: the user panned or zoomed, or a command centred it. Returns its removal.
     */
    public Runnable addViewTakenListener(Runnable listener) {
        viewTakenListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> viewTakenListeners.remove(listener);
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
     * Adds a claim on Esc: while {@code claim} holds, something shown over this pane closes on Esc itself (the
     * «Mirar adentro» magnifier), so presentation mode leaves it the key. The returned runnable removes it.
     */
    public Runnable addEscapeClaim(BooleanSupplier claim) {
        Objects.requireNonNull(claim, "claim");
        escapeClaims.add(claim);
        return () -> escapeClaims.remove(claim);
    }

    /** Whether something shown over this pane wants the next Esc ({@link #addEscapeClaim}). */
    public boolean isEscapeClaimed() {
        return escapeClaims.stream().anyMatch(BooleanSupplier::getAsBoolean);
    }

    /**
     * For tests (every extension in every pane): how many press interceptors, tooltip overrides and incoming-fragment
     * listeners this canvas holds, and whether a tooltip provider is set.
     */
    public PaneHooks paneHooks() {
        int delegates = defaultTool instanceof SelectTool select ? select.delegates().size() : 0;
        return new PaneHooks(pressInterceptors.size(), toolTipOverrides.size(), incomingFragmentListeners.size(),
                toolTipProviderSet, placementListeners.size(), delegates);
    }

    /** What {@link #paneHooks()} counts. */
    public record PaneHooks(int pressInterceptors, int toolTipOverrides, int incomingFragmentListeners,
                            boolean toolTipProvider, int placementListeners, int selectToolDelegates) {
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

    /** For tests: features must remove the screen overlays they add. */
    public int screenOverlayCount() {
        return screenOverlays.size();
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

    /** The phase the glow under problem wires is painted at: the pulse's, or steady at its brightest with no animations. */
    double problemGlowPhase() {
        return animations.getAsBoolean() ? problemPulse.phase() : ProblemPulse.HIGH_PHASE;
    }

    double problemPulsePhase() {
        return problemPulse.phase();
    }

    /** For tests: the Terminals' animation runs on {@code ticker} (a hand-driven one) instead of a Swing timer. */
    void useTerminalTicker(FrameTicker ticker) {
        terminalTicker.stop();
        terminalTicker = Objects.requireNonNull(ticker, "ticker");
        updateTerminalAnimation();
    }

    /** True while the Terminals' clock runs: on screen with a moving terminal in view. */
    boolean isAnimatingTerminals() {
        return terminalTicker.isRunning();
    }

    /**
     * Where to ask whether VSim animates ({@code Animations.enabled}, read at every paint); without animations the
     * Terminals show the cursor on, Automan's star still, a Matriz LED goes off at once and a piece's glow does not
     * fade, and their clock does not run.
     */
    public void setAnimations(BooleanSupplier animations) {
        this.animations = Objects.requireNonNull(animations, "animations");
        repaint();
    }

    /**
     * Preferencias ▸ «Animaciones» changed: starts or stops the clocks that depend on it (the selection's marching
     * ants, the Terminals' cursor) and repaints.
     */
    public void animationsChanged() {
        updateAnimation();
        repaint();
    }

    /** Replaces the on-screen check (showing and not minimized), so headless tests can show or hide the canvas. */
    void overrideOnScreen(BooleanSupplier check) {
        onScreen = check;
        updateAnimation();
    }

    /** The on-screen check now (see {@link #overrideOnScreen}): a zoom travels only on screen. */
    boolean onScreen() {
        return onScreen.getAsBoolean();
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
        boolean needed = !animationSuspended && onScreen.getAsBoolean() && animations.getAsBoolean()
                && problemWireShown();
        if (needed && !problemPulseTimer.isRunning()) {
            problemPulseTimer.start();
        } else if (!needed && problemPulseTimer.isRunning()) {
            problemPulseTimer.stop();
        }
    }

    /** Whether this canvas's window has the focus (none: a canvas outside a window, as in tests, counts as active). */
    private boolean windowActive() {
        return watchedWindow == null || watchedWindow.isActive();
    }

    /** Runs the Terminals' clock while this canvas is on screen and a moving terminal is in view, and only then. */
    private void updateTerminalAnimation() {
        boolean needed = !animationSuspended && onScreen.getAsBoolean() && !movingTerminalsInView().isEmpty();
        if (needed && !terminalTicker.isRunning()) {
            terminalTicker.start(this::terminalTick);
        } else if (!needed && terminalTicker.isRunning()) {
            terminalTicker.stop();
        }
    }

    /** One tick of the Terminals' clock: repaints the moving areas whose moment is over, or stops when none is left. */
    void terminalTick() {
        updateTerminalAnimation();
        if (!terminalTicker.isRunning()) {
            return;
        }
        long now = renderer.terminalNow();
        AffineTransform documentToScreen = viewport.documentTransform();
        for (CircuitRenderer.TerminalMotion motion : movingTerminalsInView()) {
            if (now - motion.nextChangeNanos() >= 0) {
                Rectangle area = documentToScreen.createTransformedShape(motion.documentArea()).getBounds();
                area.grow(TERMINAL_REPAINT_MARGIN, TERMINAL_REPAINT_MARGIN);
                repaint(area);
            }
        }
    }

    private List<CircuitRenderer.TerminalMotion> movingTerminalsInView() {
        List<CircuitRenderer.TerminalMotion> motions = renderer.terminalMotions();
        if (motions.isEmpty()) {
            return motions;
        }
        Rectangle2D view = visibleDocumentArea();
        return motions.stream().filter(motion -> motion.documentArea().intersects(view)).toList();
    }

    /** The part of the document this canvas shows, in document pixels. */
    private Rectangle2D visibleDocumentArea() {
        try {
            return viewport.documentTransform().createInverse()
                    .createTransformedShape(new Rectangle2D.Double(0, 0, getWidth(), getHeight())).getBounds2D();
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException("the viewport has a degenerate transform", exception);
        }
    }

    private void updateAnimation() {
        updateProblemPulse();
        updateTerminalAnimation();
        // with the animations off the selection's outline stays, dashed and still: the ants do not march
        boolean needed = !animationSuspended && onScreen.getAsBoolean() && animations.getAsBoolean()
                && (!selection.isEmpty() || tool.animatesOutline());
        if (needed && !antsTimer.isRunning()) {
            antsTimer.start();
        } else if (!needed && antsTimer.isRunning()) {
            antsTimer.stop();
        }
        tellDrawingListeners();
        publishGesturePhrase();
    }

    /**
     * What the keys and the next click do now (see {@link Tool#gesturePhrase}); empty while this canvas has lost the
     * keyboard focus, as the keys would not reach it.
     */
    public String gesturePhrase() {
        if (keyboardFocusLost) {
            return "";
        }
        CircuitCanvas keys = keyPane(); // a path under way in another pane takes this pane's keys, so it speaks
        return keys.tool.gesturePhrase(keys);
    }

    private void publishGesturePhrase() {
        String phrase = gesturePhrase();
        if (!phrase.equals(gesturePhraseTold)) {
            gesturePhraseTold = phrase;
            host.gesturePhraseChanged(this, phrase);
        }
    }

    private void tellDrawingListeners() {
        boolean drawing = tool.drawsBetweenClicks();
        if (drawing != drawingTold) {
            drawingTold = drawing;
            drawingListeners.forEach(Runnable::run);
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

    /** Whether the value is written above each bus (Ver ▸ «Valores de los buses»); yes until the extension says. */
    public boolean paintBusValues() {
        return paintBusValues;
    }

    public void setPaintBusValues(boolean paint) {
        if (paint != paintBusValues) {
            paintBusValues = paint;
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
        if (next == tool && tool.drawsBetweenClicks()) {
            return; // choosing again the tool that is drawing (Selección, while a wire is drawn) keeps its path
        }
        cancelEdgeGesture();
        endGesture();
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
    public Optional<javax.swing.KeyStroke> shortcut(String id) {
        return host.shortcut(id);
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
        return keyPane().giveKeyToTool(keyCode, shiftDown);
    }

    /** {@code stroke} goes to this canvas's tool (the user's shortcuts it reacts to); true when it used it. */
    private boolean giveStrokeToTool(javax.swing.KeyStroke stroke) {
        boolean used = tool.keyStrokePressed(toolContext(), stroke);
        updateAnimation();
        return used;
    }

    /** {@code keyCode} goes to this canvas's tool; the other panes stop showing a gesture the key ended. */
    private boolean giveKeyToTool(int keyCode, boolean shiftDown) {
        boolean shared = sharesGesture();
        boolean used = tool.keyPressed(toolContext(), keyCode, shiftDown);
        repaintOtherPanesIfTheGestureEnded(shared);
        updateAnimation();
        return used;
    }

    /** A key (Escape) cancelled the gesture the other panes showed: they stop showing it. */
    private void repaintOtherPanesIfTheGestureEnded(boolean sharedBefore) {
        if (sharedBefore && !sharesGesture()) {
            repaintOtherPanes();
        }
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
     * The AWT cursor of {@code cursor} (AWT keeps one instance of each predefined cursor; the selection and zoom cursors
     * are drawn once, lazily).
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
            case ZOOM_IN -> ZoomCursors.zoomIn();
            case ZOOM_OUT -> ZoomCursors.zoomOut();
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
    public void addTextAt(GridPoint point) {
        host.addTextAt(point);
    }

    @Override
    public void editValue(Component component) {
        host.editValue(component);
    }

    @Override
    public void editValue(Component component, Value startingValue) {
        host.editValue(component, startingValue);
    }

    @Override
    public void editMemory(Component component) {
        host.editMemory(component);
    }

    @Override
    public void editTerminal(Component component) {
        host.editTerminal(component);
    }

    @Override
    public void enterComponent(Component component) {
        host.enterComponent(component);
    }

    @Override
    public void showNotice(String text) {
        host.showNotice(text);
    }

    @Override
    public void captureKeyboard(Component component) {
        host.captureKeyboard(component);
    }

    @Override
    public boolean memoryFoldToggleAt(Component component, Point2D gridPosition) {
        return host.memoryFoldToggleAt(component, gridPosition);
    }

    // Painting and snapshots

    @Override
    protected void paintComponent(Graphics graphics) {
        paintScene(graphics, viewport);
        Graphics2D screen = (Graphics2D) graphics.create(); // logical pixels: the device transform only
        try {
            edgeController.paint(screen);
            paintEmptyHint(screen);
            for (ScreenOverlay overlay : screenOverlays) {
                overlay.paint(screen, this);
            }
        } finally {
            screen.dispose();
        }
        updateTerminalAnimation();
    }

    /**
     * The scene of this pane (background, grid, circuit, overlays, glows and the tools' previews, from the frame's
     * state) enlarged {@code factor} times around {@code centre} (logical pixels of this pane), on {@code screen}: the
     * accessibility lens. The grid point under {@code centre} stays there, and the zoom is not clamped to
     * {@link Viewport#MAXIMUM_ZOOM}. The edge bars, the empty-canvas hint and the screen overlays are not part of it;
     * the caller clips {@code screen} to the lens.
     */
    public void paintSceneMagnified(Graphics2D screen, Point2D centre, double factor) {
        paintScene(screen, viewport.magnifiedAround(centre, factor));
    }

    /** The scene seen through {@code shown}: this pane's viewport, or the lens's enlarged copy of it. */
    private void paintScene(Graphics graphics, Viewport shown) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            renderer.paintBackground(local, shown, getWidth(), getHeight(), gridStyle, gridLineSpacing);
            local.transform(shown.documentTransform());
            List<CircuitCanvas> sharing = panesSharingAGesture();
            RenderState state = new RenderState(openDocument.document(), openDocument.netlist(),
                    openDocument.netlistToken(), displayedSnapshot, selection.ids(), marchingAnts.phase(),
                    problemGlowPhase(), hiddenWires(sharing), PaintPolicy.CANVAS.withBusValues(paintBusValues),
                    host.capturedPart(), previewedParts(sharing), openDocument.pieceMemoryContents());
            renderer.paint(local, state);
            for (CanvasOverlay overlay : overlays) {
                overlay.paint(local, state);
            }
            long now = glowClock.getAsLong();
            if (pieceGlowShows(now)) {
                pieceGlow.paint(local, renderer.scheme(), now);
            }
            canvasDragOut.paintDragOutPreview(local);
            tool.paintOverlay(local, this);
            for (CircuitCanvas owner : sharing) {
                owner.tool.paintOverlay(local, new CrossPaneContext(owner, this));
            }
        } finally {
            local.dispose();
        }
    }

    /**
     * Where the empty-canvas hint asks for the key of «Buscar una acción…» («Ctrl+K», already described), read at
     * every paint so a rebinding shows at once; empty (its menu is named instead) until the command palette sets it.
     */
    public void setFindActionKey(Supplier<Optional<String>> key) {
        findActionKey = Objects.requireNonNull(key, "key");
        repaint();
    }

    /** The lines the empty-canvas hint shows now (whether or not the circuit is empty). */
    public List<String> emptyHintLines() {
        return EmptyCanvasHint.lines(findActionKey.get());
    }

    /** The welcome text of a canvas with nothing in it, centred in this pane; screen only, never in an export. */
    private void paintEmptyHint(Graphics2D screen) {
        if (EmptyCanvasHint.isShownFor(openDocument.document())) {
            EmptyCanvasHint.paint(screen, getWidth(), getHeight(), emptyHintLines());
        }
    }

    /** The wires the tools leave out of this frame: this canvas's tool's and those of the gestures crossing here. */
    private Set<ElementId> hiddenWires(List<CircuitCanvas> sharing) {
        Set<ElementId> own = tool.hiddenWires(this);
        if (sharing.isEmpty()) {
            return own;
        }
        Set<ElementId> hidden = new LinkedHashSet<>(own);
        for (CircuitCanvas owner : sharing) {
            hidden.addAll(owner.tool.hiddenWires(new CrossPaneContext(owner, this)));
        }
        return hidden;
    }

    /** The parts the tools preview changed in this frame: this canvas's tool's and those of gestures crossing here. */
    private Map<ElementId, Component> previewedParts(List<CircuitCanvas> sharing) {
        Map<ElementId, Component> own = tool.previewedParts(this);
        if (sharing.isEmpty()) {
            return own;
        }
        Map<ElementId, Component> previewed = new LinkedHashMap<>(own);
        for (CircuitCanvas owner : sharing) {
            previewed.putAll(owner.tool.previewedParts(new CrossPaneContext(owner, this)));
        }
        return previewed;
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

    void viewChanged() {
        viewListeners.forEach(Runnable::run);
        repaint();
    }

    CanvasMouseEvent convert(MouseEvent event) {
        return convert(event, event.getX(), event.getY());
    }

    /** {@code event}'s buttons and keys with the pointer at ({@code x}, {@code y}) of this canvas. */
    private CanvasMouseEvent convert(MouseEvent event, int x, int y) {
        int button = SwingUtilities.isRightMouseButton(event) ? CanvasMouseEvent.SECONDARY
                : SwingUtilities.isMiddleMouseButton(event) ? CanvasMouseEvent.MIDDLE
                : CanvasMouseEvent.PRIMARY;
        return new CanvasMouseEvent(viewport.screenToGrid(x, y), viewport.snap(x, y), new Point2D.Double(x, y),
                button, event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.getClickCount());
    }

    private final class MouseHandler extends MouseAdapter {

        private void track(MouseEvent event) {
            boolean inside = contains(event.getPoint());
            mousePoint = inside ? viewport.snap(event.getX(), event.getY()) : null;
            pointerEvent = inside ? event : null;
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
                pannedWithSpace = spaceDown;
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
            endGesture();
            autoscroll.stopHoverAutoscroll(); // a hover scroll ends with the press
            pressForwardedTo = null; // an earlier press whose release never came
            Optional<CircuitCanvas> drawer = otherDrawingPane();
            if (drawer.isPresent()) {
                pressForwardedTo = drawer.get();
                pressForwardedTo.tool.mousePressed(forwardedContext(), converted);
                pressForwardedTo.updateAnimation();
                return;
            }
            tool.mousePressed(CircuitCanvas.this, converted);
            gestureUnderWay = true;
            updateAnimation();
        }

        /** What the tool of the pane a press was forwarded to sees: its own pane, with this one's view and snapping. */
        private CrossPaneContext forwardedContext() {
            return new CrossPaneContext(pressForwardedTo, CircuitCanvas.this);
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
            if (pressForwardedTo != null) {
                pressForwardedTo.tool.mouseDragged(forwardedContext(), convert(event));
                pressForwardedTo.repaint();
                pressForwardedTo.repaintOtherPanes();
                return;
            }
            Optional<CircuitCanvas> otherPane = otherPaneUnder(event.getPoint());
            if (otherPane.isPresent()) {
                dragOver(otherPane.get(), event);
                return;
            }
            pointerPaneContext = null;
            if (!CircuitCanvas.this.contains(event.getPoint()) && canvasDragOut.overAnotherWindow(event.getPoint())
                    && beginDragOut()) {
                autoscroll.stopAutoscroll();
                getTransferHandler().exportAsDrag(CircuitCanvas.this, event, TransferHandler.MOVE);
                return;
            }
            tool.mouseDragged(CircuitCanvas.this, convert(event));
            autoscroll.followEdge(event);
            if (sharesGesture()) {
                repaintOtherPanes();
            }
            updateAnimation();
        }

        /**
         * The gesture's pointer is over another pane of the window: this canvas stops scrolling, and the tool gets the
         * point as that pane sees it, with that pane's view and snapping.
         */
        private void dragOver(CircuitCanvas pane, MouseEvent event) {
            autoscroll.stopAutoscroll();
            if (pointerPaneContext == null || pointerPaneContext.over() != pane) {
                pointerPaneContext = new CrossPaneContext(CircuitCanvas.this, pane);
            }
            tool.mouseDragged(pointerPaneContext, convertFor(pane, event));
            repaint();
            repaintOtherPanes();
            updateAnimation();
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (isExtraButton(event)) {
                return;
            }
            autoscroll.stopAutoscroll();
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
            if (pressForwardedTo != null) {
                releaseForwarded(converted);
                return;
            }
            Optional<CircuitCanvas> otherPane = otherPaneUnder(event.getPoint());
            if (otherPane.isPresent()) {
                CrossPaneContext context = new CrossPaneContext(CircuitCanvas.this, otherPane.get());
                endGesture();
                tool.mouseReleased(context, convertFor(otherPane.get(), event));
                tool.mouseExited(CircuitCanvas.this); // the pointer is in the other pane: no hover left here
                repaint();
                otherPane.get().repaint();
            } else {
                endGesture();
                tool.mouseReleased(CircuitCanvas.this, converted);
            }
            updateAnimation();
        }

        /** The release of a press given to another pane's tool goes there too; then this pane's clicks are its own. */
        private void releaseForwarded(CanvasMouseEvent converted) {
            CircuitCanvas drawer = pressForwardedTo;
            drawer.tool.mouseReleased(forwardedContext(), converted);
            pressForwardedTo = null;
            drawer.repaint();
            drawer.repaintOtherPanes();
            drawer.updateAnimation();
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
                pointerMovedTo(event);
            }
            autoscroll.followHover(event);
            showCursor();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            mousePoint = null;
            pointerEvent = null;
            autoscroll.stopHoverAutoscroll();
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
            track(event); // where the pointer is when the view moves under it
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
            if (host.takeKey(CircuitCanvas.this, event)) {
                event.consume(); // a part has the keyboard: no key binding runs (decision 5)
                return;
            }
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                if (!spaceDown) {
                    pannedWithSpace = false; // a new press, not the key repeat of a held Space
                }
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
            if (keyPane().giveStrokeToTool(javax.swing.KeyStroke.getKeyStrokeForEvent(event))
                    || keyPane().giveKeyToTool(event.getKeyCode(), event.isShiftDown())) {
                event.consume();
            }
        }

        @Override
        public void keyTyped(KeyEvent event) {
            if (host.takeKey(CircuitCanvas.this, event)) {
                event.consume();
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                boolean tapped = spaceDown && !pannedWithSpace;
                spaceDown = false;
                showCursor();
                if (tapped) {
                    spaceTapped(event);
                }
            }
            modifierChanged(event, false);
        }

        /**
         * Espacio was pressed and released with no pan in between (decided point 2 of 2026-11-08): the tool may flip
         * the elbow of the wire it draws; a held Espacio stays «hold to pan».
         */
        private void spaceTapped(KeyEvent event) {
            CircuitCanvas pane = keyPane();
            if (!pane.tool.flipElbow(pane.toolContext())) {
                return;
            }
            event.consume();
            if (pane.sharesGesture()) {
                pane.repaintOtherPanes();
            }
            pane.updateAnimation();
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
            if (tool.modifiersChanged(toolContext(), shiftDown, controlDown, altDown)) {
                if (sharesGesture()) {
                    repaintOtherPanes();
                }
                updateAnimation();
            }
        }
    }
}
