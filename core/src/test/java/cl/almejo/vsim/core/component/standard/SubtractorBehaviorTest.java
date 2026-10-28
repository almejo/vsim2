package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubtractorBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int BORROW_IN = 2;
    private static final int DIFFERENCE = 3;
    private static final int BORROW_OUT = 4;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    @Test
    void theSubtractorIsAnArithmeticBlockLikeTheAdder() {
        ComponentType subtractor = StandardComponentTypes.registry().require(ArithmeticTypes.SUBTRACTOR);
        assertThat(subtractor.category()).isEqualTo(ComponentCategory.ARITHMETIC);
        assertThat(subtractor.parameters()).extracting(parameter -> parameter.name()).containsExactly("width", "delay");
        assertThat(subtractor.pins()).extracting(PinSpec::name).containsExactly("A", "B", "Bin", "D", "Bout");
        assertThat(subtractor.pins()).extracting(PinSpec::width).containsExactly(8, 8, 1, 8, 1);
        assertThat(subtractor.pin(BORROW_IN).position().y()).isZero();
        assertThat(subtractor.pin(BORROW_OUT).position().y()).isEqualTo(subtractor.size().height());
        assertThat(subtractor.specializedFor(width(32)).size()).isEqualTo(subtractor.size());
    }

    @Test
    void subtractsEveryFourBitPairWithAndWithoutBorrow() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SUBTRACTOR, width(4)).settle();
        for (int first = 0; first < 16; first++) {
            for (int second = 0; second < 16; second++) {
                for (int borrow = 0; borrow <= 1; borrow++) {
                    harness.set(A, first).set(B, second).set(BORROW_IN, borrow).settle();
                    int total = first - second - borrow;
                    String operation = first + " - " + second + " - " + borrow;
                    assertThat(harness.output(DIFFERENCE)).as(operation).isEqualTo(Value.of(4, total & 0xF));
                    assertThat(harness.output(BORROW_OUT)).as(operation).isEqualTo(Value.of(total < 0));
                }
            }
        }
    }

    @Test
    void thirtyTwoBitsBorrowWithoutLeaking() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SUBTRACTOR, width(32)).settle();
        harness.set(A, 0).set(B, 1).settle();
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        assertThat(harness.output(BORROW_OUT)).isEqualTo(Value.ONE);
        harness.set(A, 0).set(B, 0xFFFF_FFFFL).set(BORROW_IN, 1).settle();
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.of(32, 0));
        assertThat(harness.output(BORROW_OUT)).isEqualTo(Value.ONE);
    }

    @Test
    void anUnconnectedBorrowInCountsAsZeroAndUndefinedDataGivesZ() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SUBTRACTOR, width(4), BORROW_IN).settle();
        harness.set(A, 5).set(B, 2).settle();
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.of(4, 3));
        harness.set(A, Value.highImpedance(4)).settle();
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(BORROW_OUT)).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void theOutputsChangeExactlyTheDelayAfterAnInput() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SUBTRACTOR,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 7)).settle();
        harness.set(A, 5).advance(6);
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.of(4, 0));
        harness.advance(1);
        assertThat(harness.output(DIFFERENCE)).isEqualTo(Value.of(4, 5));
    }
}
