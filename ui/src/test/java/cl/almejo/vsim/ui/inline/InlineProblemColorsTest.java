package cl.almejo.vsim.ui.inline;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class InlineProblemColorsTest {

    /** WCAG AA for normal text. */
    private static final double MINIMUM_CONTRAST = 4.5;

    private final LookAndFeel previous = UIManager.getLookAndFeel();

    @AfterEach
    void restoreTheLookAndFeel() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void theProblemTextIsReadableInTheLightTheme() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(new FlatLightLaf());
        assertThat(contrast(InlineTextEditor.problemForeground(), InlineTextEditor.problemBackground()))
                .isGreaterThanOrEqualTo(MINIMUM_CONTRAST);
    }

    @Test
    void theProblemTextIsReadableInTheDarkTheme() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(new FlatDarkLaf());
        assertThat(contrast(InlineTextEditor.problemForeground(), InlineTextEditor.problemBackground()))
                .isGreaterThanOrEqualTo(MINIMUM_CONTRAST);
    }

    @Test
    void theProblemStillLooksLikeAnError() throws UnsupportedLookAndFeelException {
        for (LookAndFeel lookAndFeel : new LookAndFeel[]{new FlatLightLaf(), new FlatDarkLaf()}) {
            UIManager.setLookAndFeel(lookAndFeel);
            Color background = InlineTextEditor.problemBackground();
            assertThat(background.getRed()).as(lookAndFeel.getName())
                    .isGreaterThan(background.getGreen() + 40)
                    .isGreaterThan(background.getBlue() + 40);
        }
    }

    private static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double fraction = value / 255.0;
        return fraction <= 0.03928 ? fraction / 12.92 : Math.pow((fraction + 0.055) / 1.055, 2.4);
    }
}
