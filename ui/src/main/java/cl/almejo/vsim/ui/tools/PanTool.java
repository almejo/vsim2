package cl.almejo.vsim.ui.tools;

import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;

public final class PanTool implements Tool {

    private Point2D last;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        last = event.screenPosition();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (last == null) {
            return;
        }
        Point2D now = event.screenPosition();
        context.viewport().panBy(now.getX() - last.getX(), now.getY() - last.getY());
        last = now;
        context.repaint();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        last = null;
    }

    /** Escape goes back to the default (select) tool, like the wire tool. */
    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        last = null;
        context.useDefaultTool();
        return true;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    }
}
