package cl.almejo.vsim.ui.magnifier;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * Where the «Mirar adentro» panel goes on the screen and the point it grows from. Pure, so it is tested without a
 * window.
 *
 * @param anchor the point the panel grows from and shrinks back to
 * @param full   the panel at full size
 */
record MagnifierPlacement(Point anchor, Rectangle full) {

    /** Distance from the pointer, so the panel never sits under it and takes its hover. */
    static final int POINTER_OFFSET = 16;

    /**
     * Below and right of {@code pointer} (screen pixels); on a side where the panel does not fit inside {@code screen}
     * (when it is known), above or left of it instead, so it never covers the pointer. It grows from its point
     * nearest to the pointer, so at every size it stays inside the full panel and off the pointer.
     */
    static MagnifierPlacement beside(Point pointer, Dimension size, Optional<Rectangle> screen) {
        int x = screen.map(bounds -> along(pointer.x, size.width, bounds.x, bounds.width))
                .orElse(pointer.x + POINTER_OFFSET);
        int y = screen.map(bounds -> along(pointer.y, size.height, bounds.y, bounds.height))
                .orElse(pointer.y + POINTER_OFFSET);
        Rectangle full = new Rectangle(x, y, size.width, size.height);
        return new MagnifierPlacement(nearestTo(pointer, full), full);
    }

    /** Where the panel starts on one axis: after the pointer, else before it, else (too big for both) clamped. */
    private static int along(int pointer, int length, int screenStart, int screenLength) {
        int screenEnd = screenStart + screenLength;
        int after = pointer + POINTER_OFFSET;
        if (after + length <= screenEnd) {
            return after;
        }
        int before = pointer - POINTER_OFFSET - length;
        if (before >= screenStart) {
            return before;
        }
        return Math.max(screenStart, Math.min(after, screenEnd - length));
    }

    private static Point nearestTo(Point pointer, Rectangle full) {
        int x = Math.max(full.x, Math.min(pointer.x, full.x + full.width - 1));
        int y = Math.max(full.y, Math.min(pointer.y, full.y + full.height - 1));
        return new Point(x, y);
    }
}
