package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.FlatLaf;

import java.awt.Color;

/**
 * The colours of the bubbles, read from the theme each time they are painted (never kept: the theme changes live).
 * The bubble is the theme's accent; its ink white or near black, whichever reads on it; the window behind is dimmed a
 * little more in a dark theme, as the shortcut sheet's veil.
 */
final class BubbleColors {

    /** Above this relative luminance the accent is light (a dark theme's accent) and takes dark ink. */
    private static final double LIGHT_ACCENT_LUMINANCE = 0.45;
    /** The mockup's dark ink on a light bubble. */
    private static final Color DARK_INK = new Color(0x0F1622);
    private static final Color LIGHT_SCRIM = new Color(20, 28, 40, 115);
    private static final Color DARK_SCRIM = new Color(0, 0, 0, 140);
    private static final int SHADOW_ALPHA = 18;
    private static final int RULE_ALPHA = 77;
    private static final int DISABLED_ALPHA = 90;

    private BubbleColors() {
    }

    static Color fill() {
        return ThemeColors.accent();
    }

    static Color ink() {
        return luminance(fill()) > LIGHT_ACCENT_LUMINANCE ? DARK_INK : Color.WHITE;
    }

    static Color scrim() {
        return FlatLaf.isLafDark() ? DARK_SCRIM : LIGHT_SCRIM;
    }

    /** One layer of the bubble's soft shadow. */
    static Color shadow() {
        return new Color(0, 0, 0, SHADOW_ALPHA);
    }

    /** The thin rule above the «Atajo» line: the ink at 30 %. */
    static Color rule() {
        return withAlpha(ink(), RULE_ALPHA);
    }

    /** The ink of a button that cannot be used now (‹ on the first bubble). */
    static Color disabledInk() {
        return withAlpha(ink(), DISABLED_ALPHA);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** The WCAG relative luminance, from 0 (black) to 1 (white). */
    static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double scaled = value / 255.0;
        return scaled <= 0.03928 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    }
}
