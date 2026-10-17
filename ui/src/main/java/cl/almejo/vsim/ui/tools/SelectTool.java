package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.canvas.SelectionBounds;
import cl.almejo.vsim.ui.wiring.MovePreview;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Everyday tool: select (Shift/Ctrl+click toggles, drag in empty space for a rubber band), move the selection by dragging any selected element, draw
 * wires by dragging from a pin or an unselected wire, toggle switches, context menu, double click to configure.
 *
 * <p>What a plain primary press grabs, in priority order: a pin (within the pin radius), then an unselected wire,
 * then the body of an element or an already selected element. Exception: a pin of a selected component grabs the
 * whole selection (instead of starting a wire) while the selection has more than one element. A press on an
 * element of a multi-selection that is released without moving (a plain click) narrows the selection to that
 * element; a press-and-drag moves the whole group. Ctrl held when a move is released copies the selection there
 * instead (one undo step).
 *
 * <p>M6.7: a move drags the wires attached to the selection when the follow mode, inverted while the configured key
 * is held, says so; the keys are read during the drag (the preview) and at the release (the move). A key held at the
 * press keeps its old meaning on a click (Shift toggles the selection); a Shift press that drags moves the selection
 * (adding the pressed element) with the mode inverted.
 *
 * <p>M6.7 §8: a press on the one selected wire that drags with the segment key held (Alt, or Shift when Alt is the
 * invert key; read during the drag and at the release) moves that wire sideways only, perpendicular to itself, and
 * the wires joined to its ends always stretch (the follow mode and the invert key do not apply).
 *
 * <p>{@link SelectToolDelegate}s (M6.3: frames) are asked first: a press one of them takes, with its drag and
 * release, is theirs alone.
 */
public final class SelectTool implements Tool {

    private static final double HOVER_RING_RADIUS = 4.5;
    private static final BasicStroke HOVER_RING = new BasicStroke(1.5f);
    private static final Font HOVER_TAG_FONT = LabelLayout.FONT.deriveFont(Font.BOLD, 9f);
    private static final double HOVER_TAG_OFFSET = 7;
    private static final double BAND_THRESHOLD = 0.5;
    private static final float BAND_WIDTH = 1f;
    private static final int BAND_FILL_ALPHA = 40;

    private final Map<String, Boolean> interactiveByType = new HashMap<>();
    private final List<SelectToolDelegate> delegates = new CopyOnWriteArrayList<>();
    private SelectToolDelegate activeDelegate;
    private ConnectionPoints connectionPoints;
    private int pinSearches;
    private DocumentElement pressedElement;
    private GridPoint pressPoint;
    private WireDrawing wire;
    private GridPoint hoverPin;
    private int hoverWidth = 1;
    private Point2D bandStart;
    private Point2D bandEnd;
    private boolean bandAdditive;
    private boolean ownPressSeen;
    private boolean narrowOnRelease;
    /** The press was a Shift click on an element: a release without moving only adjusts the selection. */
    private boolean selectionClick;
    /** That Shift click was on an element already selected: a release without moving takes it out. */
    private boolean removeOnRelease;
    private int deltaX;
    private int deltaY;
    /** The drag's delta before the locked-frame walls: what a Ctrl copy uses, as copies leave the originals. */
    private int copyDeltaX;
    private int copyDeltaY;
    private boolean followWires;
    /** The drag moves a single selected wire sideways (the segment key is held). */
    private boolean segmentDrag;
    /** The last drag event of a move, so a modifier key pressed or released without moving can redo it. */
    private CanvasMouseEvent lastDrag;
    private final MovePreview movePreview = new MovePreview();
    private CanvasCursor cursor = CanvasCursor.DEFAULT;

    /** Adds a delegate asked before the tool's own behaviour; the returned runnable removes it. */
    public Runnable addDelegate(SelectToolDelegate delegate) {
        delegates.add(delegate);
        return () -> delegates.remove(delegate);
    }

    @Override
    public void activated(ToolContext context) {
        ownPressSeen = false;
        cursor = CanvasCursor.DEFAULT; // the canvas shows cursor(), the arrow, on activation
    }

    @Override
    public void deactivated(ToolContext context) {
        cancelDelegate(context);
        hoverPin = null;
        hoverWidth = 1;
        reset();
        context.repaint();
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (delegatePress(context, event)) {
            return;
        }
        press(context, event);
        pressCursor().ifPresent(next -> showCursor(context, next));
    }

