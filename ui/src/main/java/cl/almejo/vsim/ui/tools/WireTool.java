package cl.almejo.vsim.ui.tools;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** Optional mode: every drag draws a wire, wherever it starts. */
public final class WireTool implements Tool {

    private WireDrawing drawing;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() == CanvasMouseEvent.PRIMARY) {
            drawing = new WireDrawing(event.snapped(), event.gridPosition());
        }
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (drawing != null) {
            drawing.dragTo(event);
            context.repaint();
        }
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (drawing == null) {
            return;
        }
        drawing.commit(context, event.snapped());
        drawing = null;
        context.repaint();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (drawing != null) {
            drawing = null;
            context.repaint();
        } else {
            context.useDefaultTool();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (drawing != null) {
            drawing.paintPreview(graphics, context);
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
