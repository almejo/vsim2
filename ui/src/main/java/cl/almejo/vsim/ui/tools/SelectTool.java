package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ElementBounds;
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
    private int deltaX;
    private int deltaY;
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
        if (event.clickCount() >= 2 && continuesOwnClick && hit.isPresent()) {
            if (hit.get() instanceof Component component) {
                context.selection().select(component.id());
                context.configure(component);
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
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
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
            if (deltaX != 0 || deltaY != 0) {
                if (!context.selection().isEmpty()) {
                    if (event.controlDown()) {
                        copySelection(context);
                    } else {
                        context.openDocument().execute(
                                new MoveElementsCommand(context.selection().ids(), deltaX, deltaY));
                    }
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
        showCursor(context, cursorAt(context, event.gridPosition(), pin));
    }

    /**
     * What a plain primary press at {@code gridPosition} would do, as a cursor: the priority of {@link #press}.
     * {@code nearestPin} is the pin nearest to {@code gridPosition}, if any.
     */
    private CanvasCursor cursorAt(ToolContext context, Point2D gridPosition,
                                  Optional<ConnectionPoints.PinHit> nearestPin) {
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
        if (pressPoint == null || (deltaX == 0 && deltaY == 0)) {
            return;
        }
        for (ElementId id : context.selection().ids()) {
            context.openDocument().document().element(id)
                    .ifPresent(element -> context.paintGhost(graphics, element.translate(deltaX, deltaY)));
        }
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
        Set<ElementId> ids = context.selection().ids();
        Optional<GridPoint> topLeft = SelectionBounds.topLeft(context.openDocument(), ids);
        if (topLeft.isEmpty()) {
            return;
        }
        CircuitFragment fragment = CircuitFragment.of(context.openDocument().document(), ids,
                context.openDocument().registry());
        PasteCommand paste = new PasteCommand(fragment, topLeft.get().translate(deltaX, deltaY));
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

    private boolean isInteractive(ToolContext context, Component component) {
        return interactiveByType.computeIfAbsent(component.typeId(), typeId -> context.openDocument().registry()
                .require(typeId).createBehavior(component.parameters()).isInteractive());
    }

    private void reset() {
        pressedElement = null;
        pressPoint = null;
        narrowOnRelease = false;
        wire = null;
        bandStart = null;
        bandEnd = null;
        bandAdditive = false;
        deltaX = 0;
        deltaY = 0;
    }
}
