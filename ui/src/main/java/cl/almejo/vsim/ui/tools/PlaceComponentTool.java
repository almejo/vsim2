package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Map;

/** Places a component where the user clicks, then returns to the default tool (Shift keeps placing). */
public final class PlaceComponentTool implements Tool {

    private static final ElementId PREVIEW_ID = new ElementId("preview");

    private final ComponentType type;
    private Rotation rotation = Rotation.R0;
    private GridPoint hover;

    public PlaceComponentTool(ComponentType type) {
        this.type = type;
    }

    public ComponentType type() {
        return type;
    }

    public Rotation rotation() {
        return rotation;
    }

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        hover = event.snapped();
        context.repaint();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        mouseMoved(context, event);
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        ComponentPlacement.place(context, type, event.snapped(), rotation);
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_R) {
            rotation = shiftDown ? rotation.counterClockwise() : rotation.clockwise();
            context.repaint();
            return true;
        }
        if (keyCode == KeyEvent.VK_ESCAPE) {
            context.useDefaultTool();
            return true;
        }
        return false;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (hover != null) {
            context.paintGhost(graphics, type.newComponent(PREVIEW_ID, hover, rotation, Map.of(), ""));
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
