package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.HandleSide;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SnapMode;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.SubcircuitPainter;
import cl.almejo.vsim.ui.paint.SymbolPainter;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.inline.InlineHost;
import cl.almejo.vsim.ui.inline.InlineEditRequest;

import java.util.Comparator;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shape-editing gestures and paint; every completed gesture publishes one immutable draft. */
@SuppressWarnings("serial")
final class ShapeCanvas extends JPanel {
    static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final String MESSAGE_PREFIX = "shape.";
    static final int HIT_RADIUS = 7;
    static final int BOX_HANDLE_OFFSET = 18;
    private static final int FIT_SIDE_MARGIN = 64;
    private static final int FIT_VERTICAL_MARGIN = 64;
    /** About 32 px per grid unit: the box looks comfortable, as in the mockup, however small it is. */
    private static final double FIT_MAXIMUM_ZOOM = 4;
    /** The view before the first fit (and in tests that never paint): zoom 2, the box's corner 70 px in. */
    private static final double INITIAL_ZOOM = 2;
    private static final double INITIAL_ORIGIN = 70;
    /** The selection band as the main editor's Selección draws it: fill alpha, ants width, ants pace. */
    static final int BAND_FILL_ALPHA = 40;
    static final float BAND_WIDTH = 1f;
    private static final int ANTS_MILLISECONDS = 90;
    /** On open the box takes about this part of each side of the canvas. */
    private static final double FIT_BOX_SHARE = 0.6;
    /** What a text's selection and click area add around its painted block, in screen pixels. */
    private static final double TEXT_BLOCK_PADDING = 4;
    /** Shift and an arrow moves this many grid units, as in the main editor. */
    private static final int FAR_STEP = 5;
    /** The fewest nodes «Quitar nodo» leaves in a figure: the minimums {@link SymbolPath} checks. */
    static final int MINIMUM_OPEN_NODES = 2;
    static final int MINIMUM_CLOSED_NODES = 3;
    /** A click curves a straight segment by a sixth of its length: visible, and gentle enough to drag from. */
    private static final double DEFAULT_BULGE_DIVISOR = 6;
    final ComponentDefinition definition;
    final Supplier<SymbolDraft> current;
    final Consumer<SymbolDraft> publish;
    private final Consumer<SymbolDraft> preview;
    final Supplier<ColorScheme> colors;
    final Runnable changed;
    ShapeTool tool = ShapeTool.SELECT;
    private SnapMode snap = SnapMode.HALF_UNITS;
    /** Zoom and pan, the main editor's: grid units of 8 px times the zoom, the box's corner at the grid origin. */
    final Viewport viewport = new Viewport();
    final MarchingAnts marchingAnts = new MarchingAnts();
    private FrameTicker antsTicker = FrameTicker.swing(ANTS_MILLISECONDS);
    Optional<ElementId> pin = Optional.empty();
    int pathIndex = -1;
    int nodeIndex = -1;
    private boolean nodeDrag;
    /** Nodos or «Curvar tramo»: the curve handle being dragged; with Alt it moves alone. */
    Optional<HandleRef> grabbedHandle = Optional.empty();
    /** The curve handle under the still pointer: drawn larger, so the user sees it can be grabbed. */
    Optional<HandleRef> hoveredHandle = Optional.empty();
    private boolean handleAlone;
    Optional<ShapeSegments.Hit> insertion = Optional.empty();
    Optional<NodeHit> removal = Optional.empty();
    /** «Curvar tramo»: the segment under the pointer, and the one being bent with where along it it was grabbed. */
    Optional<SegmentRef> curveTarget = Optional.empty();
    Optional<SegmentRef> curving = Optional.empty();
    private double curvingParameter;
    private Optional<String> refusal = Optional.empty();
    private boolean controlHeld;
    private boolean shiftHeld;
    /** The pointer is over the box's resize handle (the hint line then speaks about the box). */
    private boolean overBoxHandle;
    private Optional<SymbolDraft.Handle> handle = Optional.empty();
    private boolean resizingBox;
    private boolean pinDragActive;
    private boolean panning;
    private boolean rubberBand;
    SymbolPoint bandEnd;
    private Point pressed;
    SymbolPoint start;
    private SymbolDraft beforeDrag;
    private SymbolDraft committedBeforeDrag;
    private SymbolDraft dragging;
    final ShapePathDrawing pathDrawing = new ShapePathDrawing(this);
    private final ShapeCanvasPainter painter = new ShapeCanvasPainter(this);
    private final InlineTextEditor textEditor = InlineTextEditor.forHost(new ShapeHost());
    Optional<ElementId> hoveredPin = Optional.empty();
    int hoveredText = -1;
    private int textIndex = -1;
    private boolean fitPending = true;
    private int fittedWidth = -1;
    private int fittedHeight = -1;
    Point pointer = new Point();
    private Point lastDragPoint;
    Optional<String> dragTip = Optional.empty();

    /** A node of a figure: its path and its place in it. */
    record NodeHit(int pathIndex, int nodeIndex) { }

    /** One handle of a curve: the {@code side} one of node {@code nodeIndex} of figure {@code pathIndex}. */
    record HandleRef(int pathIndex, int nodeIndex, HandleSide side) { }

    /** A segment of a figure: from node {@code segment} to the next one. */
    record SegmentRef(int pathIndex, int segment) { }

    /** A pin sharing its connection point with another one: the editor refuses to accept this. */
    record Coincidence(ElementId pin, GridPoint offset, String otherName) { }

