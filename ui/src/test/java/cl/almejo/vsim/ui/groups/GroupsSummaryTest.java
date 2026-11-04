package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.standard.BitGroups;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The summary under «Configuración avanzada…»: one line for equal pins, a line per pin for custom groups. */
class GroupsSummaryTest {

    @Test
    void uniformGroupsAreOneLine() {
        GroupsSummary summary = GroupsSummary.of(BitGroups.parse("0-3|4-7", 8));

        assertThat(summary.heading()).isEqualTo("2 pines de 4 bits");
        assertThat(summary.pinLines()).isEmpty();
        assertThat(summary.unused()).isEmpty();
    }

    @Test
    void oneBitPerPinIsUniformToo() {
        assertThat(GroupsSummary.of(BitGroups.onePerPin(8)).heading()).isEqualTo("8 pines de 1 bit");
        assertThat(GroupsSummary.of(BitGroups.parse("0-7", 8)).heading()).isEqualTo("1 pin de 8 bits");
    }

    @Test
    void equalPinsWithASmallerLastOneSaySo() {
        assertThat(GroupsSummary.of(BitGroups.parse("0-6|7", 8)).heading())
                .isEqualTo("2 pines de 7 bits (el último de 1)");
        assertThat(GroupsSummary.of(BitGroups.uniform(8, 3)).heading())
                .isEqualTo("3 pines de 3 bits (el último de 2)");
    }

    @Test
    void customGroupsListEveryPinAndTheUnusedBits() {
        GroupsSummary summary = GroupsSummary.of(BitGroups.parse("7-11,25-31|15-19|20", 32));

        assertThat(summary.heading()).isEqualTo("Personalizado, 3 pines:");
        assertThat(summary.pinLines()).containsExactly("1: 7-11,25-31 (12 bits)", "2: 15-19 (5 bits)", "3: 20 (1 bit)");
        assertThat(summary.unused()).isEqualTo(Optional.of("Sin usar: 0-6,12-14,21-24"));
    }
}
