package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiplierBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int LOW = 2;
    private static final int HIGH = 3;

    private static Map<String, Object> parameters(int bits, String signedness) {
        return Map.of(StandardComponentTypes.WIDTH, bits, StandardComponentTypes.SIGNEDNESS, signedness);
    }

    @Test
    void pinsAreABAndTheTwoHalves() {
        assertThat(StandardComponentTypes.registry().require(ArithmeticTypes.MULTIPLIER).pins())
                .extracting(PinSpec::name).containsExactly("A", "B", "P", "H");
    }

    @Test
    void unsignedProductIsSplitInLowAndHighHalves() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.MULTIPLIER,
                parameters(8, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        harness.set(A, 200).set(B, 100).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(8, 20000 & 0xFF));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(8, 20000 >> 8));
        harness.set(A, 3).set(B, 4).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(8, 12));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(8, 0));
    }

    @Test
    void signedMinusOneTimesMinusOneIsOne() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.MULTIPLIER,
                parameters(8, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        harness.set(A, 0xFF).set(B, 0xFF).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(8, 1));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(8, 0));
        harness.set(A, 0xFF).set(B, 2).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(8, 0xFE));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(8, 0xFF));
        harness.set(A, 0x80).set(B, 0x80).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(8, 0));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(8, 0x40));
    }

    @Test
    void thirtyTwoBitUnsignedMaximumTimesMaximumIsExact() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.MULTIPLIER,
                parameters(32, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        harness.set(A, 0xFFFF_FFFFL).set(B, 0xFFFF_FFFFL).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(32, 1));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(32, 0xFFFF_FFFEL));
    }

    @Test
    void thirtyTwoBitSignedMinimumSquared() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.MULTIPLIER,
                parameters(32, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        harness.set(A, 0x8000_0000L).set(B, 0x8000_0000L).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(32, 0));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(32, 0x4000_0000L));
        harness.set(A, 0x8000_0000L).set(B, 1).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.of(32, 0x8000_0000L));
        assertThat(harness.output(HIGH)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
    }

    @Test
    void undefinedDataGivesZAndTheDelayIsHonoured() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.MULTIPLIER,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 5)).settle();
        harness.set(A, Value.highImpedance(4)).set(B, 3).settle();
        assertThat(harness.output(LOW)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(HIGH)).isEqualTo(Value.highImpedance(4));
        harness.set(A, 3).advance(4);
        assertThat(harness.output(LOW)).isEqualTo(Value.highImpedance(4));
        harness.advance(1);
        assertThat(harness.output(LOW)).isEqualTo(Value.of(4, 9));
    }
}
