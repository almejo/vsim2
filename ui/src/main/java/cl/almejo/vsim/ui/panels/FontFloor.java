package cl.almejo.vsim.ui.panels;

import java.awt.Font;

/**
 * Fonts derived smaller than the interface's own (headings, notes, keycaps) never go under {@value #MINIMUM_SIZE} px
 * while the interface size is under 100 % (75 %), so that they still read. Only then and only when they would: from
 * 100 % up nothing changes (not even with a small system font), and a base font that is itself under the floor is never
 * made bigger than it is. The theme manager tells the installed size ({@link #useInterfaceZoom}) before the look and feel
 * updates the components.
 */
public final class FontFloor {

    public static final float MINIMUM_SIZE = 9f;

    private static volatile int interfaceZoom = 100;

    private FontFloor() {
    }

    /** The interface size installed now, in percent: the floor holds only under 100. */
    public static void useInterfaceZoom(int percent) {
        if (percent <= 0) {
            throw new IllegalArgumentException("percent must be positive: " + percent);
        }
        interfaceZoom = percent;
    }

    /** {@code wanted}, or, under 100 %, the floor when it is under it (never above {@code base}'s own size). */
    public static float size(Font base, float wanted) {
        if (interfaceZoom >= 100) {
            return wanted;
        }
        return Math.max(wanted, Math.min(MINIMUM_SIZE, base.getSize2D()));
    }

    /** {@code base} at {@code factor} times its size, kept at the floor or more. */
    public static Font scaled(Font base, float factor) {
        return base.deriveFont(size(base, base.getSize2D() * factor));
    }

    /** {@code base} with {@code style} at {@code factor} times its size, kept at the floor or more. */
    public static Font scaled(Font base, int style, float factor) {
        return base.deriveFont(style, size(base, base.getSize2D() * factor));
    }

    /** {@code base} {@code shrink} px smaller, kept at the floor or more. */
    public static Font smaller(Font base, float shrink) {
        return base.deriveFont(size(base, base.getSize2D() - shrink));
    }
}
