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
