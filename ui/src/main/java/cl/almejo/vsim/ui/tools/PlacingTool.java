package cl.almejo.vsim.ui.tools;

import java.awt.Cursor;
import java.awt.event.KeyEvent;

/**
 * A tool that places a part chosen in the palette (a built-in component, a user component): a primary click places
 * one and goes back to the default tool (with Shift it stays, to place another); a secondary click or Escape goes back
 * without placing. Dragging is moving. The crosshair shows where.
 */
public abstract class PlacingTool implements Tool {

    /** Places one part for the primary press {@code event}. */
    protected abstract void place(ToolContext context, CanvasMouseEvent event);

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        mouseMoved(context, event);
    }

    // CPD-OFF PlaceMachineTool (state-machine branch) repeats these; it can extend this class once the branch merges
    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        place(context, event);
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_ESCAPE) {
            context.useDefaultTool();
            return true;
        }
        return false;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
    // CPD-ON
}
