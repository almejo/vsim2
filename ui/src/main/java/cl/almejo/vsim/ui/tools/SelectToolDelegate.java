package cl.almejo.vsim.ui.tools;

import java.awt.Graphics2D;
import java.util.Optional;

/**
 * Something {@link SelectTool} asks before doing its own work (M6.3: the title bars and edges of frames). A delegate
 * that grabs a primary press also receives the drag and the release that follow it; the tool does nothing else with
 * them. Where no delegate takes the press, the tool behaves exactly as without delegates.
 */
public interface SelectToolDelegate {

    /** What a delegate did with a press. */
    enum Press {
        /** Not the delegate's: the next delegate, or the tool, gets it. */
        IGNORED,
        /** Done with at once (a click on a control, a toggle, a menu, an editor): the drag and release are nobody's. */
        HANDLED,
        /** Taken with its drag and release, which go to this delegate only. */
        GRABBED
    }

    /** A primary or secondary press. Only a primary press can be {@link Press#GRABBED}. */
    Press pressed(ToolContext context, CanvasMouseEvent event);

    /** The pointer moved while the primary press this delegate grabbed is held. */
    default void dragged(ToolContext context, CanvasMouseEvent event) {
    }

    /** The primary button of the press this delegate grabbed was released. */
    default void released(ToolContext context, CanvasMouseEvent event) {
    }

    /** Escape, or the tool going away, while a press of this delegate is held: forget it, change nothing. */
    default void cancelled(ToolContext context) {
    }

    /** The cursor while a press of this delegate is dragged. */
    default CanvasCursor pressCursor() {
        return CanvasCursor.MOVE;
    }

    /**
     * The pointer moved with no button held (also right after a release); the cursor when a press here would be taken
     * by this delegate, empty otherwise. Every delegate hears every move, so each can follow the hover.
     */
    default Optional<CanvasCursor> moved(ToolContext context, CanvasMouseEvent event) {
        return Optional.empty();
    }

    /** The pointer left the canvas. */
    default void exited(ToolContext context) {
    }

    /** Paints under the tool's own previews, in document coordinates. */
    default void paintOverlay(Graphics2D graphics, ToolContext context) {
    }
}
