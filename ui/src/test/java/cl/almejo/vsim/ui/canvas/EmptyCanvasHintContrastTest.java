package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The empty-canvas hint is text over the canvas scheme's background, whatever the interface theme is. */
class EmptyCanvasHintContrastTest {

    @Test
    void theHintReadsOnEveryScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            assertThat(ReadableColors.contrast(EmptyCanvasHint.color(scheme), scheme.background()))
                    .as(id)
                    .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
        }
    }
}
