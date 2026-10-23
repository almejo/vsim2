package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchPainterTest {

    /** The switch's 0, 1 or Z is small bold text: at least the graphics contrast on its fill. */
    private static final double READABLE_ON_FILL = 3.0;

    @Test
    void theSwitchTextReadsOnTheFillOfEveryValueInEveryScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            for (Value value : List.of(Value.ZERO, Value.ONE, Value.HIGH_IMPEDANCE)) {
                Color fill = SignalColors.valueColor(value, scheme);
                assertThat(ReadableColors.contrast(SwitchPainter.textColor(fill), fill)).as("%s %s", id, value)
                        .isGreaterThanOrEqualTo(READABLE_ON_FILL);
            }
        }
    }

    @Test
    void theTextStaysWhiteWhereWhiteAlreadyReads() {
        assertThat(SwitchPainter.textColor(ColorSchemes.CLASSIC.wireOne())).isEqualTo(Color.WHITE);
        assertThat(SwitchPainter.textColor(ColorSchemes.PLACA.wireZero())).isEqualTo(Color.WHITE);
        assertThat(SwitchPainter.textColor(ColorSchemes.PLACA.wireOne())).isEqualTo(Color.BLACK);
    }
}
