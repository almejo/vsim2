package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The ROM: the RAM's read side, with no way to write. */
class RomBehaviorTest {

    private static final Map<String, Object> PROGRAM = Map.of(MemoryTypes.WORDS, "16", MemoryTypes.WIDTH, 8,
            MemoryTypes.CONTENTS, "@0 3A C6 @F FF");

    private static int pin(BlockHarness harness, String name) {
        return MemoryTypesTest.pinIndex(harness.type(), name);
    }

    @Test
    void aRomHasOnlyAddressChipSelectAndQ() {
        ComponentType rom = StandardComponentTypes.registry().require(StandardComponentTypes.ROM);
        assertThat(rom.pins()).extracting(PinSpec::name).containsExactly("A", "CS", "Q");
        assertThat(rom.parameters()).extracting(parameter -> parameter.name())
                .doesNotContain(MemoryTypes.DATA, MemoryTypes.WRITE);
    }

    @Test
    void readsItsContents() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ROM, PROGRAM, 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x3A));
        harness.set(pin(harness, "A"), 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0xC6));
        harness.set(pin(harness, "A"), 15).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0xFF));
        harness.set(pin(harness, "A"), 7).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0));
    }

    @Test
    void csRulesAreTheRamOnes() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ROM, PROGRAM).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("CS = 0").isEqualTo(Value.highImpedance(8));
        harness.set(pin(harness, "CS"), 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x3A));
        harness.conflict(pin(harness, "CS")).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("conflict on CS").isEqualTo(Value.highImpedance(8));
        harness.set(pin(harness, "CS"), 1).release(pin(harness, "A")).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("address with Z").isEqualTo(Value.highImpedance(8));
    }

    @Test
    void restoringARomChangesNothing() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ROM, PROGRAM, 1).settle();
        harness.restoreMemory().settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x3A));
    }
}
