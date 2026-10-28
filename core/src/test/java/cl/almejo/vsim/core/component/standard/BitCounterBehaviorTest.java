package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BitCounterBehaviorTest {

    private static final int A = 0;
    private static final int Y = 1;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    @Test
    void theResultHasAsManyBitsAsNeededToCountZeroToN() {
        ComponentType counter = StandardComponentTypes.registry().require(ArithmeticTypes.BIT_COUNTER);
        assertThat(counter.pins()).extracting(PinSpec::name).containsExactly("A", "Y");
        assertThat(counter.pins()).extracting(PinSpec::width).containsExactly(8, 4);
        assertThat(counter.specializedFor(width(32)).pin(Y).width()).isEqualTo(6);
        assertThat(counter.specializedFor(width(1)).pin(Y).width()).isEqualTo(1);
        assertThat(counter.specializedFor(width(7)).pin(Y).width()).isEqualTo(3);
    }

    @Test
    void countsTheOnes() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.BIT_COUNTER, width(8)).settle();
        harness.set(A, 0).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(4, 0));
        harness.set(A, 0b1011_0100).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(4, 4));
        harness.set(A, 0xFF).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(4, 8));
    }

    @Test
    void allOnesAtThirtyTwoBitsAndOneBit() {
        BlockHarness wide = new BlockHarness(ArithmeticTypes.BIT_COUNTER, width(32)).settle();
        wide.set(A, 0xFFFF_FFFFL).settle();
        assertThat(wide.output(Y)).isEqualTo(Value.of(6, 32));
        BlockHarness single = new BlockHarness(ArithmeticTypes.BIT_COUNTER, width(1)).settle();
        single.set(A, 1).settle();
        assertThat(single.output(Y)).isEqualTo(Value.ONE);
    }

    @Test
    void undefinedDataGivesZAndTheDelayIsHonoured() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.BIT_COUNTER,
                Map.of(StandardComponentTypes.WIDTH, 8, "delay", 2)).settle();
        harness.set(A, new Value(8, 0b0001, 0b1000, false)).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(4));
        harness.set(A, 7).advance(1);
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(4));
        harness.advance(1);
        assertThat(harness.output(Y)).isEqualTo(Value.of(4, 3));
    }
}
