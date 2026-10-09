package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everyday tool: select (Shift/Ctrl+click toggles, drag in empty space for a rubber band), move the selection by dragging any selected element, draw
 * wires by dragging from a pin or an unselected wire, toggle switches, context menu, double click to configure.
 *
 * <p>What a plain primary press grabs, in priority order: a pin (within the pin radius), then an unselected wire,
 * then the body of an element or an already selected element. Exception: a pin of a selected component grabs the
 * whole selection (instead of starting a wire) while the selection has more than one element. A press on an
 * element of a multi-selection that is released without moving (a plain click) narrows the selection to that
 * element; a press-and-drag moves the whole group.
 */
public final class SelectTool implements Tool {

    private static final double HOVER_RING_RADIUS = 4.5;
    private static final BasicStroke HOVER_RING = new BasicStroke(1.5f);
    private static final double BAND_THRESHOLD = 0.5;
    private static final BasicStroke BAND_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{4f, 3f}, 0f);
    private static final int BAND_FILL_ALPHA = 40;

    private final Map<String, Boolean> interactiveByType = new HashMap<>();
    private ConnectionPoints connectionPoints;
    private DocumentElement pressedElement;
    private GridPoint pressPoint;
    private WireDrawing wire;
    private GridPoint hoverPin;
    private Point2D bandStart;
    private Point2D bandEnd;
    private boolean bandAdditive;
    private boolean ownPressSeen;
    private boolean narrowOnRelease;
    private int deltaX;
    private int deltaY;

    @Override
    public void activated(ToolContext context) {
        ownPressSeen = false;
    }

    @Override
    public void deactivated(ToolContext context) {
        hoverPin = null;
        reset();
        context.repaint();
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
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
        if (event.clickCount() >= 2 && continuesOwnClick && hit.isPresent() && hit.get() instanceof Component component) {
            context.selection().select(component.id());
            context.configure(component);
            return;
        }
        Optional<ConnectionPoints.PinHit> pin = connectionPoints(context)
                .pinNear(context.openDocument().document(), event.gridPosition());
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
                wire = new WireDrawing(pin.get().point(), event.gridPosition());
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
                    context.openDocument().execute(new MoveElementsCommand(context.selection().ids(), deltaX, deltaY));
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
        if (hoverPin != null) {
            hoverPin = null;
            context.repaint();
        }
    }

    private void updateHover(ToolContext context, CanvasMouseEvent event) {
        GridPoint nearest = connectionPoints(context)
                .pinNear(context.openDocument().document(), event.gridPosition())
                .map(ConnectionPoints.PinHit::point).orElse(null);
        if (!Objects.equals(nearest, hoverPin)) {
            hoverPin = nearest;
            context.repaint();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
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
        Rectangle2D band = rubberBand();
        if (band != null) {
            paintBand(graphics, context, band);
            return;
        }
        if (hoverPin != null && wire == null) {
            double unit = GridPoint.PIXELS_PER_UNIT;
            graphics.setColor(context.scheme().selection());
            graphics.setStroke(HOVER_RING);
            graphics.draw(new Ellipse2D.Double(hoverPin.x() * unit - HOVER_RING_RADIUS,
                    hoverPin.y() * unit - HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS));
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
        graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), BAND_FILL_ALPHA));
        graphics.fill(pixels);
        graphics.setColor(color);
        graphics.setStroke(BAND_STROKE);
        graphics.draw(pixels);
    }

    /** A pin of a selected component moves the selection when the selection holds more than one element. */
    private static boolean grabsSelection(ToolContext context, Component component) {
        return context.selection().contains(component.id()) && context.selection().size() > 1;
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

    private void toggleIfInteractive(ToolContext context) {
        if (pressedElement instanceof Component component && isInteractive(context, component)) {
            context.openDocument().interact(component.id());
        }
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
