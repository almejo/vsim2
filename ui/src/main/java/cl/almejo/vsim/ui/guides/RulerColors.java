package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.ui.theme.ThemeColors;

import java.awt.Color;

/**
 * The rulers' colours, read from the FlatLaf theme each time they are painted (never cached, the theme changes live):
 * the panel background (a little darker, or lighter in a dark theme, under the pointer), the disabled text for the
 * ticks and the theme's border colour for the inner edge.
 */
record RulerColors(Color background, Color tick, Color border) {

    private static final double HOVER_SHADE = 0.06;

    static RulerColors current(boolean hovered) {
        Color background = ThemeColors.ui("Panel.background", new Color(0xF2F2F2));
        Color foreground = ThemeColors.ui("Label.foreground", Color.BLACK);
        Color tick = ThemeColors.ui("Label.disabledForeground", Color.GRAY);
        Color border = ThemeColors.ui("Component.borderColor", ThemeColors.ui("Separator.foreground", Color.LIGHT_GRAY));
        Color shown = hovered ? ThemeColors.mix(background, foreground, HOVER_SHADE) : background;
        return new RulerColors(shown, tick, border);
    }

}
