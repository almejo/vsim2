package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.theme.AppThemes;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WizardColorsTest {

    @Test
    void theColouredTextsAreReadableOnTheDialogBackgroundOfBothThemes() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                Map<String, Color> texts = Map.of("errorText", WizardColors.errorText(),
                        "warning", WizardColors.warning(), "failure", WizardColors.failure(),
                        "success", WizardColors.success());
                for (Map.Entry<String, Color> text : texts.entrySet()) {
                    assertThat(WizardColors.contrast(text.getValue(), WizardColors.background()))
                            .as(text.getKey() + " on " + theme.getName())
                            .isGreaterThanOrEqualTo(4.5);
                }
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void theErrorTextIsARedOnBothThemes() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                Color errorText = WizardColors.errorText();
                assertThat(errorText.getRed()).as(theme.getName())
                        .isGreaterThan(errorText.getGreen() + 60)
                        .isGreaterThan(errorText.getBlue() + 60);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