    /**
     * True when a delegate took the press. A primary press a delegate grabs makes it the active delegate until the
     * release; one it only handled leaves no pending state, so a lost release (a modal editor opened by a double
     * click) never blocks the next press.
     */
    private boolean delegatePress(ToolContext context, CanvasMouseEvent event) {
        if (activeDelegate != null) {
            return true; // another press while the delegate's press is held belongs to it
        }
        for (SelectToolDelegate delegate : delegates) {
            SelectToolDelegate.Press answer = delegate.pressed(context, event);
            if (answer == SelectToolDelegate.Press.IGNORED) {
                continue;
            }
            if (event.button() == CanvasMouseEvent.PRIMARY) {
                reset();
                ownPressSeen = true;
                if (answer == SelectToolDelegate.Press.GRABBED) {
                    activeDelegate = delegate;
                    hoverPin = null;
                    showCursor(context, delegate.pressCursor());
                }
            }
            return true;
        }
        return false;
    }

    private void cancelDelegate(ToolContext context) {
        if (activeDelegate != null) {
            SelectToolDelegate cancelled = activeDelegate;
            activeDelegate = null;
            cancelled.cancelled(context);
        }
    }

    private void press(ToolContext context, CanvasMouseEvent event) {
        Optional<DocumentElement> hit = context.hitTester()
                .elementAt(context.openDocument().document(), event.gridPosition());
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            hit.ifPresentOrElse(element -> {
                if (!context.selection().contains(element.id())) {
                    context.selection().select(element.id());
                }
            }, () -> context.selection().clear());
            context.showContextMenu(hit, event.screenPosition());
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        reset();
        boolean continuesOwnClick = ownPressSeen;
        ownPressSeen = true;
        if (event.clickCount() >= 2 && continuesOwnClick) {
            Optional<Component> labelled = componentLabelAt(context, event.gridPosition());
            if (labelled.isPresent()) {
                context.selection().select(labelled.get().id());
                context.editLabel(labelled.get());
                return;
            }
        }
        if (event.clickCount() >= 2 && continuesOwnClick && hit.isPresent()) {
            if (hit.get() instanceof Component component) {
                context.selection().select(component.id());
                if (component.typeId().equals(StandardComponentTypes.CONSTANT)) {
                    context.editValue(component);
                } else {
                    context.configure(component);
                }
                return;
            }
            if (hit.get() instanceof TextLabel label) {
                context.selection().select(label.id());
                context.editLabel(label);
                return;
            }
        }
        // A free text is drawn on top of everything, so a press on it never reaches a pin underneath.
        Optional<ConnectionPoints.PinHit> pin = hit.isPresent() && hit.get() instanceof TextLabel
                ? Optional.empty()
                : pinNear(context, event.gridPosition());
        if (event.shiftDown() || event.controlDown()) {
            Optional<DocumentElement> target = pin.<DocumentElement>map(ConnectionPoints.PinHit::component).or(() -> hit);
            if (target.isPresent() && !event.controlDown()) {
                // Shift+click toggles the element; Shift+drag moves the selection with it (the key inverts the mode)
                pressedElement = target.get();
                selectionClick = true;
                if (context.selection().contains(target.get().id())) {
                    removeOnRelease = true;
                } else {
                    context.selection().add(target.get().id());
                }
                pressPoint = event.snapped();
                return;
            }
            if (target.isPresent()) {
                context.selection().toggle(target.get().id());
            } else {
                startBand(event, true);
            }
            return;
        }
        if (pin.isPresent()) {
            pressedElement = pin.get().component();
            if (grabsSelection(context, pin.get().component())) {
                pressPoint = event.snapped();
                narrowOnRelease = true;
            } else {
                wire = new WireDrawing(pin.get().point(), event.gridPosition(),
                        connectionPoints(context).widthAt(context.openDocument().document(), pin.get().point()));
            }
            return;
        }
        pressedElement = hit.orElse(null);
        if (hit.isPresent() && hit.get() instanceof Wire pressedWire
                && !context.selection().contains(pressedWire.id())) {
            wire = new WireDrawing(ConnectionPoints.pointOnWire(pressedWire, event.gridPosition()),
                    event.gridPosition());
            return;
        }
        if (hit.isEmpty()) {
            startBand(event, false);
            return;
        }
        if (context.selection().contains(hit.get().id())) {
            narrowOnRelease = context.selection().size() > 1;
        } else {
            context.selection().select(hit.get().id());
        }
        pressPoint = event.snapped();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (activeDelegate != null) {
            activeDelegate.dragged(context, event);
            return;
        }
        if (bandStart != null) {
            bandEnd = event.gridPosition();
            context.repaint();
            return;
        }
        if (wire != null) {
            wire.dragTo(event);
            context.repaint();
            return;
        }
        if (pressPoint == null) {
            return;
        }
        lastDrag = event;
        dragTo(context, event);
        // Ctrl at the release copies, and a copy never drags wires: the preview must not show them following
        followWires = !event.controlDown()
                && (segmentDrag || context.selectionMoves().follows(event.shiftDown(), event.altDown()));
        showCursor(context, segmentDrag ? segmentWire(context).map(SelectTool::slideCursor).orElse(CanvasCursor.MOVE)
                : CanvasCursor.MOVE);
        context.repaint();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        if (activeDelegate != null) {
            SelectToolDelegate released = activeDelegate;
            activeDelegate = null;
            released.released(context, event);
            updateHover(context, event);
            context.repaint();
            return;
        }
        if (bandStart != null) {
            finishBand(context);
        } else if (wire != null) {
            boolean added = wire.hasMoved() && wire.commit(context, event.snapped());
            if (!added) {
                click(context);
            }
        } else if (pressPoint != null) {
            if (deltaX != 0 || deltaY != 0 || copyDeltaX != 0 || copyDeltaY != 0) {
                if (segmentWire(context).isPresent()) {
                    dragTo(context, event); // the segment key at the release decides the direction
                }
                if (!context.selection().isEmpty()) {
                    if (event.controlDown()) {
                        copySelection(context);
                    } else if (segmentDrag) {
                        context.selectionMoves().moveSegment(context.openDocument(), pressedElement.id(), deltaX,
                                deltaY);
                    } else {
                        context.selectionMoves().move(context.openDocument(), context.selection().effectiveIds(),
                                deltaX, deltaY, context.selectionMoves().follows(event.shiftDown(), event.altDown()));
                    }
                }
            } else if (selectionClick) {
                if (removeOnRelease) {
                    context.selection().remove(pressedElement.id());
                }
            } else {
                if (narrowOnRelease) {
                    context.selection().select(pressedElement.id());
                }
                toggleIfInteractive(context);
            }
        }
        reset();
        updateHover(context, event);
        context.repaint();
    }

