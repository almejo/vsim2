package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Default tool: select, move by dragging, toggle switches, context menu, double click to configure. */
public final class SelectTool implements Tool {

    private final Map<String, Boolean> interactiveByType = new HashMap<>();
    private DocumentElement pressedElement;
    private GridPoint pressPoint;
    private int deltaX;
    private int deltaY;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        Optional<DocumentElement> hit = context.hitTester().elementAt(context.openDocument().document(), event.gridPosition());
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
        if (hit.isEmpty()) {
            context.selection().clear();
            return;
        }
        DocumentElement element = hit.get();
        context.selection().select(element.id());
        if (event.clickCount() >= 2 && element instanceof Component component) {
            context.configure(component);
            return;
        }
        pressedElement = element;
        pressPoint = event.snapped();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
        context.repaint();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        if (deltaX != 0 || deltaY != 0) {
            context.openDocument().execute(new MoveElementsCommand(context.selection().ids(), deltaX, deltaY));
        } else if (pressedElement instanceof Component component && isInteractive(context, component)) {
            context.openDocument().interact(component.id());
        }
        reset();
        context.repaint();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (pressPoint != null) {
            reset();
            context.repaint();
        } else {
            context.selection().clear();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (pressPoint == null || (deltaX == 0 && deltaY == 0)) {
            return;
        }
        for (ElementId id : context.selection().ids()) {
            context.openDocument().document().element(id)
                    .ifPresent(element -> context.paintGhost(graphics, element.translate(deltaX, deltaY)));
        }
    }

    private boolean isInteractive(ToolContext context, Component component) {
        return interactiveByType.computeIfAbsent(component.typeId(), typeId -> context.openDocument().registry()
                .require(typeId).createBehavior(component.parameters()).isInteractive());
    }

    private void reset() {
        pressedElement = null;
        pressPoint = null;
        deltaX = 0;
        deltaY = 0;
    }
}
