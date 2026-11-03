package cl.almejo.vsim.ui.print;

import java.awt.Dimension;
import java.awt.Rectangle;

/** The rule for a dialog whose content grew: the window follows it, never shrinks and stays on the screen. */
final class WindowGrowth {

    private WindowGrowth() {
    }

    /**
     * The bounds {@code current} should have to fit {@code preferred}: each dimension grows when the window is smaller
     * than the preferred size (never shrinks, so a size the user made larger stays), up to the {@code usable} screen
     * area; the window is then moved the least that keeps it inside that area.
     */
    static Rectangle grown(Rectangle current, Dimension preferred, Rectangle usable) {
        int width = Math.max(current.width, Math.min(preferred.width, usable.width));
        int height = Math.max(current.height, Math.min(preferred.height, usable.height));
        int left = Math.max(usable.x, Math.min(current.x, usable.x + usable.width - width));
        int top = Math.max(usable.y, Math.min(current.y, usable.y + usable.height - height));
        return new Rectangle(left, top, width, height);
    }
}