    /**
     * The delta of the drag; a move (not a Ctrl copy) stops at the border of a locked frame it does not carry. A
     * segment drag keeps only the part of the delta perpendicular to the wire.
     */
    private void dragTo(ToolContext context, CanvasMouseEvent event) {
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
        Optional<Wire> segment = segmentWire(context)
                .filter(wire -> context.selectionMoves().segmentKeyHeld(event.shiftDown(), event.altDown()));
        segmentDrag = segment.isPresent();
        if (segmentDrag) {
            if (segment.get().start().y() == segment.get().end().y()) {
                deltaX = 0;
            } else {
                deltaY = 0;
            }
        }
        copyDeltaX = deltaX;
        copyDeltaY = deltaY;
        if (!event.controlDown() && !context.selection().isEmpty()) {
            GridPoint limited = SelectionMoves.limited(context.openDocument(), context.selection().effectiveIds(),
                    deltaX, deltaY);
            deltaX = limited.x();
            deltaY = limited.y();
        }
    }

    /**
     * The wire a segment drag would move: the press was on a horizontal or vertical wire that is, alone, the whole
     * selection.
     */
    private Optional<Wire> segmentWire(ToolContext context) {
        if (!(pressedElement instanceof Wire pressedWire)
                || !context.selection().effectiveIds().equals(Set.of(pressedWire.id()))) {
            return Optional.empty();
        }
        // every wire can slide: the Wire constructor already rejects zero-length and diagonal wires
        return context.openDocument().document().wire(pressedWire.id());
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        updateHover(context, event);
    }

    @Override
    public void mouseExited(ToolContext context) {
        delegates.forEach(delegate -> delegate.exited(context));
        if (hoverPin != null) {
            hoverPin = null;
            context.repaint();
        }
    }