    ShapeCanvas(ComponentDefinition definition, Supplier<SymbolDraft> current, Consumer<SymbolDraft> publish,
                Consumer<SymbolDraft> preview, Supplier<ColorScheme> colors, Runnable changed) {
        this.definition = definition;
        this.current = current;
        this.publish = publish;
        this.preview = preview;
        this.colors = colors;
        this.changed = changed;
        viewport.zoomAt(0, 0, INITIAL_ZOOM);
        viewport.setOrigin(INITIAL_ORIGIN, INITIAL_ORIGIN);
        setLayout(null);
        setFocusable(true);
        setToolTipText(Messages.text("shape.canvas.hint"));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                press(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                drag(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                release(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                click(event);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                pointerMoved(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredPin = Optional.empty();
                hoveredText = -1;
                insertion = Optional.empty();
                removal = Optional.empty();
                curveTarget = Optional.empty();
                hoveredHandle = Optional.empty();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (event.isControlDown()) {
                    // The main editor's wheel zoom: one step per notch, the point under the cursor stays put.
                    zoomAt(event.getX(), event.getY(),
                            zoom() * Math.pow(Viewport.ZOOM_STEP, -event.getPreciseWheelRotation()));
                } else {
                    fitPending = false;
                    viewport.panBy(0, -event.getPreciseWheelRotation() * 24);
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftChanged(true);
                } else if (event.getKeyCode() == KeyEvent.VK_CONTROL) {
                    controlChanged(true);
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftChanged(false);
                } else if (event.getKeyCode() == KeyEvent.VK_CONTROL) {
                    controlChanged(false);
                }
            }
        });
    }

    void setTool(ShapeTool selected) {
        // A figure being drawn is kept, as with Esc, rather than lost to the tool change.
        if (!pathDrawing.pathNodes.isEmpty()) {
            pathDrawing.finishOrDropPath();
        }
        cancelOperation();
        tool = selected;
        pin = Optional.empty();
        pathIndex = -1;
        nodeIndex = -1;
        hoveredHandle = Optional.empty();
        insertion = Optional.empty();
        removal = Optional.empty();
        refusal = Optional.empty();
        curveTarget = Optional.empty();
        overBoxHandle = false;
        setCursor(selected == ShapeTool.PAN ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    void setSnap(boolean enabled) {
        snap = enabled ? SnapMode.HALF_UNITS : SnapMode.FREE;
    }

    /** Sets the zoom keeping the box's corner where it is on screen; a shortcut for tests. */
    void setZoom(double value) {
        Point2D origin = viewport.gridToScreen(0, 0);
        zoomAt(origin.getX(), origin.getY(), value);
    }

    /** The toolbar's zoom, like the main editor's zoom control: the middle of the canvas stays put. */
    void zoomAroundCenter(double value) {
        zoomAt(getWidth() / 2.0, getHeight() / 2.0, value);
    }

    /** A zoom the user chose, clamped to the main editor's range, keeping screen point ({@code x}, {@code y}) put. */
    private void zoomAt(double x, double y, double value) {
        fitPending = false;
        viewport.zoomAt(x, y, value);
        changed.run();
        repaint();
    }

    /** Drives the band's marching ants with {@code ticker} instead of a Swing timer: for tests. */
    void useAntsTicker(FrameTicker ticker) {
        antsTicker.stop();
        antsTicker = ticker;
    }

    double antsPhase() {
        return marchingAnts.phase();
    }

    /** Zooms and pans so the whole box (with room for the callouts and its resize handle) is centred and visible. */
    void fitBox() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        GridSize size = current.get().appearance().size();
        double boxWidth = size.width() * UNIT;
        double boxHeight = size.height() * UNIT;
        double availableWidth = Math.max(1, getWidth() - 2.0 * FIT_SIDE_MARGIN);
        double availableHeight = Math.max(1, getHeight() - 2.0 * FIT_VERTICAL_MARGIN);
        double sharedWidth = FIT_BOX_SHARE * getWidth() / boxWidth;
        double sharedHeight = FIT_BOX_SHARE * getHeight() / boxHeight;
        double fitted = Math.clamp(Math.min(Math.min(availableWidth / boxWidth, availableHeight / boxHeight),
                Math.min(sharedWidth, sharedHeight)), Viewport.MINIMUM_ZOOM, FIT_MAXIMUM_ZOOM);
        Point2D origin = viewport.gridToScreen(0, 0);
        viewport.zoomAt(origin.getX(), origin.getY(), fitted);
        viewport.setOrigin((getWidth() - boxWidth * zoom()) / 2, (getHeight() - boxHeight * zoom()) / 2);
        fittedWidth = getWidth();
        fittedHeight = getHeight();
        changed.run();
    }

    /** The box rectangle in canvas pixels. */
    Rectangle2D boxOnScreen() {
        GridSize size = current.get().appearance().size();
        Point2D corner = viewport.gridToScreen(0, 0);
        return new Rectangle2D.Double(corner.getX(), corner.getY(), size.width() * viewport.pixelsPerUnit(),
                size.height() * viewport.pixelsPerUnit());
    }

    double zoom() {
        return viewport.zoom();
    }

    Optional<ElementId> selectedPin() {
        return pin;
    }

    void selectPin(ElementId source) {
        pin = Optional.of(source);
        repaint();
    }

    /** Where the pin {@code source} is on screen now (its connection point). */
    Point pinLocation(ElementId source) {
        GridPoint offset = current.get().appearance().pins().stream()
                .filter(placement -> placement.source().equals(source)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no pin " + source.value())).offset();
        return screen(offset.x() * 8, offset.y() * 8);
    }

    boolean operationActive() {
        return pressed != null || pathDrawing.drawingPath() || textEditor.isOpen();
    }

    boolean hasSelection() {
        return pin.isPresent() || !current.get().selection().isEmpty() || nodeIndex >= 0;
    }

    /** A node (not only a segment) is selected: the last node of an open path counts. */
    boolean nodeSelected() {
        SymbolDraft view = current.get();
        if (pathIndex < 0 || nodeIndex < 0 || pathIndex >= view.elements().size()) {
            return false;
        }
        return view.elements().get(pathIndex) instanceof SymbolPath path && nodeIndex < path.nodes().size();
    }


    void draftChanged() {
        insertion = Optional.empty();
        // A text being typed was started on an earlier draft: its element may no longer exist (undo, toolbar edit).
        textEditor.cancel();
        textIndex = -1;
        if (pathIndex < 0 || pathIndex >= current.get().elements().size()
                || !current.get().selection().contains(pathIndex)
                || !(current.get().elements().get(pathIndex) instanceof SymbolPath path)
                || nodeIndex >= path.nodes().size()) {
            pathIndex = -1;
            nodeIndex = -1;
            nodeDrag = false;
            grabbedHandle = Optional.empty();
        }
        hoveredHandle = hoveredHandle.filter(this::handleExists);
    }

    void clearSelection() {
        pin = Optional.empty();
        pathIndex = -1;
        nodeIndex = -1;
        publish.accept(current.get().select(Set.of()));
    }

    void cancelOperation() {
        if (committedBeforeDrag != null) {
            preview.accept(committedBeforeDrag);
        }
        pressed = null;
        lastDragPoint = null;
        dragging = null;
        beforeDrag = null;
        committedBeforeDrag = null;
        pathDrawing.reset();
        pinDragActive = false;
        grabbedHandle = Optional.empty();
        curving = Optional.empty();
        dragTip = Optional.empty();
        rubberBand = false;
        bandEnd = null;
        textEditor.cancel();
        updateAnts();
        repaint();
    }

    /** Enter: the text being typed is done; a multiline or polygon being drawn is finished (or dropped if too short). */
    void finishOperation() {
        if (textEditor.isOpen()) {
            textEditor.confirmOrCancel();
            return;
        }
        if (!pathDrawing.pathNodes.isEmpty()) {
            pathDrawing.finishOrDropPath();
        }
    }

    /**
     * Esc: a multiline or polygon being drawn keeps its placed nodes (only the rubber band to the pointer goes), as
     * long as it has enough of them; any other operation is cancelled.
     */
    void escapeOperation() {
        if (pathDrawing.pathNodes.isEmpty()) {
            cancelOperation();
        } else {
            pathDrawing.finishOrDropPath();
        }
    }

    /** Whether a multiline or a polygon is being drawn: Ctrl+Z takes back its last node instead of an edit. */
    boolean drawingPath() {
        return pathDrawing.drawingPath();
    }

    /** Ctrl+Z while drawing: the last placed node goes; with the first one, the drawing ends. */
    void removeLastNode() {
        pathDrawing.removeLastNode();
    }

    /** The nodes placed so far by Multilínea or Polígono, in eighths. */
    List<SymbolPoint> placedNodes() {
        return pathDrawing.placedNodes();
    }

    /** Multilínea (open) or Polígono (always closed): figures drawn a click per node. */
    private boolean isPathTool() {
        return tool == ShapeTool.MULTILINE || tool == ShapeTool.POLYGON;
    }

    void deleteSelection() {
        if ((tool == ShapeTool.NODES || tool == ShapeTool.ADD_NODE) && nodeIndex >= 0) {
            publish.accept(nodeDraft().deleteNode(pathIndex, nodeIndex));
            nodeIndex = -1;
        } else if (pin.isEmpty()) {
            publish.accept(current.get().deleteSelection());
        }
    }

    /** One arrow press: a grid unit for a pin, a magnet step (half a unit) for the rest. */
    void moveSelection(int horizontal, int vertical) {
        moveSelection(horizontal, vertical, 1, 4);
    }

    /** Shift and an arrow: the main editor's larger step, five grid units. */
    void moveSelectionFar(int horizontal, int vertical) {
        moveSelection(horizontal, vertical, FAR_STEP, 8 * FAR_STEP);
    }

    private void moveSelection(int horizontal, int vertical, int pinUnits, int eighths) {
        if (pin.isPresent()) {
            if (current.get().pinsLocked()) {
                return;
            }
            PinPlacement selected = current.get().appearance().pins().stream()
                    .filter(placement -> placement.source().equals(pin.get())).findFirst().orElseThrow();
            publish.accept(current.get().movePin(pin.get(), new GridPoint(selected.offset().x() + horizontal * pinUnits,
                    selected.offset().y() + vertical * pinUnits)));
        } else if ((tool == ShapeTool.NODES || tool == ShapeTool.ADD_NODE) && nodeIndex >= 0) {
            SymbolDraft editing = nodeDraft();
            SymbolPoint point = chosenPoint((SymbolPath) editing.elements().get(pathIndex));
            SymbolPoint moved = new SymbolPoint(point.x() + horizontal * eighths, point.y() + vertical * eighths);
            publish.accept(editing.moveNode(pathIndex, nodeIndex, moved, snap));
        } else {
            publish.accept(current.get().moveSelection(horizontal * eighths, vertical * eighths, snap));
        }
    }

    SymbolPoint chosenPoint(SymbolPath path) {
        return path.nodes().get(nodeIndex).point();
    }

    private SymbolDraft nodeDraft() {
        return current.get().select(Set.of(pathIndex));
    }

    private void press(MouseEvent event) {
        textEditor.confirmOrCancel();
        pointer = event.getPoint();
        dragTip = Optional.empty();
        requestFocusInWindow();
        pressed = event.getPoint();
        start = tool == ShapeTool.LINE && pathDrawing.lineAnchor != null ? pathDrawing.lineAnchor : point(event);
        beforeDrag = current.get();
        dragging = null;
        committedBeforeDrag = beforeDrag;
        // Ctrl+click with «Curvar tramo» straightens the segment, even next to one of its handles.
        boolean grabs = SwingUtilities.isLeftMouseButton(event) && !(tool == ShapeTool.CURVE && event.isControlDown());
        grabbedHandle = grabs ? grabbableHandleAt(event.getPoint()) : Optional.empty();
        // Alt pans the view, except on a curve handle: there it drags that handle alone.
        panning = tool == ShapeTool.PAN || SwingUtilities.isMiddleMouseButton(event)
                || event.isAltDown() && grabbedHandle.isEmpty();
        if (panning || !SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        if (grabbedHandle.isPresent()) {
            grabHandle(grabbedHandle.get(), event.isAltDown());
            return;
        }
        if (isPathTool() || tool == ShapeTool.TEXT || isNodeTool()) {
            return;
        }
        handle = Optional.empty();
        resizingBox = false;
        nodeDrag = false;
        rubberBand = false;
        curving = Optional.empty();
        if (tool == ShapeTool.CURVE) {
            pressCurve(event);
            return;
        }
        if (tool == ShapeTool.SELECT) {
            if (pressSelectHandleOrPin(event)) {
                return;
            }
        }
        if (tool == ShapeTool.NODES) {
            if (pressNode(event)) {
                return;
            }
        }
        if (tool == ShapeTool.SELECT || tool == ShapeTool.NODES) {
            pressFigure(event);
        }
    }

    /** Selección: a press on a selection handle, the box's handle or a pin; true when it took the press. */
    private boolean pressSelectHandleOrPin(MouseEvent event) {
        handle = hitHandle(event.getPoint());
        resizingBox = handle.isEmpty() && !current.get().pinsLocked()
                && boxHandleLocation().distance(event.getPoint()) < HIT_RADIUS;
        if (handle.isPresent() || resizingBox) {
            return true;
        }
        pin = pinAt(event.getPoint());
        if (pin.isPresent()) {
            changed.run();
            repaint();
            return true;
        }
        return false;
    }

    /** Nodos: a press on a node chooses it and starts dragging it; true when there was a node there. */
    private boolean pressNode(MouseEvent event) {
        if (hitNode(event.getPoint())) {
            nodeDrag = true;
            beforeDrag = nodeDraft();
            publish.accept(beforeDrag);
            changed.run();
            repaint();
            return true;
        }
        return false;
    }

    /**
     * Selección or Nodos: a press on a figure selects it (Shift toggles it) ready to be moved; a press on empty space
     * starts the rubber band.
     */
    private void pressFigure(MouseEvent event) {
        int hit = hitFigure(event.getPoint());
        Set<Integer> selection = new LinkedHashSet<>(current.get().selection());
        if (hit >= 0) {
            if (event.isShiftDown()) {
                if (!selection.remove(hit)) {
                    selection.add(hit);
                }
            } else if (!selection.contains(hit)) {
                selection = new LinkedHashSet<>(Set.of(hit));
            }
        } else if (!event.isShiftDown()) {
            selection.clear();
        }
        rubberBand = hit < 0;
        bandEnd = start;
        SymbolDraft view = current.get();
        beforeDrag = view.select(selection);
        publish.accept(beforeDrag);
        if (tool == ShapeTool.NODES && hit >= 0 && view.elements().get(hit) instanceof SymbolPath) {
            pathIndex = hit;
            nodeIndex = hitSegment(hit, event.getPoint());
        }
    }

    /**
     * «Curvar tramo»: Ctrl+press straightens the segment under the pointer; a press on a segment grabs it where it was
     * pressed and starts bending it (a release without moving gives a straight segment a gentle default curve).
     */
    private void pressCurve(MouseEvent event) {
        controlHeld = event.isControlDown();
        Optional<SegmentRef> target = curveTargetAt(event.getPoint());
        if (target.isEmpty()) {
            return;
        }
        SymbolDraft editing = current.get().select(Set.of(target.get().pathIndex()));
        if (event.isControlDown()) {
            SymbolPath path = (SymbolPath) editing.elements().get(target.get().pathIndex());
            if (path.nodes().get(target.get().segment()).curved()) {
                publish.accept(editing.straighten(target.get().pathIndex(), target.get().segment()));
            }
            return;
        }
        curving = target;
        SymbolPath path = (SymbolPath) editing.elements().get(target.get().pathIndex());
        curvingParameter = path.nearestParameter(target.get().segment(), localX(event.getPoint()),
                localY(event.getPoint()));
        beforeDrag = editing;
    }

    /** The segment under the pointer. */
    private Optional<SegmentRef> curveTargetAt(Point at) {
        return segmentAt(at).map(hit -> new SegmentRef(hit.pathIndex(), hit.segmentIndex()));
    }

    /**
     * A press on a curve handle: its node becomes the chosen one (Nodos labels it) and the drag moves the handle, the
     * other one of a smooth node turning with it unless Alt is held.
     */
    private void grabHandle(HandleRef grabbed, boolean alone) {
        handleAlone = alone;
        nodeDrag = false;
        pathIndex = grabbed.pathIndex();
        nodeIndex = grabbed.nodeIndex();
        beforeDrag = nodeDraft();
        if (tool == ShapeTool.NODES) {
            publish.accept(beforeDrag);
            changed.run();
        }
        repaint();
    }

    /** The handles drawn now, in eighths: with Nodos and «Curvar tramo», those of every curved segment. */
    List<SymbolPoint> visibleHandles() {
        return curveHandles().stream().map(this::handleLocation).toList();
    }

    /**
     * Every handle of every curved segment, figure by figure and node by node (incoming first): with Nodos and
     * «Curvar tramo» they are all on view and can be grabbed, without hovering a segment or choosing a node first.
     */
    List<HandleRef> curveHandles() {
        if (tool != ShapeTool.NODES && tool != ShapeTool.CURVE) {
            return List.of();
        }
        List<HandleRef> handles = new ArrayList<>();
        List<SymbolElement> elements = current.get().elements();
        for (int index = 0; index < elements.size(); index++) {
            if (!(elements.get(index) instanceof SymbolPath path)) {
                continue;
            }
            for (int node = 0; node < path.nodes().size(); node++) {
                for (HandleSide side : HandleSide.values()) {
                    if (path.handle(node, side).isPresent()) {
                        handles.add(new HandleRef(index, node, side));
                    }
                }
            }
        }
        return handles;
    }

    private boolean handleExists(HandleRef reference) {
        return curveHandles().contains(reference);
    }

    /** Where a handle is, in eighths. */
    SymbolPoint handleLocation(HandleRef reference) {
        return ((SymbolPath) current.get().elements().get(reference.pathIndex()))
                .handle(reference.nodeIndex(), reference.side()).orElseThrow();
    }

    /** The node a handle belongs to, in eighths. */
    SymbolPoint handleNode(HandleRef reference) {
        return ((SymbolPath) current.get().elements().get(reference.pathIndex())).nodes()
                .get(reference.nodeIndex()).point();
    }

    /**
     * The handle a press would grab: the nearest one within reach, the topmost figure first on a tie. With Nodos a node
     * at least as near wins, so a handle lying on its node never hides the node.
     */
    private Optional<HandleRef> grabbableHandleAt(Point at) {
        Optional<HandleRef> nearest = Optional.empty();
        double nearestDistance = HIT_RADIUS;
        for (HandleRef candidate : curveHandles().reversed()) {
            SymbolPoint location = handleLocation(candidate);
            double distance = screen(location.x(), location.y()).distance(at);
            if (distance < nearestDistance) {
                nearest = Optional.of(candidate);
                nearestDistance = distance;
            }
        }
        if (nearest.isEmpty() || tool != ShapeTool.NODES) {
            return nearest;
        }
        double handleDistance = nearestDistance;
        boolean nodeNearer = nodeAt(at).filter(node -> {
            SymbolPoint point = ((SymbolPath) current.get().elements().get(node.pathIndex())).nodes()
                    .get(node.nodeIndex()).point();
            return screen(point.x(), point.y()).distance(at) <= handleDistance;
        }).isPresent();
        return nodeNearer ? Optional.empty() : nearest;
    }

    /**
     * The handle of a straight segment that a click curves: a sixth of its length off its middle, away from the
     * figure's centre for a closed figure, towards the click for an open one, on the side that keeps the curve
     * inside the box.
     */
    private SymbolPoint defaultBulge(SymbolDraft editing, SegmentRef target, SymbolPoint clicked) {
        SymbolPath path = (SymbolPath) editing.elements().get(target.pathIndex());
        SymbolPoint start = path.nodes().get(target.segment()).point();
        SymbolPoint end = path.nodes().get((target.segment() + 1) % path.nodes().size()).point();
        double middleX = (start.x() + end.x()) / 2.0;
        double middleY = (start.y() + end.y()) / 2.0;
        double length = Math.hypot(end.x() - start.x(), end.y() - start.y());
        if (length == 0) {
            return start;
        }
        double normalX = -(end.y() - start.y()) / length;
        double normalY = (end.x() - start.x()) / length;
        double awayX = path.closed() ? middleX - centreX(path) : clicked.x() - middleX;
        double awayY = path.closed() ? middleY - centreY(path) : clicked.y() - middleY;
        double side = normalX * awayX + normalY * awayY < 0 ? -1 : 1;
        double controlOffset = 2 * length / DEFAULT_BULGE_DIVISOR;
        if (!controlInsideBox(middleX + side * normalX * controlOffset, middleY + side * normalY * controlOffset)) {
            side = -side;
        }
        double bulge = side * length / DEFAULT_BULGE_DIVISOR;
        return new SymbolPoint((int) Math.round(middleX + normalX * bulge), (int) Math.round(middleY + normalY * bulge));
    }

    private boolean controlInsideBox(double x, double y) {
        GridSize size = current.get().appearance().size();
        return x >= 0 && y >= 0 && x <= size.width() * 8 && y <= size.height() * 8;
    }

    private static double centreX(SymbolPath path) {
        return path.nodes().stream().mapToInt(node -> node.point().x()).average().orElse(0);
    }

    private static double centreY(SymbolPath path) {
        return path.nodes().stream().mapToInt(node -> node.point().y()).average().orElse(0);
    }

    boolean shiftHeld() {
        return shiftHeld;
    }

    private void drag(MouseEvent event) {
        controlHeld = event.isControlDown();
        shiftHeld = event.isShiftDown();
        if (grabbedHandle.isPresent()) {
            handleAlone = event.isAltDown();
        }
        drag(event.getPoint(), shiftHeld);
    }

    /** Shift pressed or released in the middle of a drag: the move is worked out again at the same pointer. */
    private void shiftChanged(boolean shift) {
        shiftHeld = shift;
        if (pathDrawing.lineAnchor != null) {
            repaint();
        }
        if (pressed != null && lastDragPoint != null && !panning) {
            drag(lastDragPoint, shift);
        }
    }

    private void drag(Point where, boolean shift) {
        if (pressed == null) {
            return;
        }
        pointer = where;
        lastDragPoint = where;
        if (panning) {
            fitPending = false;
            viewport.panBy(where.x - pressed.x, where.y - pressed.y);
            pressed = where;
            repaint();
            return;
        }
        if (isPathTool()) {
            pathDrawing.dragPath(pressed, where);
            return;
        }
        if (tool == ShapeTool.TEXT || isNodeTool()) {
            return;
        }
        SymbolPoint end = point(where);
        dragTip = Optional.empty();
        try {
            if (curving.isPresent()) {
                dragging = beforeDrag.bendSegment(curving.get().pathIndex(), curving.get().segment(),
                        curvingParameter, end, snap);
            } else if (grabbedHandle.isPresent()) {
                dragCurveHandle(end, shift);
            } else if (resizingBox) {
                dragging = resizedBox(where);
                dragTip = Optional.of(boxTip(dragging));
            } else if (handle.isPresent()) {
                dragging = beforeDrag.scaleSelection(handle.get(), end, shift, snap);
                dragTip = figureTip(dragging);
            } else if (pin.isPresent() && tool == ShapeTool.SELECT) {
                if (beforeDrag.pinsLocked()) {
                    return;
                }
                dragPin(where, shift);
            } else if (tool == ShapeTool.NODES && nodeDrag && nodeIndex >= 0) {
                SymbolPoint original = chosenPoint((SymbolPath) beforeDrag.elements().get(pathIndex));
                SymbolPoint target = shift ? axisLocked(original, end) : end;
                dragging = beforeDrag.moveNode(pathIndex, nodeIndex, target, snap);
                dragTip = Optional.of(Messages.text("shape.drag.axis"));
            } else if (rubberBand) {
                dragBand(end);
            } else if (tool == ShapeTool.SELECT || tool == ShapeTool.NODES) {
                SymbolPoint moved = shift ? axisLocked(start, end) : end;
                dragging = beforeDrag.moveSelection(moved.x() - start.x(), moved.y() - start.y(), snap);
                dragTip = Optional.of(Messages.text("shape.drag.axis"));
            } else {
                dragNewFigure(end, shift);
            }
            preview.accept(dragging);
            repaint();
        } catch (IllegalArgumentException exception) {
            // A constrained figure at the box edge can be invalid until the pointer returns inside.
            preview.accept(dragging == null ? beforeDrag : dragging);
        }
    }

    /** Nodos or «Curvar tramo»: the grabbed handle follows the pointer (along one axis with Shift). */
    private void dragCurveHandle(SymbolPoint end, boolean shift) {
        HandleRef grabbed = grabbedHandle.get();
        SymbolPoint original = ((SymbolPath) beforeDrag.elements().get(grabbed.pathIndex()))
                .handle(grabbed.nodeIndex(), grabbed.side()).orElseThrow();
        dragging = beforeDrag.moveHandle(grabbed.pathIndex(), grabbed.nodeIndex(), grabbed.side(),
                shift ? axisLocked(original, end) : end, handleAlone, snap);
        dragTip = Optional.of(Messages.text("shape.drag.handleAlone"));
    }

    /** Selección: the selected pin follows the pointer to whole grid points (along one axis with Shift). */
    private void dragPin(Point where, boolean shift) {
        pinDragActive = true;
        Point2D grid = viewport.screenToGrid(where.x, where.y);
        GridPoint target = new GridPoint((int) Math.round(grid.getX()), (int) Math.round(grid.getY()));
        if (shift) {
            GridPoint original = beforeDrag.appearance().pins().stream()
                    .filter(placement -> placement.source().equals(pin.get())).findFirst().orElseThrow().offset();
            boolean horizontal = Math.abs(target.x() - original.x()) >= Math.abs(target.y() - original.y());
            target = horizontal ? new GridPoint(target.x(), original.y()) : new GridPoint(original.x(), target.y());
        }
        dragging = beforeDrag.movePin(pin.get(), target);
        dragTip = Optional.of(Messages.text("shape.drag.axis"));
    }

    /** The rubber band reaches {@code end}: what it touches joins the selection the press left. */
    private void dragBand(SymbolPoint end) {
        bandEnd = end;
        updateAnts();
        Rectangle2D band = new Rectangle2D.Double(Math.min(start.x(), end.x()) / 8.0 * UNIT,
                Math.min(start.y(), end.y()) / 8.0 * UNIT, Math.abs(start.x() - end.x()) / 8.0 * UNIT,
                Math.abs(start.y() - end.y()) / 8.0 * UNIT);
        Set<Integer> selected = new LinkedHashSet<>(beforeDrag.selection());
        for (int index = 0; index < beforeDrag.elements().size(); index++) {
            SymbolElement element = beforeDrag.elements().get(index);
            boolean hit = element instanceof SymbolPath path ? band.intersects(SymbolPainter.path(path).getBounds2D())
                    : element instanceof SymbolText text && band.contains(text.anchor().x() / 8.0 * UNIT,
                    text.anchor().y() / 8.0 * UNIT);
            if (hit) {
                selected.add(index);
            }
        }
        dragging = beforeDrag.select(selected);
    }

    /** Línea, Rectángulo, Elipse or Triángulo: the new figure from the press to {@code end} (regular with Shift). */
    private void dragNewFigure(SymbolPoint end, boolean shift) {
        SymbolPath figure = switch (tool) {
            case LINE -> SymbolTemplates.line(start, end, shift);
            case RECTANGLE -> SymbolTemplates.rectangle(start, end, shift);
            case ELLIPSE -> SymbolTemplates.ellipse(start, end, shift);
            case TRIANGLE -> SymbolTemplates.triangle(start, end, shift);
            default -> throw new IllegalStateException("Not a figure tool");
        };
        dragging = beforeDrag.addFigure(figure);
        dragTip = figureTip(dragging);
    }

    /**
     * The box under the dragged handle: with Ctrl the whole drawing scales with it; otherwise what is on an edge moves
     * with that edge and the rest stays centred ({@link SymbolDraft#resizeBox}), never smaller than it allows.
     */
    private SymbolDraft resizedBox(Point where) {
        int width = beforeDrag.appearance().size().width()
                + (int) Math.round((where.x - pressed.x) / viewport.pixelsPerUnit());
        int height = beforeDrag.appearance().size().height()
                + (int) Math.round((where.y - pressed.y) / viewport.pixelsPerUnit());
        if (controlHeld) {
            return beforeDrag.scaleBox(new GridSize(Math.max(1, width), Math.max(1, height)));
        }
        GridSize minimum = beforeDrag.minimumBoxSize();
        return beforeDrag.resizeBox(new GridSize(Math.max(minimum.width(), width), Math.max(minimum.height(), height)));
    }

    /** Whether the box's handle is under the pointer or being dragged: the hint line then speaks about the box. */
    boolean boxHandleActive() {
        return overBoxHandle || resizingBox && pressed != null;
    }

    /** {@code target} kept only along the axis it is farthest from {@code origin} (horizontal on a tie). */
    private static SymbolPoint axisLocked(SymbolPoint origin, SymbolPoint target) {
        return Math.abs(target.x() - origin.x()) >= Math.abs(target.y() - origin.y())
                ? new SymbolPoint(target.x(), origin.y()) : new SymbolPoint(origin.x(), target.y());
    }

    private static boolean straight(SymbolDraft draft, SegmentRef target) {
        SymbolPath path = (SymbolPath) draft.elements().get(target.pathIndex());
        return path.nodes().get(target.segment()).curve().isEmpty();
    }

    private void release(MouseEvent event) {
        if (pressed == null) {
            return;
        }
        SymbolDraft completed = dragging;
        if (completed == null && curving.isPresent() && straight(beforeDrag, curving.get())) {
            completed = beforeDrag.bendSegment(curving.get().pathIndex(), curving.get().segment(), 0.5,
                    defaultBulge(beforeDrag, curving.get(), point(event)), SnapMode.FREE);
        }
        boolean drawsLines = !panning && SwingUtilities.isLeftMouseButton(event);
        if (drawsLines && tool == ShapeTool.LINE) {
            completed = pathDrawing.releaseLine(event, pressed, committedBeforeDrag, completed);
        } else if (drawsLines && isPathTool()) {
            pathDrawing.releasePath(event, pressed);
        }
        curving = Optional.empty();
        grabbedHandle = Optional.empty();
        SymbolDraft original = committedBeforeDrag;
        dragTip = Optional.empty();
        pinDragActive = false;
        pressed = null;
        lastDragPoint = null;
        beforeDrag = null;
        committedBeforeDrag = null;
        dragging = null;
        rubberBand = false;
        bandEnd = null;
        updateAnts();
        if (completed != null && original != null) {
            preview.accept(original);
            publish.accept(completed);
        }
        repaint();
    }

    boolean bandShown() {
        return pressed != null && rubberBand && bandEnd != null;
    }

    /** The ants march only while a band is on screen, like the main editor's: the ticker stops by itself after. */
    private void updateAnts() {
        if (bandShown() && !antsTicker.isRunning()) {
            antsTicker.start(this::antsTick);
        } else if (!bandShown() && antsTicker.isRunning()) {
            antsTicker.stop();
        }
    }

    private void antsTick() {
        updateAnts();
        if (antsTicker.isRunning()) {
            marchingAnts.advance();
            repaint();
        }
    }

    @Override
    public void removeNotify() {
        antsTicker.stop();
        super.removeNotify();
    }

    private void click(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) || tool == ShapeTool.PAN || event.isAltDown()) {
            return;
        }
        if (isNodeTool()) {
            controlHeld = event.isControlDown();
            hoverNodeTool(event.getPoint());
            if (nodeAction() == ShapeTool.ADD_NODE) {
                insertion.ifPresent(this::addNode);
            } else {
                removal.ifPresent(this::removeNode);
            }
            if (refusal.isEmpty()) {
                hoverNodeTool(event.getPoint());
            }
            repaint();
        } else if (isPathTool()) {
            // Placed on release (a click or the end of a drag), never twice.
            return;
        } else if (tool == ShapeTool.TEXT) {
            beginText(event.getPoint(), -1);
        }
        else if (event.getClickCount() == 2) {
            if (tool == ShapeTool.NODES && toggledNodeAt(event.getPoint())) {
                return;
            }
            int figure = hitFigure(event.getPoint());
            SymbolDraft view = current.get();
            if (figure >= 0 && view.elements().get(figure) instanceof SymbolText) {
                beginText(event.getPoint(), figure);
            } else if (figure >= 0 && tool == ShapeTool.NODES && view.elements().get(figure) instanceof SymbolPath path) {
                if (!path.closed() && path.nodes().size() >= 3
                        && screen(path.nodes().getFirst().point().x(), path.nodes().getFirst().point().y())
                        .distance(event.getPoint()) < HIT_RADIUS) {
                    publish.accept(view.select(Set.of(figure)).closePath(figure));
                } else {
                    int segment = hitSegment(figure, event.getPoint());
                    if (segment >= 0) {
                        publish.accept(view.select(Set.of(figure)).insertNode(figure, segment, point(event)));
                    }
                }
            }
        }
    }

    /**
     * Nodos, a double click on a node: smooth becomes corner and corner smooth; a node that is not between two curves
     * says why instead. False when there is no node there, or on the first node of an open figure (that closes it).
     */
    private boolean toggledNodeAt(Point at) {
        Optional<NodeHit> node = nodeAt(at);
        if (node.isEmpty()) {
            return false;
        }
        SymbolDraft view = current.get();
        SymbolPath path = (SymbolPath) view.elements().get(node.get().pathIndex());
        if (!path.closed() && node.get().nodeIndex() == 0 && path.nodes().size() >= MINIMUM_CLOSED_NODES) {
            return false;
        }
        if (!path.hasTwoHandles(node.get().nodeIndex())) {
            refuse("shape.node.smoothNeedsCurves");
            repaint();
            return true;
        }
        pathIndex = node.get().pathIndex();
        nodeIndex = node.get().nodeIndex();
        publish.accept(view.select(Set.of(pathIndex)).toggleSmooth(pathIndex, nodeIndex));
        repaint();
        return true;
    }

    /** «Agregar nodo» or «Quitar nodo»: the tools that act with one click on a segment or a node. */
    boolean isNodeTool() {
        return tool == ShapeTool.ADD_NODE || tool == ShapeTool.REMOVE_NODE;
    }

    /** What a click does now with a node tool: Ctrl held switches to the other one while it is held. */
    ShapeTool nodeAction() {
        if (!isNodeTool()) {
            return tool;
        }
        boolean adds = tool == ShapeTool.ADD_NODE;
        return adds != controlHeld ? ShapeTool.ADD_NODE : ShapeTool.REMOVE_NODE;
    }

    private void addNode(ShapeSegments.Hit hit) {
        SymbolDraft editing = current.get().select(Set.of(hit.pathIndex()));
        SymbolPath path = (SymbolPath) editing.elements().get(hit.pathIndex());
        if (path.nodes().size() >= SymbolPath.MAXIMUM_NODES) {
            refuse("shape.nodes.limit");
            return;
        }
        pathIndex = hit.pathIndex();
        nodeIndex = hit.segmentIndex() + 1;
        insertion = Optional.empty();
        publish.accept(editing.insertNode(hit.pathIndex(), hit.segmentIndex(), hit.point()));
    }

    /** Removes the node, unless the figure would fall below two nodes (three if it is closed): then says why. */
    private void removeNode(NodeHit hit) {
        SymbolDraft editing = current.get().select(Set.of(hit.pathIndex()));
        SymbolPath path = (SymbolPath) editing.elements().get(hit.pathIndex());
        int minimum = path.closed() ? MINIMUM_CLOSED_NODES : MINIMUM_OPEN_NODES;
        if (path.nodes().size() <= minimum) {
            refuse(path.closed() ? "shape.nodeRemove.minimumClosed" : "shape.nodeRemove.minimumOpen");
            return;
        }
        pathIndex = -1;
        nodeIndex = -1;
        removal = Optional.empty();
        publish.accept(editing.deleteNode(hit.pathIndex(), hit.nodeIndex()));
    }

    void refuse(String key) {
        refusal = Optional.of(Messages.text(key));
        changed.run();
    }

    /** The node of a figure under the pointer, the topmost figure first. */
    private Optional<NodeHit> nodeAt(Point at) {
        List<SymbolElement> elements = current.get().elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (!(elements.get(index) instanceof SymbolPath path)) {
                continue;
            }
            for (int node = 0; node < path.nodes().size(); node++) {
                SymbolPoint point = path.nodes().get(node).point();
                if (screen(point.x(), point.y()).distance(at) < HIT_RADIUS) {
                    return Optional.of(new NodeHit(index, node));
                }
            }
        }
        return Optional.empty();
    }

    /** Ctrl pressed or released: a node tool switches mode under a still pointer. */
    private void controlChanged(boolean held) {
        if (controlHeld == held) {
            return;
        }
        controlHeld = held;
        if (isNodeTool()) {
            hoverNodeTool(pointer);
            repaint();
        } else if (resizingBox && pressed != null && lastDragPoint != null) {
            drag(lastDragPoint, shiftHeld);
        }
    }

    /**
     * What a click would act on under the pointer: the segment (to add) or the node (to remove), with its cursor. A
     * refusal stays while the pointer is on the same target and goes when it moves on.
     */
    private void hoverNodeTool(Point at) {
        boolean adds = nodeAction() == ShapeTool.ADD_NODE;
        Optional<ShapeSegments.Hit> segment = adds ? segmentAt(at) : Optional.empty();
        Optional<NodeHit> node = adds ? Optional.empty() : nodeAt(at);
        boolean sameSegment = segment.isPresent() && insertion.isPresent()
                && segment.get().pathIndex() == insertion.get().pathIndex()
                && segment.get().segmentIndex() == insertion.get().segmentIndex();
        boolean sameNode = node.isPresent() && node.equals(removal);
        if (!sameSegment && !sameNode && refusal.isPresent()) {
            refusal = Optional.empty();
            changed.run();
        }
        insertion = segment;
        removal = node;
        setCursor(adds ? ShapeCursors.addNode() : ShapeCursors.removeNode());
        setToolTipText(Messages.text(adds ? "shape.nodeAdd.hint" : "shape.nodeRemove.hint"));
    }

    /** Opens the shared inline editor over the text (or where a new one goes); a blank text on confirm is no text. */
    private void beginText(Point location, int index) {
        SymbolText existing = index >= 0 && current.get().elements().get(index) instanceof SymbolText found ? found : null;
        textIndex = existing == null ? -1 : index;
        SymbolPoint anchor = existing == null ? point(location) : existing.anchor();
        TextSize size = existing == null ? TextSize.MEDIUM : existing.size();
        Rectangle2D area = existing == null ? newTextArea(anchor, size) : SymbolPainter.textBlock(existing,
                definition.name());
        Font font = TextFonts.labelFont().deriveFont(Font.BOLD, SymbolPainter.sizeOf(size));
        textEditor.open(InlineEditRequest.multiLine(area, font, existing == null ? "" : existing.text().orElse(""),
                entered -> Optional.empty(), entered -> commitText(anchor, index, entered)));
        repaint();
    }

    /** Where a new text will be written: a line tall, at the anchor. */
    private static Rectangle2D newTextArea(SymbolPoint anchor, TextSize size) {
        double height = SymbolPainter.lineHeight(size);
        return new Rectangle2D.Double(anchor.x() / 8.0 * UNIT, anchor.y() / 8.0 * UNIT - height / 2, 0, height);
    }

    private void commitText(SymbolPoint anchor, int requestedIndex, String entered) {
        SymbolText existing = requestedIndex >= 0 && requestedIndex < current.get().elements().size()
                && current.get().elements().get(requestedIndex) instanceof SymbolText found ? found : null;
        int index = existing == null ? -1 : requestedIndex;
        textIndex = -1;
        boolean followsName = existing != null && existing.text().isEmpty() && entered.isEmpty();
        if (entered.isBlank() && !followsName) {
            // A text left blank is no text: a new one is never added, an old one goes with this same edit.
            if (index >= 0) {
                publish.accept(current.get().select(Set.of(index)).deleteSelection());
            }
            requestFocusInWindow();
            repaint();
            return;
        }
        Optional<String> value = existing != null && entered.equals(existing.text().orElse(""))
                ? existing.text() : Optional.of(entered);
        SymbolText text = existing == null
                ? new SymbolText(anchor, value, TextSize.MEDIUM, TextAlignment.LEFT)
                : new SymbolText(anchor, value, existing.size(), existing.alignment(), existing.color(),
                existing.border());
        publish.accept(index >= 0 ? current.get().editText(index, text) : current.get().addText(text));
        requestFocusInWindow();
        repaint();
    }

    /** The inline editor of this canvas (its text field is the area of a request of several lines). */
    InlineTextEditor textEditor() {
        return textEditor;
    }

    /** What the shared inline editor needs of this canvas: where it floats and how the figure is mapped to pixels. */
    private final class ShapeHost implements InlineHost {
        @Override
        public JComponent component() {
            return ShapeCanvas.this;
        }

        @Override
        public Runnable addFloatingComponent(JComponent editor) {
            add(editor, 0);
            repaint();
            return () -> {
                Rectangle last = editor.getBounds();
                remove(editor);
                repaint(last);
            };
        }

        @Override
        public Runnable addViewportListener(Runnable listener) {
            return viewport.addChangeListener(listener);
        }

        @Override
        public Runnable addChangeListener(Runnable listener) {
            return () -> { };
        }

        @Override
        public AffineTransform documentTransform() {
            return viewport.documentTransform();
        }

        @Override
        public double zoom() {
            return viewport.zoom();
        }
    }

    /** Chooses the node under the pointer, if any. */
    private boolean hitNode(Point at) {
        Optional<NodeHit> node = nodeAt(at);
        node.ifPresent(hit -> {
            pathIndex = hit.pathIndex();
            nodeIndex = hit.nodeIndex();
        });
        return node.isPresent();
    }

    private int hitFigure(Point at) {
        double x = localX(at);
        double y = localY(at);
        List<SymbolElement> elements = current.get().elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            SymbolElement element = elements.get(index);
            if (element instanceof SymbolPath path) {
                Shape outline = SymbolPainter.path(path);
                if (path.filled() && outline.contains(x, y)
                        || new BasicStroke((float) (2 * HIT_RADIUS / zoom())).createStrokedShape(outline)
                        .contains(x, y)) {
                    return index;
                }
            } else if (element instanceof SymbolText text && paddedTextBlock(text).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    /** The block a text paints, with a little padding: its selection rectangle and its click area (local pixels). */
    Rectangle2D paddedTextBlock(SymbolText text) {
        Rectangle2D block = SymbolPainter.textBlock(text, definition.name());
        double padding = TEXT_BLOCK_PADDING / zoom();
        return new Rectangle2D.Double(block.getX() - padding, block.getY() - padding, block.getWidth() + 2 * padding,
                block.getHeight() + 2 * padding);
    }

    /** The pin under {@code at}, if any. */
    private Optional<ElementId> pinAt(Point at) {
        return current.get().appearance().pins().stream().filter(placement ->
                screen(placement.offset().x() * 8, placement.offset().y() * 8).distance(at) < HIT_RADIUS)
                .map(PinPlacement::source).findFirst();
    }

    /** True when every selected element is a text: its selection is its block, not a box with handles. */
    boolean onlyTextsSelected() {
        SymbolDraft view = current.get();
        return !view.selection().isEmpty() && view.selection().stream()
                .allMatch(index -> index < view.elements().size() && view.elements().get(index) instanceof SymbolText);
    }

    private int hitSegment(int index, Point at) {
        SymbolPath path = (SymbolPath) current.get().elements().get(index);
        return ShapeSegments.find(List.of(path), localX(at), localY(at), HIT_RADIUS / zoom() / UNIT * 8)
                .map(ShapeSegments.Hit::segmentIndex).orElse(-1);
    }

    private Optional<ShapeSegments.Hit> segmentAt(Point at) {
        return ShapeSegments.find(current.get().elements(), localX(at), localY(at), HIT_RADIUS / zoom() / UNIT * 8);
    }

    /** {@code point} in eighths of a grid unit, which are also the figure's local pixels ({@code UNIT} is 8). */
    private double localX(Point point) {
        return viewport.screenToGrid(point.x, point.y).getX() * 8;
    }

    private double localY(Point point) {
        return viewport.screenToGrid(point.x, point.y).getY() * 8;
    }

    private Optional<SymbolDraft.Handle> hitHandle(Point at) {
        if (onlyTextsSelected()) {
            return Optional.empty();
        }
        return current.get().selectionBounds().flatMap(box -> {
            for (SymbolDraft.Handle handle : SymbolDraft.Handle.values()) {
                SymbolPoint point = handlePoint(handle, box);
                if (screen(point.x(), point.y()).distance(at) < HIT_RADIUS) {
                    return Optional.of(handle);
                }
            }
            return Optional.empty();
        });
    }

    private Point boxHandleLocation() {
        Point corner = screen(current.get().appearance().size().width() * 8,
                current.get().appearance().size().height() * 8);
        return new Point(corner.x + BOX_HANDLE_OFFSET, corner.y + BOX_HANDLE_OFFSET);
    }

    private void pointerMoved(MouseEvent event) {
        pointer = event.getPoint();
        shiftHeld = event.isShiftDown();
        if (pathDrawing.lineAnchor != null) {
            repaint();
        }
        boolean wasOverBoxHandle = overBoxHandle;
        overBoxHandle = tool == ShapeTool.SELECT && !current.get().pinsLocked()
                && hitHandle(event.getPoint()).isEmpty()
                && boxHandleLocation().distance(event.getPoint()) < HIT_RADIUS;
        if (overBoxHandle != wasOverBoxHandle) {
            changed.run();
        }
        Optional<ElementId> wasHoveredPin = hoveredPin;
        int wasHoveredText = hoveredText;
        hoveredPin = tool == ShapeTool.SELECT ? pinAt(event.getPoint()) : Optional.empty();
        hoveredText = tool == ShapeTool.SELECT && hoveredPin.isEmpty() ? hoveredTextAt(event.getPoint()) : -1;
        if (!hoveredPin.equals(wasHoveredPin) || hoveredText != wasHoveredText) {
            repaint();
        }
        if (isNodeTool()) {
            controlHeld = event.isControlDown();
            hoverNodeTool(event.getPoint());
            repaint();
        } else if (tool == ShapeTool.CURVE) {
            hoverCurve(event);
        } else if (isPathTool()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText(ShapeEditorTexts.hint(new ShapeEditorTexts.HintState(tool, false, false, 0, false,
                    false)));
            repaint();
        } else if (tool == ShapeTool.PAN) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(Messages.text("shape.pan.hint"));
        } else if (tool == ShapeTool.NODES) {
            boolean onHandle = hoverHandle(event.getPoint());
            setCursor(Cursor.getPredefinedCursor(onHandle ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR));
            setToolTipText(Messages.text(onHandle ? "shape.handle.hint" : "shape.nodes.hint"));
            if (refusal.isPresent() && nodeAt(event.getPoint()).isEmpty()) {
                refusal = Optional.empty();
                changed.run();
            }
        } else if (hoveredPin.isPresent()) {
            setCursor(Cursor.getPredefinedCursor(current.get().pinsLocked() ? Cursor.DEFAULT_CURSOR
                    : Cursor.MOVE_CURSOR));
            setToolTipText(pinHover(hoveredPin.get()));
        } else if (tool == ShapeTool.SELECT && hitHandle(event.getPoint()).isPresent()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
            setToolTipText(Messages.text("shape.figureResize.hint"));
        } else if (overBoxHandle) {
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
            setToolTipText(Messages.text("shape.boxResize.hint"));
        } else {
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(Messages.text("shape.canvas.hint"));
        }
    }

    private int hoveredTextAt(Point at) {
        int figure = hitFigure(at);
        return figure >= 0 && current.get().elements().get(figure) instanceof SymbolText ? figure : -1;
    }

    /** «A · entrada»: the pin's name and its direction. */
    String pinHover(ElementId source) {
        String direction = definition.pinout().stream().filter(candidate -> candidate.source().equals(source))
                .findFirst().map(found -> Messages.text(MESSAGE_PREFIX + "direction."
                        + found.spec().direction().name().toLowerCase(Locale.ROOT))).orElse("");
        return Messages.format("shape.pin.hover", pinName(source), direction);
    }

    /** The pin under the pointer, when the select tool is hovering one. */
    Optional<ElementId> hoveredPin() {
        return hoveredPin;
    }

    /**
     * «Curvar tramo»: a handle under the pointer grows and offers to be dragged; otherwise the segment under it lights
     * up and the hand offers a click.
     */
    private void hoverCurve(MouseEvent event) {
        boolean onHandle = hoverHandle(event.getPoint());
        curveTarget = onHandle ? Optional.empty() : curveTargetAt(event.getPoint());
        setCursor(Cursor.getPredefinedCursor(onHandle || curveTarget.isPresent()
                ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        setToolTipText(Messages.text(onHandle ? "shape.handle.hint" : "shape.hint.curve"));
        repaint();
    }

    /** Notes the curve handle under the pointer (repainting when it changes) and says whether there is one. */
    private boolean hoverHandle(Point at) {
        Optional<HandleRef> under = grabbableHandleAt(at);
        if (!under.equals(hoveredHandle)) {
            hoveredHandle = under;
            repaint();
        }
        return under.isPresent();
    }

    static SymbolPoint handlePoint(SymbolDraft.Handle handle, SymbolDraft.Box box) {
        return new SymbolPoint(switch (handle) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> box.left();
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> box.right();
            default -> (box.left() + box.right()) / 2;
        }, switch (handle) {
            case TOP_LEFT, TOP, TOP_RIGHT -> box.top();
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> box.bottom();
            default -> (box.top() + box.bottom()) / 2;
        });
    }

    SymbolPoint point(MouseEvent event) {
        return point(event.getPoint());
    }

    SymbolPoint point(Point at) {
        SymbolPoint raw = rawPoint(at);
        return new SymbolPoint(Math.clamp(raw.x(), 0, current.get().appearance().size().width() * 8),
                Math.clamp(raw.y(), 0, current.get().appearance().size().height() * 8));
    }
    private SymbolPoint rawPoint(Point at) {
        return new SymbolPoint(snap.snap((int) Math.round(localX(at))), snap.snap((int) Math.round(localY(at))));
    }
    Point screen(int x, int y) {
        Point2D screen = viewport.gridToScreen(x / 8.0, y / 8.0);
        return new Point((int) Math.round(screen.getX()), (int) Math.round(screen.getY()));
    }

    /** Pins that share their connection point, each with the name of another pin on the same point. */
    List<Coincidence> coincidences() {
        List<PinPlacement> placements = current.get().appearance().pins();
        List<Coincidence> found = new ArrayList<>();
        for (PinPlacement placement : placements) {
            placements.stream().filter(other -> !other.source().equals(placement.source())
                            && other.offset().equals(placement.offset())).findFirst()
                    .ifPresent(other -> found.add(new Coincidence(placement.source(), placement.offset(),
                            pinName(other.source()))));
        }
        return found;
    }

    /** «Ya hay un pin aquí (A)» for the pin being dragged or selected, when it sits on another one. */
    Optional<String> coincidenceCallout() {
        return pin.flatMap(selected -> coincidences().stream().filter(found -> found.pin().equals(selected)).findFirst())
                .map(found -> Messages.format("shape.pin.coincides", found.otherName()));
    }

    /** «Op · entrada · 2 bits» for the selected pin. */
    Optional<String> pinCallout() {
        return pin.flatMap(selected -> definition.pinout().stream().filter(candidate -> candidate.source().equals(selected))
                .findFirst()).map(found -> Messages.format("shape.pin.row", found.spec().name(),
                Messages.text(MESSAGE_PREFIX + "direction." + found.spec().direction().name().toLowerCase(Locale.ROOT)),
                found.spec().width()));
    }

    /** Why the last click did nothing («una figura cerrada necesita al menos 3 nodos»), until the pointer moves on. */
    Optional<String> refusal() {
        return refusal;
    }

    /** The live size of the box or figure being dragged, with the proportion hint when Shift applies. */
    Optional<String> dragTip() {
        return dragTip;
    }

    /** «(x, y)» of the selected node (or curve handle) in grid units. */
    Optional<String> nodeLabel() {
        if (tool != ShapeTool.NODES && tool != ShapeTool.ADD_NODE || !nodeSelected()) {
            return Optional.empty();
        }
        SymbolPath path = (SymbolPath) current.get().elements().get(pathIndex);
        SymbolPoint point = chosenPoint(path);
        String coordinates = "(" + units(point.x()) + ", " + units(point.y()) + ")";
        if (!path.hasTwoHandles(nodeIndex)) {
            return Optional.of(coordinates);
        }
        String kind = Messages.text(current.get().smooth(pathIndex, nodeIndex) ? "shape.node.smooth" : "shape.node.corner");
        return Optional.of(Messages.format("shape.node.label", coordinates, kind));
    }

    String pinName(ElementId source) {
        return definition.pinout().stream().filter(candidate -> candidate.source().equals(source)).findFirst()
                .map(found -> found.spec().name()).orElse(source.value());
    }

    /** «4 × 2» and, below, what Shift does to a figure. */
    private Optional<String> figureTip(SymbolDraft view) {
        return view.selectionBounds().map(box -> sizeTip(box.width() / 8.0, box.height() / 8.0) + "\n"
                + Messages.text("shape.drag.shift"));
    }

    /** «8 × 6» of the box and, below, what Ctrl does to the drawing. */
    private String boxTip(SymbolDraft resized) {
        return sizeTip(resized.appearance().size().width(), resized.appearance().size().height()) + "\n"
                + Messages.text("shape.drag.ctrl");
    }

    private static String sizeTip(double width, double height) {
        return Messages.format("shape.drag.size", units(width), units(height));
    }

    private static String units(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Messages.locale());
        format.setMaximumFractionDigits(3);
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private static String units(int smallUnits) {
        return units(smallUnits / 8.0);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (fitPending && (getWidth() != fittedWidth || getHeight() != fittedHeight)) {
            boolean pending = fitPending;
            fitBox();
            fitPending = pending;
        }
        painter.paint(graphics);
    }

    /** Every whole grid point on the box edge: where pins usually go and where the outline should pass. */
    List<GridPoint> edgeNotches() {
        GridSize size = current.get().appearance().size();
        List<GridPoint> notches = new ArrayList<>();
        for (int column = 0; column <= size.width(); column++) {
            notches.add(new GridPoint(column, 0));
            notches.add(new GridPoint(column, size.height()));
        }
        for (int row = 1; row < size.height(); row++) {
            notches.add(new GridPoint(0, row));
            notches.add(new GridPoint(size.width(), row));
        }
        return notches;
    }

    /** Whether a pin is being dragged right now (the pin guide is shown only then). */
    boolean pinGuideActive() {
        return pinDragActive && pin.isPresent();
    }

    /** The points of the box another pin occupies while one is dragged; none when no pin is dragged. */
    List<GridPoint> occupiedPinPoints() {
        if (!pinGuideActive()) {
            return List.of();
        }
        return current.get().appearance().pins().stream().filter(placement -> !placement.source().equals(pin.get()))
                .map(PinPlacement::offset).distinct().toList();
    }

    /** The whole grid points inside the box where the dragged pin can go free; none when no pin is dragged. */
    List<GridPoint> freePinPoints() {
        if (!pinGuideActive()) {
            return List.of();
        }
        GridSize size = current.get().appearance().size();
        List<GridPoint> occupied = occupiedPinPoints();
        List<GridPoint> free = new ArrayList<>();
        for (int row = 0; row <= size.height(); row++) {
            for (int column = 0; column <= size.width(); column++) {
                GridPoint candidate = new GridPoint(column, row);
                if (!occupied.contains(candidate)) {
                    free.add(candidate);
                }
            }
        }
        return free;
    }

    /** The free point nearest to the pointer: where the pin would land. */
    Optional<GridPoint> nearestFreePinPoint() {
        Point2D grid = viewport.screenToGrid(pointer.x, pointer.y);
        double pointerX = grid.getX();
        double pointerY = grid.getY();
        return freePinPoints().stream().min(Comparator.comparingDouble(
                candidate -> Math.hypot(candidate.x() - pointerX, candidate.y() - pointerY)));
    }

    static void paintAppearance(Graphics2D graphics, ComponentDefinition definition, ComponentAppearance appearance,
                                ColorScheme scheme, boolean editing) {
        paintAppearance(graphics, definition, appearance, scheme, editing, 1.6f, 1);
    }

    /**
     * With {@code strokeWidth} and {@code pinScale} in local pixels: a zoomed view keeps them constant on screen
     * (texts grow with the zoom).
     */
    static void paintAppearance(Graphics2D graphics, ComponentDefinition definition, ComponentAppearance appearance,
                                ColorScheme scheme, boolean editing, float strokeWidth, double pinScale) {
        ComponentType original = SubcircuitTypes.typeOf(definition);
        List<PinSpec> pins = new ArrayList<>();
        for (DefinitionPin pin : definition.pinout()) {
            GridPoint position = appearance.pins().stream().filter(placement -> placement.source().equals(pin.source()))
                    .findFirst().map(PinPlacement::offset).orElse(pin.spec().position());
            pins.add(new PinSpec(pin.spec().name(), position, pin.spec().direction(), pin.spec().width()));
        }
        ComponentType type = new ComponentType(original.id(), original.category(), appearance.size(), pins,
                List.of(), original.behaviorFactory());
        if (appearance.drawing().isPresent()) {
            SymbolDrawing drawing = appearance.drawing().get();
            SymbolPainter.paint(graphics, type, editing
                    ? new SymbolDrawing(drawing.size(), drawing.elements(), true) : drawing, scheme, definition.name(),
                    strokeWidth, pinScale);
        } else {
            SubcircuitPainter.paintDefaultBox(graphics, type, scheme, strokeWidth, pinScale);
        }
    }
}
