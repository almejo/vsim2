package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComparatorBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int LESS = 2;
    private static final int EQUAL = 3;
    private static final int GREATER = 4;

    private static Map<String, Object> parameters(int bits, String signedness) {
        return Map.of(StandardComponentTypes.WIDTH, bits, StandardComponentTypes.SIGNEDNESS, signedness);
    }

    @Test
    void theComparatorIsABlockWithDataLeftAndThreeResultsRight() {
        ComponentType comparator = StandardComponentTypes.registry().require(StandardComponentTypes.COMPARATOR);
        assertThat(comparator.category()).isEqualTo(ComponentCategory.ARITHMETIC);
        assertThat(comparator.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("width", "signedness", "delay");
        assertThat(comparator.parameters().get(1).kind()).isEqualTo(ParameterKind.CHOICE);
        assertThat(comparator.parameters().get(1).defaultValue()).isEqualTo(StandardComponentTypes.SIGNEDNESS_UNSIGNED);
        assertThat(comparator.parameters().get(1).choices())
                .isEqualTo(List.of(StandardComponentTypes.SIGNEDNESS_UNSIGNED, StandardComponentTypes.SIGNEDNESS_SIGNED));
        assertThat(comparator.pins()).extracting(PinSpec::name).containsExactly("A", "B", "A<B", "A=B", "A>B");
        assertThat(comparator.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.OUTPUT, PinDirection.OUTPUT, PinDirection.OUTPUT);
        assertThat(comparator.pins()).extracting(PinSpec::width).containsExactly(8, 8, 1, 1, 1);
        int right = comparator.size().width();
        assertThat(comparator.pin(A).position().x()).isZero();
        assertThat(comparator.pin(B).position().x()).isZero();
        for (int output = LESS; output <= GREATER; output++) {
            assertThat(comparator.pin(output).position().x()).isEqualTo(right);
        }
        ComponentType wide = comparator.specializedFor(parameters(32, StandardComponentTypes.SIGNEDNESS_SIGNED));
        assertThat(wide.pins()).extracting(PinSpec::width).containsExactly(32, 32, 1, 1, 1);
        assertThat(wide.size()).isEqualTo(comparator.size());
    }

    @Test
    void comparesEveryFourBitPairUnsignedAndSigned() {
        for (String signedness : List.of(StandardComponentTypes.SIGNEDNESS_UNSIGNED, StandardComponentTypes.SIGNEDNESS_SIGNED)) {
            BlockHarness harness = new BlockHarness(StandardComponentTypes.COMPARATOR, parameters(4, signedness))
                    .settle();
            boolean signed = signedness.equals(StandardComponentTypes.SIGNEDNESS_SIGNED);
            for (int first = 0; first < 16; first++) {
                for (int second = 0; second < 16; second++) {
                    harness.set(A, first).set(B, second).settle();
                    int firstNumber = signed && first >= 8 ? first - 16 : first;
                    int secondNumber = signed && second >= 8 ? second - 16 : second;
                    assertResult(harness, Integer.compare(firstNumber, secondNumber),
                            signedness + " " + first + " vs " + second);
                }
            }
            assertThat(harness.state()).isEqualTo(VisibleState.NONE);
        }
    }

    @Test
    void thirtyTwoBitExtremes() {
        BlockHarness unsigned = new BlockHarness(StandardComponentTypes.COMPARATOR,
                parameters(32, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        unsigned.set(A, 0xFFFF_FFFFL).set(B, 0).settle();
        assertResult(unsigned, 1, "unsigned max vs 0");
        unsigned.set(A, 0x7FFF_FFFFL).set(B, 0x8000_0000L).settle();
        assertResult(unsigned, -1, "unsigned 0x7FFFFFFF vs 0x80000000");
        unsigned.set(A, 0xFFFF_FFFFL).set(B, 0xFFFF_FFFFL).settle();
        assertResult(unsigned, 0, "unsigned max vs max");

        BlockHarness signed = new BlockHarness(StandardComponentTypes.COMPARATOR,
                parameters(32, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        signed.set(A, 0xFFFF_FFFFL).set(B, 0).settle();
        assertResult(signed, -1, "signed -1 vs 0");
        signed.set(A, 0x7FFF_FFFFL).set(B, 0x8000_0000L).settle();
        assertResult(signed, 1, "signed max vs min");
        signed.set(A, 0x8000_0000L).set(B, 0x8000_0000L).settle();
        assertResult(signed, 0, "signed min vs min");
    }

    @Test
    void aOneBitSignedComparatorReadsOneAsMinusOne() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.COMPARATOR,
                parameters(1, StandardComponentTypes.SIGNEDNESS_SIGNED)).settle();
        harness.set(A, 1).set(B, 0).settle();
        assertResult(harness, -1, "signed 1 bit: -1 vs 0");
    }

    @Test
    void undefinedDataGivesZOnEveryOutput() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.COMPARATOR,
                parameters(4, StandardComponentTypes.SIGNEDNESS_UNSIGNED)).settle();
        harness.set(A, 3).release(B).settle();
        assertUndefined(harness);
        harness.set(B, new Value(4, 0b0001, 0b0010, false)).settle();
        assertUndefined(harness);
        harness.set(B, 3).conflict(A).settle();
        assertUndefined(harness);
        harness.set(A, 3).settle();
        assertResult(harness, 0, "recovered");

        BlockHarness unconnected = new BlockHarness(StandardComponentTypes.COMPARATOR,
                parameters(4, StandardComponentTypes.SIGNEDNESS_UNSIGNED), B).settle();
        assertUndefined(unconnected);
    }

    @Test
    void theOutputsChangeExactlyTheDelayAfterAnInputAndOnInitialization() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.COMPARATOR,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 3)).advance(3);
        assertResult(harness, 0, "at start");

        harness.set(A, 2).advance(2);
        assertResult(harness, 0, "before the delay");
        harness.advance(1);
        assertResult(harness, 1, "after the delay");
    }

    private static void assertResult(BlockHarness harness, int comparison, String description) {
        assertThat(harness.output(LESS)).as(description).isEqualTo(Value.of(comparison < 0));
        assertThat(harness.output(EQUAL)).as(description).isEqualTo(Value.of(comparison == 0));
        assertThat(harness.output(GREATER)).as(description).isEqualTo(Value.of(comparison > 0));
    }

    private static void assertUndefined(BlockHarness harness) {
        for (int output = LESS; output <= GREATER; output++) {
            assertThat(harness.output(output)).isEqualTo(Value.HIGH_IMPEDANCE);
        }
    }
}