    private void updateHover(ToolContext context, CanvasMouseEvent event) {
        Optional<CanvasCursor> delegateCursor = Optional.empty();
        for (SelectToolDelegate delegate : delegates) {
            Optional<CanvasCursor> answer = delegate.moved(context, event);
            if (delegateCursor.isEmpty()) {
                delegateCursor = answer;
            }
        }
        if (delegateCursor.isPresent()) {
            // a press here goes to the delegate, never to a pin
            if (hoverPin != null) {
                hoverPin = null;
                hoverWidth = 1;
                context.repaint();
            }
            showCursor(context, delegateCursor.get());
            return;
        }
        Optional<ConnectionPoints.PinHit> pin = pinNear(context, event.gridPosition());
        GridPoint nearest = pin.map(ConnectionPoints.PinHit::point).orElse(null);
        if (!Objects.equals(nearest, hoverPin)) {
            hoverPin = nearest;
            hoverWidth = nearest == null ? 1
                    : connectionPoints(context).widthAt(context.openDocument().document(), nearest);
            context.repaint();
        }
        showCursor(context, cursorAt(context, event.gridPosition(), pin, event));
    }

    /** The cursor of a segment drag of {@code wire}: it slides across its own direction. */
    private static CanvasCursor slideCursor(Wire wire) {
        return wire.start().y() == wire.end().y() ? CanvasCursor.SLIDE_VERTICAL : CanvasCursor.SLIDE_HORIZONTAL;
    }

    /**
     * What a plain primary press at {@code gridPosition} would do, as a cursor: the priority of {@link #press}.
     * {@code nearestPin} is the pin nearest to {@code gridPosition}, if any.
     */
    private CanvasCursor cursorAt(ToolContext context, Point2D gridPosition,
                                  Optional<ConnectionPoints.PinHit> nearestPin, CanvasMouseEvent event) {
        CircuitDocument document = context.openDocument().document();
        Optional<DocumentElement> hit = context.hitTester().elementAt(document, gridPosition);
        // A free text is drawn on top of everything, so it hides a pin underneath.
        Optional<ConnectionPoints.PinHit> pin = hit.isPresent() && hit.get() instanceof TextLabel
                ? Optional.empty()
                : nearestPin;
        if (pin.isPresent()) {
            return grabsSelection(context, pin.get().component()) ? CanvasCursor.MOVE : CanvasCursor.CROSSHAIR;
        }
        if (hit.isEmpty()) {
            return CanvasCursor.DEFAULT;
        }
        DocumentElement element = hit.get();
        boolean selected = context.selection().contains(element.id());
        if (element instanceof Wire && !selected) {
            return CanvasCursor.CROSSHAIR;
        }
        if (element instanceof Wire hoveredWire && context.selection().effectiveIds().equals(Set.of(hoveredWire.id()))
                && context.selectionMoves().segmentKeyHeld(event.shiftDown(), event.altDown())) {
            return slideCursor(hoveredWire);
        }
        if (element instanceof Component component && isInteractive(context, component)) {
            return CanvasCursor.HAND;
        }
        return selected ? CanvasCursor.MOVE : CanvasCursor.DEFAULT;
    }

    /** While a press drags: a wire (crosshair), a rubber band (arrow) or a move (move). */
    private Optional<CanvasCursor> pressCursor() {
        if (wire != null) {
            return Optional.of(CanvasCursor.CROSSHAIR);
        }
        if (bandStart != null) {
            return Optional.of(CanvasCursor.DEFAULT);
        }
        if (pressPoint != null) {
            return Optional.of(CanvasCursor.MOVE);
        }
        return Optional.empty();
    }

    private void showCursor(ToolContext context, CanvasCursor next) {
        if (next != cursor) {
            cursor = next;
            context.setCursor(next);
        }
    }

    /**
     * During a move (or a delegate's drag), redoes the drag at the last pointer position with the new keys: the
     * preview, the hidden wires, the segment mode and the cursor follow Shift, Ctrl and Alt at once.
     */
    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        if (activeDelegate != null) {
            return activeDelegate.modifiersChanged(context, shiftDown, controlDown, altDown);
        }
        if (lastDrag == null || pressPoint == null || wire != null || bandStart != null) {
            return false;
        }
        mouseDragged(context, lastDrag.withModifiers(shiftDown, controlDown, altDown));
        return true;
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (activeDelegate != null) {
            cancelDelegate(context);
            context.repaint();
            return true;
        }
        if (pressPoint != null || wire != null || bandStart != null) {
            reset();
            context.repaint();
        } else {
            context.selection().clear();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        for (SelectToolDelegate delegate : delegates) {
            delegate.paintOverlay(graphics, context);
        }
        Rectangle2D band = rubberBand();
        if (band != null) {
            paintBand(graphics, context, band);
            return;
        }
        if (hoverPin != null && wire == null) {
            paintHover(graphics, context);
        }
        if (wire != null) {
            wire.paintPreview(graphics, context);
            return;
        }
        if (pressPoint == null) {
            return;
        }
        movePreview.paint(graphics, context, context.selection().effectiveIds(), deltaX, deltaY, followWires);
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        if (activeDelegate != null) {
            return activeDelegate.hiddenWires(context);
        }
        if (pressPoint == null || wire != null || bandStart != null || !followWires) {
            return Set.of();
        }
        return movePreview.hiddenWires(context, context.selection().effectiveIds(), deltaX, deltaY);
    }

