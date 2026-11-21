package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/** The pale-yellow bulb is held by its outline: at least 3:1 (graphics) against a light card. */
class TipBulbOutlineTest {

    @Test
    void theOutlineReadsOnALightCard() {
        assertThat(ReadableColors.contrast(TipBulbIcon.OUTLINE, new Color(0xF2F2F2))).isGreaterThanOrEqualTo(3.0);
    }
}
