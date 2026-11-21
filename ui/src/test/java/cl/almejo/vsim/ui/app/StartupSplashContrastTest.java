package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The version under the splash's logo is readable text on the splash's fixed dark background. */
class StartupSplashContrastTest {

    @Test
    void theVersionReadsOnTheSplashBackground() {
        assertThat(ReadableColors.contrast(StartupSplash.VERSION_COLOR, StartupSplash.BACKGROUND))
                .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
    }
}
