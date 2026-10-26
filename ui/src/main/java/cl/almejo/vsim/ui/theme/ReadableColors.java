package cl.almejo.vsim.ui.theme;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * Colours that must stay readable on every theme {@link FlatLafInstaller} can install (FlatLaf light and dark and
 * their macOS variants), worked out from the current theme each time they are asked for: never cache them, the
 * theme changes live.
 */
public final class ReadableColors {

    /** WCAG AA's minimum contrast for normal text (1.4.3). */
    public static final double MINIMUM_TEXT_CONTRAST = 4.5;
    private static final Color LIGHT_ERROR_TEXT = new Color(0xC62828);
    private static final Color DARK_ERROR_TEXT = new Color(0xFF8F8F);
    private static final Color LIGHT_WARNING_TEXT = new Color(0x8A5300);
    private static final Color DARK_WARNING_TEXT = new Color(0xF2B85C);
    private static final Color LIGHT_MUTED_TEXT = new Color(0x626262);
    private static final Color DARK_MUTED_TEXT = new Color(0xADADAD);

    private ReadableColors() {
    }

    /**
     * The red of an error text (a value that does not validate, a failure in the status bar, the wizard's reasons): a
     * dark red on a light theme and a bright one on a dark theme, at least {@link #MINIMUM_TEXT_CONTRAST}:1 on the
     * panel background. The theme's own {@code Component.error.focusedBorderColor} is a border colour: as text it is
     * 3.7:1 on FlatLaf Light and 1.4:1 on FlatLaf Dark.
     */
    public static Color errorText() {
        return dark() ? DARK_ERROR_TEXT : LIGHT_ERROR_TEXT;
    }

    /**
     * The amber of a warning text that does not block anything («El pin F no está en el borde»): a dark amber on a
     * light theme and a bright one on a dark theme, at least {@link #MINIMUM_TEXT_CONTRAST}:1 on the panel background
     * (the theme's {@code Actions.Yellow} is an icon colour, about 2:1 as text on FlatLaf Light).
     */
    public static Color warningText() {
        return dark() ? DARK_WARNING_TEXT : LIGHT_WARNING_TEXT;
    }

    /**
     * The grey of a secondary sentence (a hint under a drawing, the scope line of a dialog), at least
     * {@link #MINIMUM_TEXT_CONTRAST}:1 on the panel background; {@code Label.disabledForeground} is about 3:1 on
     * FlatLaf Light and reads as «disabled».
     */
    public static Color mutedText() {
        return dark() ? DARK_MUTED_TEXT : LIGHT_MUTED_TEXT;
    }

    /** The panel background of the current theme (white when the look and feel has none). */
    public static Color panelBackground() {
        Color background = UIManager.getColor("Panel.background");
        return background != null ? background : Color.WHITE;
    }

    /** True when the current theme's panel background is dark: the bright variant of a colour reads there. */
    public static boolean dark() {
        Color background = panelBackground();
        return perceivedBrightness(background) < 0.5;
    }

    /** The simple brightness of {@code color}, 0 (black) to 1 (white): weighted sRGB channels, no gamma. */
    public static double perceivedBrightness(Color color) {
        return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255;
    }

    /** The WCAG contrast ratio between two opaque colours (1 to 21). */
    public static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** {@code color} as it shows painted over the opaque {@code background} (a translucent colour mixes with it). */
    public static Color over(Color color, Color background) {
        double share = color.getAlpha() / 255.0;
        return new Color(mix(color.getRed(), background.getRed(), share),
                mix(color.getGreen(), background.getGreen(), share),
                mix(color.getBlue(), background.getBlue(), share));
    }

    private static int mix(int top, int bottom, double share) {
        return (int) Math.round(top * share + bottom * (1 - share));
    }

    /** The WCAG relative luminance of {@code color} (linear light), 0 to 1. */
    public static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
