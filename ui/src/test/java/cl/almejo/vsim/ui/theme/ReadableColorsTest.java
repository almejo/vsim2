package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ReadableColorsTest {

    @Test
    void theErrorTextIsAReadableRedOnEveryTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                Color errorText = ReadableColors.errorText();
                assertThat(ReadableColors.contrast(errorText, ReadableColors.panelBackground()))
                        .as(theme.getName())
                        .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                assertThat(errorText.getRed()).as(theme.getName())
                        .isGreaterThan(errorText.getGreen() + 60)
                        .isGreaterThan(errorText.getBlue() + 60);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void anErrorLabelFollowsALiveThemeChange() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            ErrorTextLabel label = new ErrorTextLabel("No cabe en 8 bits");
            Color onLight = label.getForeground();

            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", true));

            assertThat(label.getForeground()).isNotEqualTo(onLight).isEqualTo(ReadableColors.errorText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void aTranslucentColourIsMixedWithItsBackground() {
        Color halfWhite = new Color(255, 255, 255, 128);

        assertThat(ReadableColors.over(halfWhite, Color.BLACK)).isEqualTo(new Color(128, 128, 128));
        assertThat(ReadableColors.over(Color.RED, Color.BLACK)).isEqualTo(Color.RED);
    }
}
