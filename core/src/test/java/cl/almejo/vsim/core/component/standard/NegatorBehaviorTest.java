package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NegatorBehaviorTest {

    private static final int A = 0;
    private static final int Y = 1;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    @Test
    void pinsAreAAndY() {
        assertThat(StandardComponentTypes.registry().require(ArithmeticTypes.NEGATOR).pins())
                .extracting(PinSpec::name).containsExactly("A", "Y");
    }

    @Test
    void negatesInTwosComplementAndTheMinimumStaysTheMinimum() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.NEGATOR, width(8)).settle();
        harness.set(A, 5).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 251));
        harness.set(A, 0).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 0));
        harness.set(A, 0x80).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 0x80));
        harness.set(A, 0xFF).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 1));
    }

    @Test
    void thirtyTwoBitsAndOneBit() {
        BlockHarness wide = new BlockHarness(ArithmeticTypes.NEGATOR, width(32)).settle();
        wide.set(A, 1).settle();
        assertThat(wide.output(Y)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        BlockHarness single = new BlockHarness(ArithmeticTypes.NEGATOR, width(1)).settle();
        single.set(A, 1).settle();
        assertThat(single.output(Y)).isEqualTo(Value.ONE);
    }

    @Test
    void undefinedInputGivesZAndTheDelayIsHonoured() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.NEGATOR,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 3)).settle();
        harness.set(A, Value.highImpedance(4)).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(4));
        harness.set(A, 1).advance(2);
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(4));
        harness.advance(1);
        assertThat(harness.output(Y)).isEqualTo(Value.of(4, 15));
    }
}
