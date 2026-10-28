package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DividerBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int QUOTIENT = 2;
    private static final int REMAINDER = 3;
    private static final int ERROR = 4;

    private static Map<String, Object> parameters(int bits, String signedness) {
        return Map.of(StandardComponentTypes.WIDTH, bits, StandardComponentTypes.SIGNEDNESS, signedness);
    }

    @Test
    void pinsAreABQuotientRemainderAndTheZeroFlag() {
        assertThat(StandardComponentTypes.registry().require(ArithmeticTypes.DIVIDER).pins())
                .extracting(PinSpec::name).containsExactly("A", "B", "Q", "R", "E");
    }

    @Test
    void unsignedDivision() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.DIVIDER,
                parameters(8, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        harness.set(A, 250).set(B, 7).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.of(8, 35));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.of(8, 5));
        assertThat(harness.output(ERROR)).isEqualTo(Value.ZERO);
        harness.set(A, 0xFF).set(B, 0x80).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.of(8, 1));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.of(8, 0x7F));
    }

    @Test
    void dividingByZeroGivesZeroTheDividendAndTheFlag() {
        for (String signedness : new String[]{StandardComponentTypes.SIGNEDNESS_UNSIGNED,
                StandardComponentTypes.SIGNEDNESS_SIGNED}) {
            BlockHarness harness = new BlockHarness(ArithmeticTypes.DIVIDER, parameters(8, signedness)).settle();
            harness.set(A, 0xF9).set(B, 0).settle();
            assertThat(harness.output(QUOTIENT)).as(signedness).isEqualTo(Value.of(8, 0));
            assertThat(harness.output(REMAINDER)).as(signedness).isEqualTo(Value.of(8, 0xF9));
            assertThat(harness.output(ERROR)).as(signedness).isEqualTo(Value.ONE);
        }
    }

    @Test
    void signedDivisionTruncatesTowardZeroWithTheDividendsSignOnTheRemainder() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.DIVIDER,
                parameters(8, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        harness.set(A, 0xF9).set(B, 2).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.of(8, 0xFD));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.of(8, 0xFF));
        harness.set(A, 7).set(B, 0xFE).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.of(8, 0xFD));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.of(8, 1));
    }

    @Test
    void minimumDividedByMinusOneWrapsAround() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.DIVIDER,
                parameters(8, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        harness.set(A, 0x80).set(B, 0xFF).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.of(8, 0x80));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.of(8, 0));
        assertThat(harness.output(ERROR)).isEqualTo(Value.ZERO);
    }

    @Test
    void thirtyTwoBitUnsignedAndSigned() {
        BlockHarness unsigned = new BlockHarness(ArithmeticTypes.DIVIDER,
                parameters(32, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        unsigned.set(A, 0xFFFF_FFFFL).set(B, 2).settle();
        assertThat(unsigned.output(QUOTIENT)).isEqualTo(Value.of(32, 0x7FFF_FFFFL));
        assertThat(unsigned.output(REMAINDER)).isEqualTo(Value.of(32, 1));
        BlockHarness signed = new BlockHarness(ArithmeticTypes.DIVIDER,
                parameters(32, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        signed.set(A, 0x8000_0000L).set(B, 0xFFFF_FFFFL).settle();
        assertThat(signed.output(QUOTIENT)).isEqualTo(Value.of(32, 0x8000_0000L));
    }

    @Test
    void undefinedDataGivesZOnEveryOutput() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.DIVIDER, Map.of(StandardComponentTypes.WIDTH, 4))
                .settle();
        harness.set(A, 9).set(B, Value.highImpedance(4)).settle();
        assertThat(harness.output(QUOTIENT)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(REMAINDER)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(ERROR)).isEqualTo(Value.HIGH_IMPEDANCE);
    }
}
