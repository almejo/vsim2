package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

/**
 * The Terminal part's size in grid units: the cells rounded up to whole units, a title bar and a margin on every side
 * of the screen. Fixed in core so the size is known without a font.
 */
public final class TerminalGeometry {

    /** The title bar's height, in grid units. */
    public static final int TITLE_BAR_UNITS = 2;
    /** The margin around the screen, in grid units. */
    public static final int MARGIN_UNITS = 1;
    /** Wide enough for the three control pins on the bottom edge (x = 2, 4, 6) to sit inside the part. */
    public static final int MINIMUM_WIDTH_UNITS = 8;

    private TerminalGeometry() {
    }

    /** The part's size for a screen of {@code rows} × {@code columns} cells. */
    public static GridSize size(int rows, int columns, TerminalFontSize fontSize) {
        int screenWidth = unitsFor(columns * fontSize.cellWidth());
        int screenHeight = unitsFor(rows * fontSize.cellHeight());
        return new GridSize(Math.max(MINIMUM_WIDTH_UNITS, screenWidth + 2 * MARGIN_UNITS),
                TITLE_BAR_UNITS + screenHeight + 2 * MARGIN_UNITS);
    }

    private static int unitsFor(int pixels) {
        return (pixels + GridPoint.PIXELS_PER_UNIT - 1) / GridPoint.PIXELS_PER_UNIT;
    }
}
