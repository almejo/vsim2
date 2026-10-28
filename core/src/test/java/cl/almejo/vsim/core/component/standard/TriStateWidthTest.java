package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The tri-state buffer's «Ancho (bits)» and «Negar salida», and the controlled inverter preset. */
class TriStateWidthTest {

    private static final int INPUT = 0;
    private static final int OUTPUT = 1;
    private static final int ENABLE = 2;

    private static Map<String, Object> parameters(int width, boolean negate) {
        return Map.of("width", width, "negate", negate);
    }

    @Test
    void aBareTriStateIsStillOneBitWithoutNegation() {
        ComponentType triState = StandardComponentTypes.registry().require(StandardComponentTypes.TRI_STATE);
        assertThat(triState.pins()).extracting(PinSpec::name).containsExactly("in", "out", "enable");
        assertThat(triState.pins()).extracting(PinSpec::width).containsExactly(1, 1, 1);
        assertThat(triState.resolveParameters(Map.of())).containsEntry("width", 1).containsEntry("negate", false);
    }

    @Test
    void aWideTriStateCopiesAnEightBitBusWhileEnabledAndReleasesItOtherwise() {
        BlockHarness harness = new BlockHarness("tri-state", parameters(8, false))
                .set(INPUT, 0xA5L).set(ENABLE, 1L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(8, 0xA5L));
        harness.set(ENABLE, 0L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(8));
    }

    @Test
    void negatedItDrivesTheInvertedBusWhileEnabledAndReleasesItOtherwise() {
        BlockHarness harness = new BlockHarness("tri-state", parameters(8, true))
                .set(INPUT, 0xA5L).set(ENABLE, 1L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(8, 0x5AL));
        harness.set(ENABLE, 0L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(8));
    }

    @Test
    void anUnconnectedEnableCountsAsOff() {
        BlockHarness harness = new BlockHarness("tri-state", parameters(4, true), ENABLE).set(INPUT, 3L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(4));
    }

    @Test
    void theControlledInverterIsANegatedTriStatePreset() {
        ComponentPreset preset = StandardComponentTypes.preset("inverter-controlled").orElseThrow();
        assertThat(preset.typeId()).isEqualTo(StandardComponentTypes.TRI_STATE);
        assertThat(preset.parameters()).containsEntry("negate", true);
        assertThat(StandardComponentTypes.presets()).contains(preset);
        assertThat(StandardComponentTypes.nameId("tri-state", Map.of("negate", true)))
                .isEqualTo("inverter-controlled");
        assertThat(StandardComponentTypes.nameId("tri-state", Map.of("negate", false))).isEqualTo("tri-state");
    }
}
