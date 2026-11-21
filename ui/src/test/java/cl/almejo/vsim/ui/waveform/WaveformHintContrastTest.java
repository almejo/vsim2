package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The empty-waveform hint is text over the scheme's chronogram background, whatever the interface theme is. */
class WaveformHintContrastTest {

    @Test
    void theHintReadsOnEveryScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            assertThat(ReadableColors.contrast(WaveformPlot.hintColor(scheme), scheme.diagramBackground()))
                    .as(id)
                    .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
        }
    }
}
