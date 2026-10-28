package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchValuesTest {

    private final ComponentType switchType = StandardComponentTypes.registry().require(StandardComponentTypes.SWITCH);

    private Map<String, Object> parameters(int width, String value) {
        return switchType.resolveParameters(Map.of(StandardComponentTypes.WIDTH, width, StandardComponentTypes.VALUE,
                value));
    }

    @Test
    void aClickTogglesAllOnesAndTurnsAnythingElseIntoAllOnes() {
        assertThat(SwitchValues.toggled(Value.ONE)).isEqualTo(Value.ZERO);
        assertThat(SwitchValues.toggled(Value.ZERO)).isEqualTo(Value.ONE);
        assertThat(SwitchValues.toggled(Value.HIGH_IMPEDANCE)).isEqualTo(Value.ONE);
        assertThat(SwitchValues.toggled(Value.of(4, 0b0101))).isEqualTo(Value.of(4, 0b1111));
    }

    @Test
    void aBitClickFlipsThatBitAndLeavesZAlone() {
        assertThat(SwitchValues.withBitFlipped(Value.of(4, 0b0101), 1)).contains(Value.of(4, 0b0111));
        assertThat(SwitchValues.withBitFlipped(Value.of(4, 0b0101), 4)).isEmpty();
        assertThat(SwitchValues.withBitFlipped(Value.highImpedance(4), 0)).isEmpty();
    }

    @Test
    void aOneBitSwitchStoresZeroOneOrZ() {
        assertThat(SwitchValues.textFor(Value.ZERO, parameters(1, "1"))).contains("0");
        assertThat(SwitchValues.textFor(Value.ONE, parameters(1, "Z"))).contains("1");
        assertThat(SwitchValues.textFor(Value.HIGH_IMPEDANCE, parameters(1, "1"))).contains("Z");
    }

    @Test
    void aWideSwitchKeepsTheBaseItsValueWasWrittenIn() {
        assertThat(SwitchValues.textFor(Value.of(8, 0b1010_0101), parameters(8, "0b1010"))).contains("0b10100101");
        assertThat(SwitchValues.textFor(Value.of(8, 0xA5), parameters(8, "0x1F"))).contains("0xA5");
        assertThat(SwitchValues.textFor(Value.of(8, 0xA5), parameters(8, "7"))).contains("165");
        assertThat(SwitchValues.textFor(Value.of(8, 1), parameters(8, "0x10"))).as("0 and 1 are plain digits")
                .contains("1");
    }

    @Test
    void aSignedDecimalStaysSigned() {
        Map<String, Object> signed = switchType.resolveParameters(Map.of(StandardComponentTypes.WIDTH, 8,
                StandardComponentTypes.VALUE, "-3", StandardComponentTypes.SIGNEDNESS,
                StandardComponentTypes.SIGNEDNESS_SIGNED));

        assertThat(SwitchValues.textFor(Value.of(8, 0xFF), signed)).contains("-1");
    }

    @Test
    void aWideValueWithZCannotBeStored() {
        assertThat(SwitchValues.textFor(Value.highImpedance(8), parameters(8, "0"))).isEmpty();
    }

    @Test
    void theValueOfTheParametersIsWhatTheSwitchDrives() {
        assertThat(SwitchValues.valueOf(parameters(8, "0x2A"))).isEqualTo(Value.of(8, 0x2A));
        assertThat(SwitchValues.valueOf(parameters(1, "Z"))).isEqualTo(Value.HIGH_IMPEDANCE);
    }
}
