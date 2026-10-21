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

    String tooltip();

    void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered);

    /** Starts the gesture a press on the handle begins; it owns the drag and the release until it ends. */
    EdgeGesture press(CanvasMouseEvent event);
}
