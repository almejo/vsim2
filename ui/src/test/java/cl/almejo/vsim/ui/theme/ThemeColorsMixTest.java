package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeColorsMixTest {

    @Test
    void anAmountOfZeroKeepsTheFirstColorAndOneGivesTheSecond() {
        Color from = new Color(10, 20, 30);
        Color toward = new Color(200, 100, 50);

        assertThat(ThemeColors.mix(from, toward, 0)).isEqualTo(from);
        assertThat(ThemeColors.mix(from, toward, 1)).isEqualTo(toward);
    }

    @Test
    void halfWayRoundsEachChannel() {
        Color mixed = ThemeColors.mix(new Color(0, 0, 0), new Color(255, 101, 10), 0.5);

        assertThat(mixed).isEqualTo(new Color(128, 51, 5));
    }

    @Test
    void theResultIsOpaqueAndMovesDownwardsToo() {
        Color mixed = ThemeColors.mix(new Color(200, 200, 200, 40), new Color(100, 0, 100), 0.25);

        assertThat(mixed).isEqualTo(new Color(175, 150, 175));
        assertThat(mixed.getAlpha()).isEqualTo(255);
    }
}
