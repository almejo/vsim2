package cl.almejo.vsim.ui.tools;

import java.awt.Cursor;

/**
 * A tool that puts something new on the canvas where the pointer is (a part chosen in the palette, a frame or a sticky
 * note drawn by dragging): the crosshair shows where.
 */
public interface CrosshairTool extends Tool {

    @Override
    default Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
