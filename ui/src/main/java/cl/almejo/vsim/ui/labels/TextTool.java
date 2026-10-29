package cl.almejo.vsim.ui.labels;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Cursor;
import java.awt.event.KeyEvent;

/**
 * The «Texto» tool: a click opens the inline editor for a free text at the snapped point (the same one right click ▸
 * «Agregar texto aquí» opens) and the editor goes back to Selección at once, so a confirmed or a cancelled text both
 * leave the default tool active. Nothing is added until the text is confirmed, and a blank text adds nothing.
 */
public final class TextTool implements Tool {

    private boolean pressed;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        pressed = event.button() == CanvasMouseEvent.PRIMARY;
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (!pressed) {
            return;
        }
        pressed = false;
        GridPoint point = context.snapper().snapPoint(context, event.snapped()).point();
        context.useDefaultTool();
        context.addTextAt(point);
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        context.useDefaultTool();
        return true;
    }

    @Override
    public void deactivated(ToolContext context) {
        pressed = false;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
    }
}