    /** A circle around a 1-bit pin; a square of the same footprint, tagged with the width, around a bus pin. */
    private void paintHover(Graphics2D graphics, ToolContext context) {
        double unit = GridPoint.PIXELS_PER_UNIT;
        double centerX = hoverPin.x() * unit;
        double centerY = hoverPin.y() * unit;
        graphics.setColor(context.scheme().selection());
        graphics.setStroke(HOVER_RING);
        if (hoverWidth <= 1) {
            graphics.draw(new Ellipse2D.Double(centerX - HOVER_RING_RADIUS, centerY - HOVER_RING_RADIUS,
                    2 * HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS));
            return;
        }
        graphics.draw(new Rectangle2D.Double(centerX - HOVER_RING_RADIUS, centerY - HOVER_RING_RADIUS,
                2 * HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS));
        graphics.setFont(HOVER_TAG_FONT);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        PainterSupport.paintTag(graphics, graphics.getFontMetrics(), String.valueOf(hoverWidth),
                centerX + HOVER_TAG_OFFSET, centerY - HOVER_TAG_OFFSET + 2, context.scheme().selection(),
                context.scheme().background());
    }

    @Override
    public boolean animatesOutline() {
        return bandStart != null;
    }

    GridPoint hoverPin() {
        return hoverPin;
    }

    /** The rubber band in grid units, or {@code null} when none is being dragged. */
    Rectangle2D rubberBand() {
        if (bandStart == null) {
            return null;
        }
        return new Rectangle2D.Double(Math.min(bandStart.getX(), bandEnd.getX()),
                Math.min(bandStart.getY(), bandEnd.getY()),
                Math.abs(bandEnd.getX() - bandStart.getX()), Math.abs(bandEnd.getY() - bandStart.getY()));
    }

    private void startBand(CanvasMouseEvent event, boolean additive) {
        bandStart = event.gridPosition();
        bandEnd = event.gridPosition();
        bandAdditive = additive;
    }

    /** A band smaller than half a grid unit is a click: it clears the selection unless it was additive. */
    private void finishBand(ToolContext context) {
        Rectangle2D band = rubberBand();
        if (Math.max(band.getWidth(), band.getHeight()) < BAND_THRESHOLD) {
            if (!bandAdditive) {
                context.selection().clear();
            }
            return;
        }
        Set<ElementId> inside = elementsInside(context, band);
        if (bandAdditive) {
            Set<ElementId> combined = new LinkedHashSet<>(context.selection().ids());
            combined.addAll(inside);
            context.selection().set(combined);
        } else {
            context.selection().set(inside);
        }
    }

    private static Set<ElementId> elementsInside(ToolContext context, Rectangle2D band) {
        Set<ElementId> inside = new LinkedHashSet<>();
        for (DocumentElement element : context.openDocument().document().elements()) {
            Bounds bounds = ElementBounds.of(element, context.openDocument().registry());
            if (bounds.x() >= band.getMinX() && bounds.maximumX() <= band.getMaxX()
                    && bounds.y() >= band.getMinY() && bounds.maximumY() <= band.getMaxY()) {
                inside.add(element.id());
            }
        }
        return inside;
    }

    private static void paintBand(Graphics2D graphics, ToolContext context, Rectangle2D band) {
        double unit = GridPoint.PIXELS_PER_UNIT;
        Rectangle2D pixels = new Rectangle2D.Double(band.getX() * unit, band.getY() * unit,
                band.getWidth() * unit, band.getHeight() * unit);
        Color color = context.scheme().selection();
        graphics.setColor(PainterSupport.withAlpha(color, BAND_FILL_ALPHA));
        graphics.fill(pixels);
        MarchingAnts.draw(graphics, pixels, color, BAND_WIDTH, context.selectionDashPhase());
    }

