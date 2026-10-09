package cl.almejo.vsim.ui.canvas;

import java.awt.Point;

/**
 * How far the view scrolls per tick while a move or a rubber band is held near or past the canvas edge: nothing
 * farther than {@link #MARGIN} pixels from every edge, then faster the deeper the pointer, up to {@link #MAXIMUM_STEP}.
 */
final class EdgeAutoscroll {

    static final int MARGIN = 24;
    static final int MAXIMUM_STEP = 16;

    private EdgeAutoscroll() {
    }

    /** Pixels to pan the content this tick; positive moves it right/down (the view goes towards the left/top edge). */
    static Point step(int width, int height, Point pointer) {
        return new Point(axisStep(pointer.x, width), axisStep(pointer.y, height));
    }

    private static int axisStep(int position, int length) {
        if (position < MARGIN) {
            return speed(MARGIN - position);
        }
        if (position > length - MARGIN) {
            return -speed(position - (length - MARGIN));
        }
        return 0;
    }

    private static int speed(int depth) {
        return Math.min(MAXIMUM_STEP, 2 + depth / 3);
    }
}
