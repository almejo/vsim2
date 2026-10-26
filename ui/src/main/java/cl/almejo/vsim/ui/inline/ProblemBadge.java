package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.util.List;

/**
 * The readable white-on-red badge a problem shows in (the inline text editor's problem, the find bar's «Sin
 * resultados» and invalid expressions). Its colours are read from the theme every time, since the theme may change
 * while it shows.
 */
public final class ProblemBadge extends JLabel {

    private static final double MINIMUM_CONTRAST = 4.5;
    private static final Color FALLBACK_ERROR = new Color(0xDB5860);

    public ProblemBadge() {
        setOpaque(true);
        setBorder(new EmptyBorder(1, 4, 1, 4));
    }

    @Override
    public Color getForeground() {
        return foreground();
    }

    @Override
    public Color getBackground() {
        return background();
    }

    /** White on the solid red of {@link #background()}. */
    public static Color foreground() {
        return Color.WHITE;
    }

    /**
     * The theme's error red, darkened until white text on it reaches the WCAG AA contrast for normal text, in light
     * and dark themes alike.
     */
    public static Color background() {
        Color color = themeErrorColor();
        while (contrastWithWhite(color) < MINIMUM_CONTRAST) {
            color = new Color((int) (color.getRed() * 0.9), (int) (color.getGreen() * 0.9),
                    (int) (color.getBlue() * 0.9));
        }
        return color;
    }

    /** The theme's error red as it is (for outlines and tints, not for text). */
    public static Color themeErrorColor() {
        for (String key : List.of("Actions.Red", "Component.error.focusedBorderColor")) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return FALLBACK_ERROR;
    }

    private static double contrastWithWhite(Color color) {
        return 1.05 / (ReadableColors.luminance(color) + 0.05);
    }
}
