package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AluBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int OPERATION = 2;
    private static final int RESULT = 3;
    private static final int ZERO = 4;
    private static final int NEGATIVE = 5;
    private static final int CARRY = 6;
    private static final int OVERFLOW = 7;

    private static final int ADD = 0b000;
    private static final int SUBTRACT = 0b001;
    private static final int SHIFT_LEFT = 0b110;
    private static final int SHIFT_RIGHT = 0b111;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    /** Y, Z, N, C, V the way plain Java arithmetic gives them for 4-bit operands. */
    private record Expected(int result, boolean carry, boolean overflow) {

        static Expected of(int operation, int first, int second) {
            int signedFirst = first >= 8 ? first - 16 : first;
            int signedSecond = second >= 8 ? second - 16 : second;
            return switch (operation) {
                case 0 -> {
                    int signedTotal = signedFirst + signedSecond;
                    yield new Expected((first + second) & 0xF, first + second > 15,
                            signedTotal < -8 || signedTotal > 7);
                }
                case 1 -> {
                    int signedDifference = signedFirst - signedSecond;
                    yield new Expected((first - second) & 0xF, first < second,
                            signedDifference < -8 || signedDifference > 7);
                }
                case 2 -> new Expected(first & second, false, false);
                case 3 -> new Expected(first | second, false, false);
                case 4 -> new Expected(first ^ second, false, false);
                case 5 -> new Expected(~first & 0xF, false, false);
                case 6 -> new Expected((first << 1) & 0xF, (first & 0b1000) != 0, false);
                case 7 -> new Expected(first >>> 1, (first & 1) != 0, false);
                default -> throw new IllegalArgumentException("operation " + operation);
            };
        }
    }

    @Test
    void theAluIsABlockWithDataLeftOperationAtTheBottomAndResultAndFlagsRight() {
        ComponentType alu = StandardComponentTypes.registry().require(StandardComponentTypes.ALU);
        assertThat(alu.category()).isEqualTo(ComponentCategory.ARITHMETIC);
        assertThat(alu.parameters()).extracting(parameter -> parameter.name()).containsExactly("width", "delay");
        assertThat(alu.pins()).extracting(PinSpec::name).containsExactly("A", "B", "OP", "Y", "Z", "N", "C", "V");
        assertThat(alu.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT, PinDirection.OUTPUT,
                PinDirection.OUTPUT, PinDirection.OUTPUT, PinDirection.OUTPUT);
        assertThat(alu.pins()).extracting(PinSpec::width).containsExactly(8, 8, 3, 8, 1, 1, 1, 1);
        int right = alu.size().width();
        int bottom = alu.size().height();
        assertThat(alu.pin(A).position().x()).isZero();
        assertThat(alu.pin(B).position().x()).isZero();
        assertThat(alu.pin(OPERATION).position().y()).isEqualTo(bottom);
        for (int output = RESULT; output <= OVERFLOW; output++) {
            assertThat(alu.pin(output).position().x()).isEqualTo(right);
        }

        ComponentType wide = alu.specializedFor(width(32));
        assertThat(wide.pins()).extracting(PinSpec::width).containsExactly(32, 32, 3, 32, 1, 1, 1, 1);
        assertThat(wide.size()).isEqualTo(alu.size());
        ComponentType single = alu.specializedFor(width(1));
        assertThat(single.pins()).extracting(PinSpec::width).containsExactly(1, 1, 3, 1, 1, 1, 1, 1);
    }

    @Test
    void everyOperationOnEveryFourBitPairMatchesJava() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(4)).settle();
        for (int operation = 0; operation < 8; operation++) {
            harness.set(OPERATION, operation);
            for (int first = 0; first < 16; first++) {
                for (int second = 0; second < 16; second++) {
                    harness.set(A, first).set(B, second).settle();
                    Expected expected = Expected.of(operation, first, second);
                    String description = "OP " + operation + " A " + first + " B " + second;
                    assertThat(harness.output(RESULT)).as(description).isEqualTo(Value.of(4, expected.result()));
                    assertThat(harness.output(ZERO)).as(description).isEqualTo(Value.of(expected.result() == 0));
                    assertThat(harness.output(NEGATIVE)).as(description)
                            .isEqualTo(Value.of((expected.result() & 0b1000) != 0));
                    assertThat(harness.output(CARRY)).as(description).isEqualTo(Value.of(expected.carry()));
                    assertThat(harness.output(OVERFLOW)).as(description).isEqualTo(Value.of(expected.overflow()));
                }
            }
            assertThat(harness.state()).isEqualTo(new VisibleState.AluState(operation));
        }
    }

    @Test
    void thirtyTwoBitsCarryBorrowAndOverflowAtTheLimits() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(32)).settle();
        harness.set(OPERATION, ADD).set(A, 0xFFFF_FFFFL).set(B, 1).settle();
        assertFlags(harness, 0, true, false);
        assertThat(harness.output(ZERO)).isEqualTo(Value.ONE);

        harness.set(A, 0x7FFF_FFFFL).set(B, 1).settle();
        assertFlags(harness, 0x8000_0000L, false, true);
        assertThat(harness.output(NEGATIVE)).isEqualTo(Value.ONE);

        harness.set(A, 0x8000_0000L).set(B, 0x8000_0000L).settle();
        assertFlags(harness, 0, true, true);

        harness.set(OPERATION, SUBTRACT).set(A, 0).set(B, 1).settle();
        assertFlags(harness, 0xFFFF_FFFFL, true, false);

        harness.set(A, 0x8000_0000L).set(B, 1).settle();
        assertFlags(harness, 0x7FFF_FFFFL, false, true);

        harness.set(A, 0x7FFF_FFFFL).set(B, 0xFFFF_FFFFL).settle();
        assertFlags(harness, 0x8000_0000L, true, true);

        harness.set(A, 5).set(B, 5).settle();
        assertFlags(harness, 0, false, false);

        harness.set(OPERATION, SHIFT_LEFT).set(A, 0x8000_0001L).settle();
        assertFlags(harness, 0x0000_0002L, true, false);

        harness.set(OPERATION, SHIFT_RIGHT).set(A, 0x8000_0001L).settle();
        assertFlags(harness, 0x4000_0000L, true, false);
        assertThat(harness.output(NEGATIVE)).isEqualTo(Value.ZERO);
    }

    private static void assertFlags(BlockHarness harness, long result, boolean carry, boolean overflow) {
        assertThat(harness.output(RESULT).bits()).isEqualTo(result);
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(32, result));
        assertThat(harness.output(CARRY)).isEqualTo(Value.of(carry));
        assertThat(harness.output(OVERFLOW)).isEqualTo(Value.of(overflow));
    }

    @Test
    void aOneBitAluStillAddsAndSubtracts() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(1)).settle();
        harness.set(OPERATION, ADD).set(A, 1).set(B, 1).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(CARRY)).isEqualTo(Value.ONE);
        // -1 + -1 = -2 does not fit in one signed bit.
        assertThat(harness.output(OVERFLOW)).isEqualTo(Value.ONE);

        harness.set(OPERATION, SUBTRACT).set(A, 0).set(B, 1).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.ONE);
        assertThat(harness.output(CARRY)).isEqualTo(Value.ONE);
        assertThat(harness.output(NEGATIVE)).isEqualTo(Value.ONE);
    }

    @Test
    void anUndefinedOperationGivesZEverywhereAndAQuestionMarkState() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(4)).settle();
        harness.set(A, 3).set(B, 2).set(OPERATION, new Value(3, 0b001, 0b100, false)).settle();
        assertAllOutputsUndefined(harness);
        assertThat(harness.state()).isEqualTo(new VisibleState.AluState(-1));

        harness.conflict(OPERATION).settle();
        assertAllOutputsUndefined(harness);
        assertThat(harness.state()).isEqualTo(new VisibleState.AluState(-1));

        harness.set(OPERATION, ADD).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 5));
        assertThat(harness.state()).isEqualTo(new VisibleState.AluState(ADD));

        BlockHarness unconnected = new BlockHarness(StandardComponentTypes.ALU, width(4), OPERATION).settle();
        assertAllOutputsUndefined(unconnected);
        assertThat(unconnected.state()).isEqualTo(new VisibleState.AluState(-1));
    }

    @Test
    void undefinedDataGivesZOnlyWhenTheOperationReadsIt() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(4)).settle();
        harness.set(OPERATION, ADD).set(A, 3).release(B).settle();
        assertAllOutputsUndefined(harness);
        assertThat(harness.state()).isEqualTo(new VisibleState.AluState(ADD));

        harness.set(B, 1).conflict(A).settle();
        assertAllOutputsUndefined(harness);

        // NOT A and the shifts ignore B.
        harness.set(A, 0b0110).release(B).set(OPERATION, 0b101).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 0b1001));
        harness.set(OPERATION, SHIFT_LEFT).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 0b1100));
        harness.set(A, new Value(4, 0b0001, 0b0100, false)).settle();
        assertAllOutputsUndefined(harness);
    }

    private static void assertAllOutputsUndefined(BlockHarness harness) {
        assertThat(harness.output(RESULT)).isEqualTo(Value.highImpedance(4));
        for (int flag = ZERO; flag <= OVERFLOW; flag++) {
            assertThat(harness.output(flag)).as("flag " + flag).isEqualTo(Value.HIGH_IMPEDANCE);
        }
    }

    @Test
    void theOutputsChangeExactlyTheDelayAfterAnInput() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 5)).settle();
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.output(ZERO)).isEqualTo(Value.ONE);

        harness.set(A, 6).advance(4);
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 0));
        harness.advance(1);
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 6));
        assertThat(harness.output(ZERO)).isEqualTo(Value.ZERO);
    }

    @Test
    void anAluComputesOnInitialization() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ALU, width(4)).advance(1);
        assertThat(harness.output(RESULT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.output(ZERO)).isEqualTo(Value.ONE);
        assertThat(harness.state()).isEqualTo(new VisibleState.AluState(ADD));
    }
}
