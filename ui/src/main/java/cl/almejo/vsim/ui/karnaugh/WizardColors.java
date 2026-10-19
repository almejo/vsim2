package cl.almejo.vsim.ui.karnaugh;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The theme colours the wizard paints with, read from the FlatLaf theme each time (never cached: the theme changes
 * live), with plain fallbacks for other look and feels.
 */
final class WizardColors {

    private WizardColors() {
    }

    static Color foreground() {
        return color("Label.foreground", Color.BLACK);
    }

    static Color background() {
        return color("Panel.background", Color.WHITE);
    }

    static Color cellBackground() {
        return color("Table.background", background());
    }

    static Color muted() {
        return color("Label.disabledForeground", Color.GRAY);
    }

    static Color gridLine() {
        return color("Component.borderColor", Color.GRAY);
    }

    static Color highlight() {
        return color("Component.focusColor", new Color(0x1E88E5));
    }

    static Color error() {
        return color("Component.error.focusedBorderColor", new Color(0xE53935));
    }

    /**
     * The error colour for text: {@link #error()} on a light theme; on a dark theme the theme's error colour is a dim
     * border red, too dark to read as text, so a brighter red is used.
     */
    static Color errorText() {
        return dark() ? new Color(0xFF7A7A) : error();
    }

    /**
     * Amber for a warning that does not block anything (letters written together): a dark amber on a light theme and
     * a bright one on a dark theme, both readable as text (the theme's own warning colour is a border colour, too
     * pale for text on white).
     */
    static Color warning() {
        return dark() ? new Color(0xE8B04A) : new Color(0x8A5A00);
    }

    /** True on a dark theme: picks the bright variant of the group colours. */
    static boolean dark() {
        Color background = background();
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255;
        return luminance < 0.5;
    }

    /** {@code color} with {@code alpha}. */
    static Color translucent(Color color, int alpha) {
        return new Color((color.getRGB() & 0xFFFFFF) | alpha << 24, true);
    }

    /** An opaque mix: {@code share} of {@code top} over {@code bottom} (renderers cannot be translucent). */
    static Color blend(Color top, Color bottom, double share) {
        return new Color(mix(top.getRed(), bottom.getRed(), share), mix(top.getGreen(), bottom.getGreen(), share),
                mix(top.getBlue(), bottom.getBlue(), share));
    }

    private static int mix(int top, int bottom, double share) {
        return (int) Math.round(top * share + bottom * (1 - share));
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
