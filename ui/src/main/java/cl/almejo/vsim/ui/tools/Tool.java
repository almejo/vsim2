package cl.almejo.vsim.ui.tools;

import java.awt.Cursor;
import java.awt.Graphics2D;

/** What the canvas does with the mouse: select, place a component, draw a wire, pan. */
public interface Tool {

    default void activated(ToolContext context) {
    }

    default void deactivated(ToolContext context) {
    }

    default void mousePressed(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseDragged(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseReleased(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseMoved(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseExited(ToolContext context) {
    }

    /** @return true when the tool consumed the key, so canvas key bindings must not run */
    default boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        return false;
    }

    /** Paints previews on top of the circuit, in document coordinates (8 px per grid unit). */
    default void paintOverlay(Graphics2D graphics, ToolContext context) {
    }

    default Cursor cursor() {
        return Cursor.getDefaultCursor();
    }
}
