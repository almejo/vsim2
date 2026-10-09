package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everyday tool: select, move by dragging a body, draw wires by dragging from a pin or an unselected wire,
 * toggle switches, context menu, double click to configure.
 *
 * <p>What a primary press grabs, in priority order: a pin (within the pin radius), then an unselected wire, then
 * the body of an element or an already selected element.
 */
public final class SelectTool implements Tool {

    private static final double HOVER_RING_RADIUS = 4.5;
    private static final BasicStroke HOVER_RING = new BasicStroke(1.5f);

    private final Map<String, Boolean> interactiveByType = new HashMap<>();
    private ConnectionPoints connectionPoints;
    private DocumentElement pressedElement;
    private GridPoint pressPoint;
    private WireDrawing wire;
    private GridPoint hoverPin;
    private boolean ownPressSeen;
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
        if (pin.isPresent()) {
            pressedElement = pin.get().component();
            wire = new WireDrawing(pin.get().point(), event.gridPosition());
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
            context.selection().clear();
            return;
        }
        context.selection().select(hit.get().id());
        pressPoint = event.snapped();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
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
        if (wire != null) {
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
        if (pressPoint != null || wire != null) {
            reset();
            context.repaint();
        } else {
            context.selection().clear();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
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

    /**
     * The snapped point where the current press on a selected element started, while that press may still become a
     * move of the selection; empty with no press or while drawing a wire. The canvas uses it to start a drag out.
     */
    public Optional<GridPoint> movingFrom() {
        return wire == null ? Optional.ofNullable(pressPoint) : Optional.empty();
    }

    /** Forgets the press in progress without changing the document: the drag goes on outside the canvas. */
    public void cancelMove(ToolContext context) {
        reset();
        context.repaint();
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
        wire = null;
        deltaX = 0;
        deltaY = 0;
    }
}
