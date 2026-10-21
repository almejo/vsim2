package cl.almejo.vsim.ui.canvas.edges;

import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * Something at a canvas edge the pointer can grab (a ruler today, a split grip later), painted in screen coordinates
 * on top of everything (canvas-edges spec §1–§4). Sizes are logical Swing pixels.
 */
public interface EdgeHandle {

    /** Where the handle takes presses and shows its cursor, in logical canvas pixels. */
    Shape hitArea(Dimension canvasSize);

    /** The higher wins where two hit areas overlap. */
    int priority();

    Cursor cursor();

    /**
     * The cursor over {@code event}'s point, inside the hit area; {@link #cursor()} unless the handle shows different
     * ones in different places (a guide line: up-down for a horizontal guide, left-right for a vertical one).
     */
    default Cursor cursorAt(CanvasMouseEvent event) {
        return cursor();
    }

    /** The tooltip over the handle; empty for none. */
    String tooltip();

    /**
     * Whether a press (or the pointer) at {@code event}, already inside the hit area, is this handle's: a guide line
     * under an element is not, nor one while another tool than the select tool is active. True by default.
     */
    default boolean claims(CanvasMouseEvent event) {
        return true;
    }

    void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered);

    /** Starts the gesture a press on the handle begins; it owns the drag and the release until it ends. */
    EdgeGesture press(CanvasMouseEvent event);
}
