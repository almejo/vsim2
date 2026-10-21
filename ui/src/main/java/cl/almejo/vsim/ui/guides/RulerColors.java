package cl.almejo.vsim.ui.guides;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The rulers' colours, read from the FlatLaf theme each time they are painted (never cached, the theme changes live):
 * the panel background (a little darker, or lighter in a dark theme, under the pointer), the disabled text for the
 * ticks and the theme's border colour for the inner edge.
 */
record RulerColors(Color background, Color tick, Color border) {

    private static final double HOVER_SHADE = 0.06;

    static RulerColors current(boolean hovered) {
        Color background = color("Panel.background", new Color(0xF2F2F2));
        Color foreground = color("Label.foreground", Color.BLACK);
        Color tick = color("Label.disabledForeground", Color.GRAY);
        Color border = color("Component.borderColor", color("Separator.foreground", Color.LIGHT_GRAY));
        return new RulerColors(hovered ? mix(background, foreground, HOVER_SHADE) : background, tick, border);
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private static Color mix(Color from, Color toward, double amount) {
        return new Color(
                (int) Math.round(from.getRed() + (toward.getRed() - from.getRed()) * amount),
                (int) Math.round(from.getGreen() + (toward.getGreen() - from.getGreen()) * amount),
                (int) Math.round(from.getBlue() + (toward.getBlue() - from.getBlue()) * amount));
    }
}
