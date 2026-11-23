package cl.almejo.vsim.ui.panels;

import java.awt.Font;

/**
 * Fonts derived smaller than the interface's own (headings, notes, keycaps) never go under {@value #MINIMUM_SIZE} px,
 * so that at the interface size 75 % they still read. Only when they would: from 100 % up nothing changes, and a base
 * font that is itself under the floor is never made bigger than it is.
 */
public final class FontFloor {

    public static final float MINIMUM_SIZE = 9f;

    private FontFloor() {
    }

    /** {@code wanted}, or the floor when it is under it (never above {@code base}'s own size). */
    public static float size(Font base, float wanted) {
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
