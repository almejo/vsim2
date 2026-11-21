package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/** The off track of both switches shows the white knob, in the light and in the dark theme. */
class SwitchTrackTest {

    @Test
    void theWhiteKnobReadsAgainstTheOffTrackOnEveryTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                assertThat(ReadableColors.contrast(Color.WHITE, ReadableColors.switchOffTrack()))
                        .as(theme.getName())
                        .isGreaterThanOrEqualTo(3.0);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
