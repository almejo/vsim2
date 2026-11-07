package cl.almejo.vsim.ui.theme;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * The current theme's colours, read when asked: never cache them, the theme changes live. A key the look and feel does
 * not define (Metal in tests, an older FlatLaf) gives the caller's fallback.
 */
public final class ThemeColors {

    private static final Color FALLBACK_ACCENT = new Color(0x2675BF);

    private ThemeColors() {
    }

    /** The look and feel's colour {@code key}, or {@code fallback} when it has none. */
    public static Color ui(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    /** The theme's accent: a chosen segment, an on switch, the active icon of the right panel's strip. */
    public static Color accent() {
        return ui("Component.accentColor", ui("Component.focusColor",
                ui("List.selectionBackground", FALLBACK_ACCENT)));
    }

    /**
     * Paints a 1 px rule in the theme's border colour ({@code owner}'s foreground when there is none) over the
     * rectangle given: the line between a panel and what is next to it.
     */
    public static void paintRule(Graphics graphics, Component owner, int x, int y, int width, int height) {
        graphics.setColor(ui("Component.borderColor", owner.getForeground()));
        graphics.fillRect(x, y, width, height);
    }
}
