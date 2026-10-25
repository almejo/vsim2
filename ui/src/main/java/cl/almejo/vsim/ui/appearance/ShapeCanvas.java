package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.DraftProblem;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SnapMode;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
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
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.SubcircuitPainter;
import cl.almejo.vsim.ui.paint.SymbolPainter;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.theme.ReadableColors;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.undo.UndoManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shape-editing gestures and paint; every completed gesture publishes one immutable draft. */
@SuppressWarnings("serial")
final class ShapeCanvas extends JPanel {
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final String MESSAGE_PREFIX = "shape.";
    private static final int HIT_RADIUS = 7;
    private static final int BOX_HANDLE_OFFSET = 18;
    private static final int FIT_SIDE_MARGIN = 64;
    private static final int FIT_VERTICAL_MARGIN = 64;
    /** About 32 px per grid unit: the box looks comfortable, as in the mockup, however small it is. */
    private static final double FIT_MAXIMUM_ZOOM = 4;
    /** On open the box takes about this part of each side of the canvas. */
    private static final double FIT_BOX_SHARE = 0.6;
    /** Figure strokes and pin markers keep this size on screen whatever the zoom. */
    private static final float SCREEN_STROKE_WIDTH = 1.6f;
    private static final double SCREEN_PIN_RADIUS = 3;
    private static final int NOTCH_LENGTH = 4;
    /** Shift and an arrow moves this many grid units, as in the main editor. */
    private static final int FAR_STEP = 5;
    private final ComponentDefinition definition;
    private final Supplier<SymbolDraft> current;
    private final Consumer<SymbolDraft> publish;
    private final Consumer<SymbolDraft> preview;
    private final Supplier<ColorScheme> colors;
    private final Runnable changed;
    private ShapeTool tool = ShapeTool.SELECT;
    private SnapMode snap = SnapMode.HALF_UNITS;
    private double zoom = 2;
    private double originX = 70;
    private double originY = 70;
    private Optional<ElementId> pin = Optional.empty();
    private int pathIndex = -1;
    private int nodeIndex = -1;
    private boolean control;
    private boolean nodeDrag;
    private Optional<ShapeSegments.Hit> insertion = Optional.empty();
    private Optional<SymbolDraft.Handle> handle = Optional.empty();
    private boolean resizingBox;
    private boolean pinDragActive;
    private boolean panning;
    private boolean rubberBand;
    private SymbolPoint bandEnd;
    private Point pressed;
    private SymbolPoint start;
    private SymbolDraft beforeDrag;
    private SymbolDraft committedBeforeDrag;
    private SymbolDraft dragging;
    private final List<SymbolPoint> polygon = new ArrayList<>();
    private JTextField textField;
    private int textIndex = -1;
    private boolean fitPending = true;
    private int fittedWidth = -1;
    private int fittedHeight = -1;
    private Point pointer = new Point();
    private Point lastDragPoint;
    private Optional<String> dragTip = Optional.empty();

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
                insertion = Optional.empty();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (event.isControlDown() || event.isMetaDown()) {
                    setZoom(zoom * Math.pow(1.15, -event.getPreciseWheelRotation()));
                } else {
                    fitPending = false;
                    originY -= event.getPreciseWheelRotation() * 24;
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
                }
            }
            @Override
            public void keyReleased(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftChanged(false);
                }
            }
        });
    }

    void setTool(ShapeTool selected) {
        cancelOperation();
        tool = selected;
        pin = Optional.empty();
        pathIndex = -1;
        nodeIndex = -1;
        insertion = Optional.empty();
        setCursor(selected == ShapeTool.PAN ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    void setSnap(boolean enabled) {
        snap = enabled ? SnapMode.HALF_UNITS : SnapMode.FREE;
    }

    void setZoom(double value) {
        fitPending = false;
        zoom = Math.clamp(value, 0.25, 6);
        changed.run();
        repaint();
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
        zoom = Math.clamp(Math.min(Math.min(availableWidth / boxWidth, availableHeight / boxHeight),
                Math.min(sharedWidth, sharedHeight)), 0.25, FIT_MAXIMUM_ZOOM);
        originX = (getWidth() - boxWidth * zoom) / 2;
        originY = (getHeight() - boxHeight * zoom) / 2;
        fittedWidth = getWidth();
        fittedHeight = getHeight();
        changed.run();
    }

    /** The box rectangle in canvas pixels. */
    Rectangle2D boxOnScreen() {
        GridSize size = current.get().appearance().size();
        return new Rectangle2D.Double(originX, originY, size.width() * UNIT * zoom, size.height() * UNIT * zoom);
    }

    double zoom() {
        return zoom;
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
        return pressed != null || !polygon.isEmpty() || textField != null;
    }

    boolean hasSelection() {
        return pin.isPresent() || !current.get().selection().isEmpty() || nodeIndex >= 0;
    }

    /** A node (not only a segment) is selected: the last node of an open path counts. */
    boolean nodeSelected() {
        SymbolDraft view = nodeView();
        if (pathIndex < 0 || nodeIndex < 0 || pathIndex >= view.elements().size()) {
            return false;
        }
        return view.elements().get(pathIndex) instanceof SymbolPath path && nodeIndex < path.nodes().size();
    }

    boolean segmentSelected() {
        SymbolDraft view = nodeView();
        if (pathIndex < 0 || nodeIndex < 0 || pathIndex >= view.elements().size()) {
            return false;
        }
        if (!(view.elements().get(pathIndex) instanceof SymbolPath path)) {
            return false;
        }
        return nodeIndex < path.nodes().size() - (path.closed() ? 0 : 1);
    }

    void draftChanged() {
        insertion = Optional.empty();
        // A text being typed was started on an earlier draft: its element may no longer exist (undo, toolbar edit).
        if (textField != null) {
            remove(textField);
            textField = null;
            textIndex = -1;
        }
        if (pathIndex < 0 || pathIndex >= current.get().elements().size()
                || !current.get().selection().contains(pathIndex)
                || !(current.get().elements().get(pathIndex) instanceof SymbolPath path)
                || nodeIndex >= path.nodes().size()) {
            pathIndex = -1;
            nodeIndex = -1;
            control = false;
            nodeDrag = false;
        }
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
        polygon.clear();
        pinDragActive = false;
        dragTip = Optional.empty();
        if (textField != null) {
            remove(textField);
            textField = null;
        }
        repaint();
    }

    void finishOperation() {
        if (textField != null) {
            finishText();
            return;
        }
        if (polygon.size() >= 2) {
            publish.accept(nodeView().addFigure(SymbolTemplates.polygon(List.copyOf(polygon), false)));
            polygon.clear();
            repaint();
        }
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
            SymbolNode node = ((SymbolPath) editing.elements().get(pathIndex)).nodes().get(nodeIndex);
            SymbolPoint point = control ? node.control().orElse(node.point()) : node.point();
            SymbolPoint moved = new SymbolPoint(point.x() + horizontal * eighths, point.y() + vertical * eighths);
            publish.accept(control ? editing.moveControl(pathIndex, nodeIndex, moved, snap)
                    : editing.moveNode(pathIndex, nodeIndex, moved, snap));
        } else {
            publish.accept(current.get().moveSelection(horizontal * eighths, vertical * eighths, snap));
        }
    }

    void toggleCurve() {
        if (segmentSelected()) {
            publish.accept(nodeDraft().toggleCurve(pathIndex, nodeIndex));
        }
    }

    /** The default box exposes editable nodes, but becomes a custom figure only on an actual edit. */
    private SymbolDraft nodeView() {
        return withBoxAsFigure(current.get());
    }

    /** The default box as the Rectángulo template (box, title and name stay); any other drawing as it is. */
    private static SymbolDraft withBoxAsFigure(SymbolDraft draft) {
        return draft.appearance().drawing().isEmpty() ? draft.applyTemplate(SymbolTemplates.Template.RECTANGLE) : draft;
    }

    private SymbolDraft nodeDraft() {
        return nodeView().select(Set.of(pathIndex));
    }

    private void press(MouseEvent event) {
        if (textField != null) {
            finishText();
        }
        pointer = event.getPoint();
        dragTip = Optional.empty();
        requestFocusInWindow();
        pressed = event.getPoint();
        start = point(event);
        beforeDrag = current.get();
        dragging = null;
        committedBeforeDrag = beforeDrag;
        panning = tool == ShapeTool.PAN || SwingUtilities.isMiddleMouseButton(event) || event.isAltDown();
        if (panning || !SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        if (tool == ShapeTool.POLYGON || tool == ShapeTool.TEXT || tool == ShapeTool.ADD_NODE) {
            return;
        }
        handle = Optional.empty();
        resizingBox = false;
        nodeDrag = false;
        rubberBand = false;
        if (tool == ShapeTool.SELECT) {
            handle = hitHandle(event.getPoint());
            resizingBox = handle.isEmpty() && !current.get().pinsLocked()
                    && boxHandleLocation().distance(event.getPoint()) < HIT_RADIUS;
            if (handle.isPresent() || resizingBox) {
                return;
            }
            pin = current.get().appearance().pins().stream().filter(placement ->
                    screen(placement.offset().x() * 8, placement.offset().y() * 8).distance(event.getPoint()) < HIT_RADIUS)
                    .map(PinPlacement::source).findFirst();
            if (pin.isPresent()) {
                changed.run();
                repaint();
                return;
            }
        }
        if (tool == ShapeTool.NODES) {
            if (hitNode(event.getPoint())) {
                nodeDrag = true;
                beforeDrag = nodeDraft();
                if (current.get().appearance().drawing().isPresent()) {
                    publish.accept(beforeDrag);
                }
                changed.run();
                repaint();
                return;
            }
        }
        if (tool == ShapeTool.SELECT || tool == ShapeTool.NODES) {
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
            SymbolDraft view = tool == ShapeTool.NODES ? nodeView() : current.get();
            beforeDrag = view.select(selection);
            if (current.get().appearance().drawing().isPresent() || tool != ShapeTool.NODES) {
                publish.accept(beforeDrag);
            }
            if (tool == ShapeTool.NODES && hit >= 0 && view.elements().get(hit) instanceof SymbolPath) {
                pathIndex = hit;
                nodeIndex = hitSegment(hit, event.getPoint());
                control = false;
            }
        }
    }

    private void drag(MouseEvent event) {
        drag(event.getPoint(), event.isShiftDown());
    }

    /** Shift pressed or released in the middle of a drag: the move is worked out again at the same pointer. */
    private void shiftChanged(boolean shift) {
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
            originX += where.x - pressed.x;
            originY += where.y - pressed.y;
            pressed = where;
            repaint();
            return;
        }
        if (tool == ShapeTool.POLYGON || tool == ShapeTool.TEXT || tool == ShapeTool.ADD_NODE) {
            return;
        }
        SymbolPoint end = point(where);
        dragTip = Optional.empty();
        try {
            if (resizingBox) {
                GridSize minimum = beforeDrag.minimumBoxSize();
                int width = beforeDrag.appearance().size().width()
                        + (int) Math.round((where.x - pressed.x) / zoom / UNIT);
                int height = beforeDrag.appearance().size().height()
                        + (int) Math.round((where.y - pressed.y) / zoom / UNIT);
                dragging = beforeDrag.resizeBox(new GridSize(Math.max(minimum.width(), width),
                        Math.max(minimum.height(), height)));
                dragTip = Optional.of(sizeTip(dragging.appearance().size().width(),
                        dragging.appearance().size().height(), false));
            } else if (handle.isPresent()) {
                dragging = beforeDrag.scaleSelection(handle.get(), end, shift, snap);
                dragTip = figureTip(dragging);
            } else if (pin.isPresent() && tool == ShapeTool.SELECT) {
                if (beforeDrag.pinsLocked()) {
                    return;
                }
                pinDragActive = true;
                GridPoint target = new GridPoint((int) Math.round((where.x - originX) / zoom / UNIT),
                        (int) Math.round((where.y - originY) / zoom / UNIT));
                if (shift) {
                    GridPoint original = beforeDrag.appearance().pins().stream()
                            .filter(placement -> placement.source().equals(pin.get())).findFirst().orElseThrow().offset();
                    boolean horizontal = Math.abs(target.x() - original.x()) >= Math.abs(target.y() - original.y());
                    target = horizontal ? new GridPoint(target.x(), original.y()) : new GridPoint(original.x(), target.y());
                }
                dragging = beforeDrag.movePin(pin.get(), target);
                dragTip = Optional.of(Messages.text("shape.drag.axis"));
            } else if (tool == ShapeTool.NODES && nodeDrag && nodeIndex >= 0) {
                SymbolNode grabbed = ((SymbolPath) beforeDrag.elements().get(pathIndex)).nodes().get(nodeIndex);
                SymbolPoint original = control ? grabbed.control().orElse(grabbed.point()) : grabbed.point();
                SymbolPoint target = shift ? axisLocked(original, end) : end;
                dragging = control ? beforeDrag.moveControl(pathIndex, nodeIndex, target, snap)
                        : beforeDrag.moveNode(pathIndex, nodeIndex, target, snap);
                dragTip = Optional.of(Messages.text("shape.drag.axis"));
            } else if (rubberBand) {
                bandEnd = end;
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
            } else if (tool == ShapeTool.SELECT || tool == ShapeTool.NODES) {
                SymbolPoint moved = shift ? axisLocked(start, end) : end;
                dragging = beforeDrag.moveSelection(moved.x() - start.x(), moved.y() - start.y(), snap);
                dragTip = Optional.of(Messages.text("shape.drag.axis"));
            } else {
                SymbolPath figure = switch (tool) {
                    case LINE -> SymbolTemplates.line(start, end, shift);
                    case RECTANGLE -> SymbolTemplates.rectangle(start, end, shift);
                    case ELLIPSE -> SymbolTemplates.ellipse(start, end, shift);
                    case TRIANGLE -> SymbolTemplates.triangle(start, end, shift);
                    default -> throw new IllegalStateException("Not a figure tool");
                };
                dragging = withBoxAsFigure(beforeDrag).addFigure(figure);
                dragTip = figureTip(dragging);
            }
            preview.accept(dragging);
            repaint();
        } catch (IllegalArgumentException exception) {
            // A constrained figure at the box edge can be invalid until the pointer returns inside.
            preview.accept(dragging == null ? beforeDrag : dragging);
        }
    }

    /** {@code target} kept only along the axis it is farthest from {@code origin} (horizontal on a tie). */
    private static SymbolPoint axisLocked(SymbolPoint origin, SymbolPoint target) {
        return Math.abs(target.x() - origin.x()) >= Math.abs(target.y() - origin.y())
                ? new SymbolPoint(target.x(), origin.y()) : new SymbolPoint(origin.x(), target.y());
    }

    private void release(MouseEvent event) {
        if (pressed == null) {
            return;
        }
        SymbolDraft completed = dragging;
        SymbolDraft original = committedBeforeDrag;
        dragTip = Optional.empty();
        pinDragActive = false;
        pressed = null;
        lastDragPoint = null;
        beforeDrag = null;
        committedBeforeDrag = null;
        dragging = null;
        if (completed != null && original != null) {
            preview.accept(original);
            publish.accept(completed);
        }
        repaint();
    }

    private void click(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) || tool == ShapeTool.PAN || event.isAltDown()) {
            return;
        }
        if (tool == ShapeTool.ADD_NODE) {
            segmentAt(event.getPoint()).ifPresent(hit -> {
                SymbolDraft editing = nodeView().select(Set.of(hit.pathIndex()));
                SymbolPath path = (SymbolPath) editing.elements().get(hit.pathIndex());
                if (path.nodes().size() >= SymbolPath.MAXIMUM_NODES) {
                    setToolTipText(Messages.text("shape.nodes.limit"));
                    return;
                }
                pathIndex = hit.pathIndex();
                nodeIndex = hit.segmentIndex() + 1;
                control = false;
                insertion = Optional.empty();
                publish.accept(editing.insertNode(hit.pathIndex(), hit.segmentIndex(), hit.point()));
            });
            repaint();
        } else if (tool == ShapeTool.POLYGON) {
            if (polygon.size() >= 3 && (event.getClickCount() > 1
                    || screen(polygon.getFirst().x(), polygon.getFirst().y()).distance(event.getPoint()) < HIT_RADIUS)) {
                publish.accept(nodeView().addFigure(SymbolTemplates.polygon(List.copyOf(polygon), true)));
                polygon.clear();
            } else if (event.getClickCount() == 1) {
                polygon.add(point(event));
            }
            repaint();
        } else if (tool == ShapeTool.TEXT) {
            beginText(event.getPoint(), -1);
        }
        else if (event.getClickCount() == 2) {
            int figure = hitFigure(event.getPoint());
            SymbolDraft view = tool == ShapeTool.NODES ? nodeView() : current.get();
            if (figure >= 0 && view.elements().get(figure) instanceof SymbolText
                    && current.get().appearance().drawing().isPresent()) {
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

    private void beginText(Point location, int index) {
        SymbolText existing = index >= 0 && current.get().elements().get(index) instanceof SymbolText found ? found : null;
        textIndex = existing == null ? -1 : index;
        textField = new JTextField(existing == null ? "" : existing.text().orElse(""));
        textField.putClientProperty("shape.anchor", existing == null ? point(location) : existing.anchor());
        textField.setBounds(location.x, location.y - 12, 160, 26);
        textField.addActionListener(event -> finishText());
        bindTextUndo(textField);
        add(textField);
        textField.requestFocusInWindow();
        textField.selectAll();
        repaint();
    }

    /**
     * Ctrl/Cmd+Z, Y and D belong to the text while it is being typed: the dialog binds them on an ancestor, which would
     * otherwise undo or duplicate the draft under the field.
     */
    private static void bindTextUndo(JTextField field) {
        UndoManager history = new UndoManager();
        field.getDocument().addUndoableEditListener(history);
        for (String modifier : new String[]{"ctrl", "meta"}) {
            bindText(field, modifier + " Z", "undo", () -> {
                if (history.canUndo()) {
                    history.undo();
                }
            });
            bindText(field, modifier + " shift Z", "redo", () -> redo(history));
            bindText(field, modifier + " Y", "redo", () -> redo(history));
            bindText(field, modifier + " D", "swallow", () -> { });
        }
    }

    private static void redo(UndoManager history) {
        if (history.canRedo()) {
            history.redo();
        }
    }

    private static void bindText(JTextField field, String key, String name, Runnable action) {
        String binding = "shape.text." + name + "." + key;
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), binding);
        field.getActionMap().put(binding, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void finishText() {
        if (textField == null) {
            return;
        }
        SymbolPoint anchor = (SymbolPoint) textField.getClientProperty("shape.anchor");
        SymbolText existing = textIndex >= 0 && textIndex < current.get().elements().size()
                && current.get().elements().get(textIndex) instanceof SymbolText found ? found : null;
        int index = existing == null ? -1 : textIndex;
        String entered = textField.getText();
        Optional<String> value = existing != null && entered.equals(existing.text().orElse(""))
                ? existing.text() : Optional.of(entered);
        SymbolText text = new SymbolText(anchor, value, existing == null ? TextSize.MEDIUM : existing.size(),
                existing == null ? TextAlignment.LEFT : existing.alignment());
        remove(textField);
        textField = null;
        publish.accept(index >= 0 ? current.get().editText(index, text) : nodeView().addText(text));
        requestFocusInWindow();
        repaint();
    }

    private boolean hitNode(Point at) {
        List<SymbolElement> elements = nodeView().elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (!(elements.get(index) instanceof SymbolPath path)) {
                continue;
            }
            for (int node = 0; node < path.nodes().size(); node++) {
                SymbolNode candidate = path.nodes().get(node);
                if (candidate.control().isPresent()) {
                    SymbolPoint point = candidate.control().get();
                    if (screen(point.x(), point.y()).distance(at) < HIT_RADIUS) {
                        pathIndex = index;
                        nodeIndex = node;
                        control = true;
                        return true;
                    }
                }
                if (screen(candidate.point().x(), candidate.point().y()).distance(at) < HIT_RADIUS) {
                    pathIndex = index;
                    nodeIndex = node;
                    control = false;
                    return true;
                }
            }
        }
        return false;
    }

    private int hitFigure(Point at) {
        double x = (at.x - originX) / zoom;
        double y = (at.y - originY) / zoom;
        List<SymbolElement> elements = (tool == ShapeTool.NODES ? nodeView() : current.get()).elements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            SymbolElement element = elements.get(index);
            if (element instanceof SymbolPath path) {
                Shape outline = SymbolPainter.path(path);
                if (path.filled() && outline.contains(x, y)
                        || new BasicStroke((float) (2 * HIT_RADIUS / zoom)).createStrokedShape(outline).contains(x, y)) {
                    return index;
                }
            } else if (element instanceof SymbolText text) {
                Point anchor = screen(text.anchor().x(), text.anchor().y());
                if (new Rectangle2D.Double(anchor.x - 40 * zoom, anchor.y - 10 * zoom,
                        80 * zoom, 20 * zoom).contains(at)) {
                            return index;
                        }
            }
        }
        return -1;
    }

    private int hitSegment(int index, Point at) {
        SymbolPath path = (SymbolPath) nodeView().elements().get(index);
        return ShapeSegments.find(List.of(path), localX(at), localY(at), HIT_RADIUS / zoom / UNIT * 8)
                .map(ShapeSegments.Hit::segmentIndex).orElse(-1);
    }

    private Optional<ShapeSegments.Hit> segmentAt(Point at) {
        return ShapeSegments.find(nodeView().elements(), localX(at), localY(at), HIT_RADIUS / zoom / UNIT * 8);
    }

    private double localX(Point point) {
        return (point.x - originX) / zoom / UNIT * 8;
    }

    private double localY(Point point) {
        return (point.y - originY) / zoom / UNIT * 8;
    }

    private Optional<SymbolDraft.Handle> hitHandle(Point at) {
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
        if (tool == ShapeTool.ADD_NODE) {
            insertion = segmentAt(event.getPoint());
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText(Messages.text("shape.nodeAdd.hint"));
            repaint();
        } else if (tool == ShapeTool.PAN) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(Messages.text("shape.pan.hint"));
        } else if (tool == ShapeTool.NODES) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText(Messages.text("shape.nodes.hint"));
        } else if (tool == ShapeTool.SELECT && hitHandle(event.getPoint()).isPresent()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
            setToolTipText(Messages.text("shape.figureResize.hint"));
        } else if (tool == ShapeTool.SELECT && !current.get().pinsLocked()
                && boxHandleLocation().distance(event.getPoint()) < HIT_RADIUS) {
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
            setToolTipText(Messages.text("shape.boxResize.hint"));
        } else {
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(Messages.text("shape.canvas.hint"));
        }
    }

    private static SymbolPoint handlePoint(SymbolDraft.Handle handle, SymbolDraft.Box box) {
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

    private SymbolPoint point(MouseEvent event) {
        return point(event.getPoint());
    }

    private SymbolPoint point(Point at) {
        SymbolPoint raw = rawPoint(at);
        return new SymbolPoint(Math.clamp(raw.x(), 0, current.get().appearance().size().width() * 8),
                Math.clamp(raw.y(), 0, current.get().appearance().size().height() * 8));
    }
    private SymbolPoint rawPoint(Point at) {
        return new SymbolPoint(snap.snap((int) Math.round((at.x - originX) / zoom / UNIT * 8)),
                snap.snap((int) Math.round((at.y - originY) / zoom / UNIT * 8)));
    }
    private Point screen(int x, int y) {
        return new Point((int) Math.round(originX + x / 8.0 * UNIT * zoom),
                (int) Math.round(originY + y / 8.0 * UNIT * zoom));
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

    /** The live size of the box or figure being dragged, with the proportion hint when Shift applies. */
    Optional<String> dragTip() {
        return dragTip;
    }

    /** «(x, y)» of the selected node (or control point) in grid units. */
    Optional<String> nodeLabel() {
        if (tool != ShapeTool.NODES && tool != ShapeTool.ADD_NODE || !nodeSelected()) {
            return Optional.empty();
        }
        SymbolNode node = ((SymbolPath) nodeView().elements().get(pathIndex)).nodes().get(nodeIndex);
        SymbolPoint point = control ? node.control().orElse(node.point()) : node.point();
        return Optional.of("(" + units(point.x()) + ", " + units(point.y()) + ")");
    }

    private String pinName(ElementId source) {
        return definition.pinout().stream().filter(candidate -> candidate.source().equals(source)).findFirst()
                .map(found -> found.spec().name()).orElse(source.value());
    }

    private Optional<String> figureTip(SymbolDraft view) {
        return view.selectionBounds().map(box -> sizeTip(box.width() / 8.0, box.height() / 8.0, true));
    }

    private static String sizeTip(double width, double height, boolean proportion) {
        String size = Messages.format("shape.drag.size", units(width), units(height));
        return proportion ? size + "\n" + Messages.text("shape.drag.shift") : size;
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
        Graphics2D surface = (Graphics2D) graphics.create();
        try {
            ColorScheme scheme = colors.get();
            surface.setColor(scheme.background());
            surface.fillRect(0, 0, getWidth(), getHeight());
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double spacing = UNIT * zoom;
            if (spacing >= 4) {
                surface.setColor(scheme.grid());
                for (double x = originX % spacing; x < getWidth(); x += spacing) {
                    for (double y = originY % spacing; y < getHeight(); y += spacing) {
                        surface.fill(new Ellipse2D.Double(x - 0.8, y - 0.8, 1.6, 1.6));
                    }
                }
            }
            ComponentAppearance appearance = current.get().appearance();
            surface.translate(originX, originY);
            surface.scale(zoom, zoom);
            paintAppearance(surface, definition, appearance, scheme, true, (float) (SCREEN_STROKE_WIDTH / zoom),
                    SCREEN_PIN_RADIUS / (PainterSupport.PIN_DOT_RADIUS * zoom));
            paintGuides(surface, scheme, appearance.size());
            surface.setColor(PainterSupport.withAlpha(scheme.text(), 130));
            surface.setStroke(new BasicStroke((float) (1 / zoom), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[]{(float) (5 / zoom)}, 0));
            surface.draw(new Rectangle2D.Double(0, 0, appearance.size().width() * UNIT, appearance.size().height() * UNIT));
            surface.setFont(TextFonts.labelFont().deriveFont((float) (10 / zoom)));
            surface.drawString(Messages.text("shape.box.label"), 0, (float) (-10 / zoom));
            if (!current.get().pinsLocked()) {
                double right = appearance.size().width() * UNIT;
                double bottom = appearance.size().height() * UNIT;
                double offset = BOX_HANDLE_OFFSET / zoom;
                surface.draw(new Line2D.Double(right, bottom, right + offset, bottom + offset));
                surface.fill(new Ellipse2D.Double(right + offset - 3 / zoom,
                        bottom + offset - 3 / zoom, 6 / zoom, 6 / zoom));
            }
            surface.setColor(scheme.selection());
            if (tool == ShapeTool.SELECT) {
                current.get().selectionBounds().ifPresent(box -> paintSelection(surface, box));
            }
            for (PinPlacement placement : appearance.pins()) {
                if (pin.filter(placement.source()::equals).isPresent()) {
                    surface.draw(new Ellipse2D.Double(placement.offset().x() * UNIT - 5 / zoom,
                            placement.offset().y() * UNIT - 5 / zoom, 10 / zoom, 10 / zoom));
                }
            }
            paintCoincidences(surface, scheme);
            paintOffEdgePins(surface);
            if (tool == ShapeTool.ADD_NODE) {
                paintInsertion(surface);
            }
            if (tool == ShapeTool.NODES || tool == ShapeTool.ADD_NODE) {
                paintNodes(surface);
            }
            if (pressed != null && rubberBand && bandEnd != null) {
                surface.draw(new Rectangle2D.Double(Math.min(start.x(), bandEnd.x()) / 8.0 * UNIT,
                        Math.min(start.y(), bandEnd.y()) / 8.0 * UNIT, Math.abs(start.x() - bandEnd.x()) / 8.0 * UNIT,
                        Math.abs(start.y() - bandEnd.y()) / 8.0 * UNIT));
            }
            for (int index = 1; index < polygon.size(); index++) {
                SymbolPoint first = polygon.get(index - 1);
                SymbolPoint second = polygon.get(index);
                surface.draw(new Line2D.Double(first.x() / 8.0 * UNIT, first.y() / 8.0 * UNIT,
                        second.x() / 8.0 * UNIT, second.y() / 8.0 * UNIT));
            }
        } finally {
            surface.dispose();
        }
        Graphics2D overlay = (Graphics2D) graphics.create();
        try {
            overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            overlay.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintCallouts(overlay, colors.get());
        } finally {
            overlay.dispose();
        }
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
        double pointerX = (pointer.x - originX) / zoom / UNIT;
        double pointerY = (pointer.y - originY) / zoom / UNIT;
        return freePinPoints().stream().min(Comparator.comparingDouble(
                candidate -> Math.hypot(candidate.x() - pointerX, candidate.y() - pointerY)));
    }

    /** Notches on the box edge, and while a pin is dragged its free and occupied points; the colours are the scheme's. */
    private void paintGuides(Graphics2D surface, ColorScheme scheme, GridSize size) {
        Graphics2D guide = (Graphics2D) surface.create();
        try {
            guide.setStroke(new BasicStroke((float) (1.5 / zoom)));
            guide.setColor(PainterSupport.withAlpha(scheme.text(), 150));
            double notch = NOTCH_LENGTH / zoom;
            for (GridPoint point : edgeNotches()) {
                double x = point.x() * UNIT;
                double y = point.y() * UNIT;
                boolean onSide = point.x() == 0 || point.x() == size.width();
                boolean corner = onSide && (point.y() == 0 || point.y() == size.height());
                if (onSide || corner) {
                    guide.draw(new Line2D.Double(x - notch, y, x + notch, y));
                }
                if (!onSide || corner) {
                    guide.draw(new Line2D.Double(x, y - notch, x, y + notch));
                }
            }
            Optional<GridPoint> nearest = nearestFreePinPoint();
            for (GridPoint point : freePinPoints()) {
                boolean strong = nearest.filter(point::equals).isPresent();
                double radius = (strong ? 5 : 2) / zoom;
                guide.setColor(strong ? scheme.selection() : PainterSupport.withAlpha(scheme.selection(), 140));
                guide.setStroke(new BasicStroke((float) ((strong ? 2 : 1.2) / zoom)));
                Ellipse2D ring = new Ellipse2D.Double(point.x() * UNIT - radius, point.y() * UNIT - radius,
                        2 * radius, 2 * radius);
                if (strong) {
                    guide.draw(ring);
                } else {
                    guide.fill(ring);
                }
            }
            guide.setColor(scheme.wireConflict());
            guide.setStroke(new BasicStroke((float) (1.5 / zoom)));
            double radius = 5 / zoom;
            double slash = radius * 0.7;
            for (GridPoint point : occupiedPinPoints()) {
                double x = point.x() * UNIT;
                double y = point.y() * UNIT;
                guide.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
                guide.draw(new Line2D.Double(x - slash, y + slash, x + slash, y - slash));
            }
        } finally {
            guide.dispose();
        }
    }

    /** A red ring and dot on every pin that shares its point: the icon and the callout carry the meaning too. */
    private void paintCoincidences(Graphics2D surface, ColorScheme scheme) {
        Map<GridPoint, Boolean> marked = new HashMap<>();
        for (Coincidence found : coincidences()) {
            if (marked.put(found.offset(), true) != null) {
                continue;
            }
            double x = found.offset().x() * UNIT;
            double y = found.offset().y() * UNIT;
            surface.setColor(scheme.wireConflict());
            surface.setStroke(new BasicStroke((float) (1.5 / zoom)));
            surface.draw(new Ellipse2D.Double(x - 8 / zoom, y - 8 / zoom, 16 / zoom, 16 / zoom));
            surface.fill(new Ellipse2D.Double(x - 4 / zoom, y - 4 / zoom, 8 / zoom, 8 / zoom));
        }
    }

    /** An amber ring on every pin that is not on the box edge: the row's triangle and the warning line say the rest. */
    private void paintOffEdgePins(Graphics2D surface) {
        Set<String> offEdge = new HashSet<>();
        for (DraftProblem problem : current.get().problems()) {
            if (problem.kind() == DraftProblem.Kind.PIN_OFF_EDGE) {
                offEdge.addAll(problem.pins());
            }
        }
        surface.setColor(ReadableColors.warningText());
        surface.setStroke(new BasicStroke((float) (1.5 / zoom)));
        for (PinPlacement placement : current.get().appearance().pins()) {
            if (offEdge.contains(pinName(placement.source()))) {
                surface.draw(new Ellipse2D.Double(placement.offset().x() * UNIT - 7 / zoom,
                        placement.offset().y() * UNIT - 7 / zoom, 14 / zoom, 14 / zoom));
            }
        }
    }

    private void paintCallouts(Graphics2D overlay, ColorScheme scheme) {
        Optional<String> coincidence = coincidenceCallout();
        if (coincidence.isPresent()) {
            PinPlacement selected = current.get().appearance().pins().stream()
                    .filter(placement -> pin.filter(placement.source()::equals).isPresent()).findFirst().orElseThrow();
            paintCallout(overlay, screen(selected.offset().x() * 8, selected.offset().y() * 8),
                    new String[]{coincidence.get()}, scheme.wireConflict(), true, true);
        } else if (pinCallout().isPresent()) {
            PinPlacement selected = current.get().appearance().pins().stream()
                    .filter(placement -> pin.filter(placement.source()::equals).isPresent()).findFirst().orElseThrow();
            paintCallout(overlay, screen(selected.offset().x() * 8, selected.offset().y() * 8),
                    new String[]{pinCallout().get()}, scheme.selection(), false, true);
        }
        dragTip.ifPresent(tip -> paintCallout(overlay, pointer,
                tip.split("\n"), scheme.selection(), false, false));
        nodeLabel().ifPresent(label -> {
            SymbolNode node = ((SymbolPath) nodeView().elements().get(pathIndex)).nodes().get(nodeIndex);
            SymbolPoint point = control ? node.control().orElse(node.point()) : node.point();
            Point at = screen(point.x(), point.y());
            overlay.setFont(TextFonts.labelFont().deriveFont(Font.BOLD, 13f));
            FontMetrics metrics = overlay.getFontMetrics();
            int width = metrics.stringWidth(label) + 10;
            int x = Math.min(at.x + 12, Math.max(0, getWidth() - width - 2));
            int y = Math.max(metrics.getHeight() + 2, at.y - 10);
            overlay.setColor(PainterSupport.withAlpha(scheme.background(), 220));
            overlay.fill(new RoundRectangle2D.Double(x - 4, y - metrics.getAscent() - 1, width, metrics.getHeight() + 2, 6, 6));
            overlay.setColor(scheme.text());
            overlay.drawString(label, x, y);
        });
    }

    /**
     * A rounded label next to {@code anchor}, kept inside the canvas. With {@code warning} it starts with a drawn
     * «no entry» sign, so the meaning never depends on its colour alone.
     */
    private void paintCallout(Graphics2D overlay, Point anchor, String[] lines, Color fill, boolean warning,
                              boolean above) {
        overlay.setFont(TextFonts.labelFont().deriveFont(Font.BOLD, 13f));
        FontMetrics metrics = overlay.getFontMetrics();
        int iconSize = warning ? 14 : 0;
        int iconGap = warning ? 6 : 0;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int width = textWidth + iconSize + iconGap + 16;
        int height = metrics.getHeight() * lines.length + 10;
        int x = Math.clamp(anchor.x + 16, 2, Math.max(2, getWidth() - width - 2));
        int y = Math.clamp(above ? anchor.y - 18 - height : anchor.y + 18, 2, Math.max(2, getHeight() - height - 2));
        overlay.setColor(fill);
        overlay.fill(new RoundRectangle2D.Double(x, y, width, height, 8, 8));
        Color ink = readableOn(fill);
        overlay.setColor(ink);
        int textX = x + 8;
        if (warning) {
            int iconY = y + (height - iconSize) / 2;
            overlay.setStroke(new BasicStroke(2f));
            overlay.draw(new Ellipse2D.Double(textX + 1, iconY + 1, iconSize - 2, iconSize - 2));
            overlay.draw(new Line2D.Double(textX + 4, iconY + iconSize / 2.0, textX + iconSize - 4, iconY + iconSize / 2.0));
            textX += iconSize + iconGap;
        }
        for (int line = 0; line < lines.length; line++) {
            overlay.drawString(lines[line], textX, y + 5 + metrics.getAscent() + line * metrics.getHeight());
        }
    }

    private static Color readableOn(Color fill) {
        double luminance = (0.2126 * fill.getRed() + 0.7152 * fill.getGreen() + 0.0722 * fill.getBlue()) / 255;
        return luminance < 0.6 ? Color.WHITE : Color.BLACK;
    }

    private void paintSelection(Graphics2D surface, SymbolDraft.Box box) {
        surface.draw(new Rectangle2D.Double(box.left() / 8.0 * UNIT, box.top() / 8.0 * UNIT,
                box.width() / 8.0 * UNIT, box.height() / 8.0 * UNIT));
        for (SymbolDraft.Handle handle : SymbolDraft.Handle.values()) {
            SymbolPoint point = handlePoint(handle, box);
            surface.fill(new Rectangle2D.Double(point.x() / 8.0 * UNIT - 3 / zoom,
                    point.y() / 8.0 * UNIT - 3 / zoom, 6 / zoom, 6 / zoom));
        }
    }

    private void paintNodes(Graphics2D surface) {
        List<SymbolElement> elements = nodeView().elements();
        surface.setStroke(new BasicStroke((float) (1.5 / zoom)));
        for (int index = 0; index < elements.size(); index++) {
            if (!(elements.get(index) instanceof SymbolPath path)) {
                continue;
            }
            for (int nodeNumber = 0; nodeNumber < path.nodes().size(); nodeNumber++) {
                SymbolNode node = path.nodes().get(nodeNumber);
                double x = node.point().x() / 8.0 * UNIT;
                double y = node.point().y() / 8.0 * UNIT;
                Shape marker = new Ellipse2D.Double(x - 4 / zoom, y - 4 / zoom, 8 / zoom, 8 / zoom);
                surface.setColor(index == pathIndex && nodeNumber == nodeIndex && !control
                        ? colors.get().selection() : colors.get().background());
                surface.fill(marker);
                surface.setColor(colors.get().componentStroke());
                surface.draw(marker);
                node.control().ifPresent(point -> {
                    double controlX = point.x() / 8.0 * UNIT;
                    double controlY = point.y() / 8.0 * UNIT;
                    surface.draw(new Line2D.Double(x, y, controlX, controlY));
                    Path2D diamond = new Path2D.Double();
                    diamond.moveTo(controlX, controlY - 4 / zoom);
                    diamond.lineTo(controlX + 4 / zoom, controlY);
                    diamond.lineTo(controlX, controlY + 4 / zoom);
                    diamond.lineTo(controlX - 4 / zoom, controlY);
                    diamond.closePath();
                    surface.setColor(colors.get().background());
                    surface.fill(diamond);
                    surface.setColor(colors.get().componentStroke());
                    surface.draw(diamond);
                });
            }
        }
    }

    private void paintInsertion(Graphics2D surface) {
        insertion.ifPresent(hit -> {
            SymbolPath path = (SymbolPath) nodeView().elements().get(hit.pathIndex());
            Shape segment = AffineTransform.getScaleInstance(UNIT / 8, UNIT / 8)
                    .createTransformedShape(ShapeSegments.shape(path, hit.segmentIndex()));
            surface.setColor(colors.get().selection());
            surface.setStroke(new BasicStroke((float) (3 / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            surface.draw(segment);
            surface.fill(new Ellipse2D.Double(hit.point().x() / 8.0 * UNIT - 3 / zoom,
                    hit.point().y() / 8.0 * UNIT - 3 / zoom, 6 / zoom, 6 / zoom));
        });
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