    /** A pin of a selected component moves the selection when the selection holds more than one element. */
    private static boolean grabsSelection(ToolContext context, Component component) {
        return context.selection().contains(component.id()) && context.selection().size() > 1;
    }

    /**
     * The snapped point where the current press on a selected element started, while that press may still become a
     * move of the selection; empty with no press, while drawing a wire or while dragging a rubber band. The canvas
     * uses it to start a drag out.
     */
    public Optional<GridPoint> movingFrom() {
        return wire == null && bandStart == null ? Optional.ofNullable(pressPoint) : Optional.empty();
    }

    /** True when no gesture is under way: no press, wire, rubber band or delegate (a frame resize, a label drag). */
    public boolean isIdle() {
        return pressPoint == null && wire == null && bandStart == null && activeDelegate == null;
    }

    /** Forgets the press in progress without changing the document: the drag goes on outside the canvas. */
    public void cancelMove(ToolContext context) {
        reset();
        context.repaint();
    }

    /**
     * True while a drag moves the selection, a rubber band or a delegate's press: the canvas scrolls when the pointer
     * nears its edge.
     */
    public boolean autoscrolls() {
        return bandStart != null || movingFrom().isPresent() || activeDelegate != null;
    }

    /** A press on a pin or wire that did not become a wire: behave like a plain click on what was under it. */
    private void click(ToolContext context) {
        if (pressedElement == null) {
            context.selection().clear();
            return;
        }
        context.selection().select(pressedElement.id());
        toggleIfInteractive(context);
    }

    /** Ctrl held at the release: the selection stays put and a copy lands where the move would have put it. */
    private void copySelection(ToolContext context) {
        Set<ElementId> ids = context.selection().effectiveIds();
        Optional<GridPoint> topLeft = SelectionBounds.topLeft(context.openDocument(), ids);
        if (topLeft.isEmpty()) {
            return;
        }
        CircuitFragment fragment = CircuitFragment.of(context.openDocument().document(), ids,
                context.openDocument().registry());
        PasteCommand paste = new PasteCommand(fragment, topLeft.get().translate(copyDeltaX, copyDeltaY));
        context.openDocument().execute(paste);
        context.selection().set(paste.pastedIds());
    }

    private void toggleIfInteractive(ToolContext context) {
        if (pressedElement instanceof Component component && isInteractive(context, component)) {
            context.openDocument().interact(component.id());
        }
    }

    /** The pin nearest to {@code gridPosition}: a scan of every pin of the document, counted for tests. */
    private Optional<ConnectionPoints.PinHit> pinNear(ToolContext context, Point2D gridPosition) {
        pinSearches++;
        return connectionPoints(context).pinNear(context.openDocument().document(), gridPosition);
    }

    /** For tests: how many times the nearest pin was looked for. */
    int pinSearches() {
        return pinSearches;
    }

    private ConnectionPoints connectionPoints(ToolContext context) {
        if (connectionPoints == null) {
            connectionPoints = new ConnectionPoints(context.openDocument().registry());
        }
        return connectionPoints;
    }

    /** The component whose drawn label holds {@code gridPosition}, if any (the topmost one when labels overlap). */
    private static Optional<Component> componentLabelAt(ToolContext context, Point2D gridPosition) {
        double documentX = gridPosition.getX() * GridPoint.PIXELS_PER_UNIT;
        double documentY = gridPosition.getY() * GridPoint.PIXELS_PER_UNIT;
        List<Component> components = context.openDocument().document().components();
        for (int index = components.size() - 1; index >= 0; index--) {
            Component component = components.get(index);
            boolean onLabel = LabelLayout.componentLabel(context.openDocument().registry().typeOf(component), component)
                    .map(label -> label.bounds().contains(documentX, documentY))
                    .orElse(false);
            if (onLabel) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }

    private boolean isInteractive(ToolContext context, Component component) {
        return interactiveByType.computeIfAbsent(component.typeId(), typeId -> context.openDocument().registry()
                .require(typeId).createBehavior(component.parameters()).isInteractive());
    }

    private void reset() {
        pressedElement = null;
        pressPoint = null;
        narrowOnRelease = false;
        selectionClick = false;
        removeOnRelease = false;
        wire = null;
        bandStart = null;
        bandEnd = null;
        bandAdditive = false;
        deltaX = 0;
        deltaY = 0;
        copyDeltaX = 0;
        copyDeltaY = 0;
        followWires = false;
        segmentDrag = false;
        lastDrag = null;
        movePreview.clear();
    }
}
