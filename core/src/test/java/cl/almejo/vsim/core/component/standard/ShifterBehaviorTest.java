package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShifterBehaviorTest {

    private static final int A = 0;
    private static final int AMOUNT = 1;
    private static final int Y = 2;

    private static Map<String, Object> parameters(int bits, String shiftType) {
        return Map.of(StandardComponentTypes.WIDTH, bits, ArithmeticTypes.SHIFT_TYPE, shiftType);
    }

    private static Value shifted(String shiftType, int amount, int data) {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(8, shiftType)).settle();
        harness.set(A, data).set(AMOUNT, amount).settle();
        return harness.output(Y);
    }

    @Test
    void theAmountHasAsManyBitsAsNeededToCountZeroToNMinusOne() {
        ComponentType shifter = StandardComponentTypes.registry().require(ArithmeticTypes.SHIFTER);
        assertThat(shifter.pins()).extracting(PinSpec::name).containsExactly("A", "S", "Y");
        assertThat(shifter.pins()).extracting(PinSpec::width).containsExactly(8, 3, 8);
        assertThat(shifter.specializedFor(Map.of("width", 32)).pin(AMOUNT).width()).isEqualTo(5);
        assertThat(shifter.specializedFor(Map.of("width", 5)).pin(AMOUNT).width()).isEqualTo(3);
        assertThat(shifter.specializedFor(Map.of("width", 1)).pin(AMOUNT).width()).isEqualTo(1);
    }

    @Test
    void everyTypeWithAmountZeroLeavesTheDataAlone() {
        for (String shiftType : ArithmeticTypes.SHIFT_TYPES) {
            assertThat(shifted(shiftType, 0, 0xA5)).as(shiftType).isEqualTo(Value.of(8, 0xA5));
        }
    }

    @Test
    void everyTypeWithAmountNMinusOne() {
        assertThat(shifted("logical-left", 7, 0xA5)).isEqualTo(Value.of(8, 0x80));
        assertThat(shifted("logical-right", 7, 0xA5)).isEqualTo(Value.of(8, 1));
        assertThat(shifted("arithmetic-right", 7, 0xA5)).isEqualTo(Value.of(8, 0xFF));
        assertThat(shifted("arithmetic-right", 7, 0x25)).isEqualTo(Value.of(8, 0));
        assertThat(shifted("rotate-left", 7, 0xA5)).isEqualTo(Value.of(8, 0xD2));
        assertThat(shifted("rotate-right", 7, 0xA5)).isEqualTo(Value.of(8, 0x4B));
    }

    @Test
    void middleAmounts() {
        assertThat(shifted("logical-left", 1, 0x81)).isEqualTo(Value.of(8, 0x02));
        assertThat(shifted("logical-right", 1, 0x81)).isEqualTo(Value.of(8, 0x40));
        assertThat(shifted("arithmetic-right", 1, 0x80)).isEqualTo(Value.of(8, 0xC0));
        assertThat(shifted("rotate-left", 1, 0x81)).isEqualTo(Value.of(8, 0x03));
        assertThat(shifted("rotate-right", 1, 0x81)).isEqualTo(Value.of(8, 0xC0));
    }

    @Test
    void anAmountBeyondTheWidthOnANonPowerOfTwoWidth() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(5, "logical-left")).settle();
        harness.set(A, 0b11111).set(AMOUNT, 6).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(5, 0));
        BlockHarness rotating = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(5, "rotate-left")).settle();
        rotating.set(A, 0b00011).set(AMOUNT, 6).settle();
        assertThat(rotating.output(Y)).isEqualTo(Value.of(5, 0b00110));
        BlockHarness arithmetic = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(5, "arithmetic-right")).settle();
        arithmetic.set(A, 0b10000).set(AMOUNT, 7).settle();
        assertThat(arithmetic.output(Y)).isEqualTo(Value.of(5, 0b11111));
    }

    @Test
    void thirtyTwoBits() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(32, "arithmetic-right")).settle();
        harness.set(A, 0x8000_0000L).set(AMOUNT, 31).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        BlockHarness left = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(32, "logical-left")).settle();
        left.set(A, 0xFFFF_FFFFL).set(AMOUNT, 4).settle();
        assertThat(left.output(Y)).isEqualTo(Value.of(32, 0xFFFF_FFF0L));
    }

    @Test
    void undefinedInputsGiveZ() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(8, "logical-left")).settle();
        harness.set(A, 1).set(AMOUNT, Value.highImpedance(3)).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(8));
        harness.set(AMOUNT, 1).set(A, Value.highImpedance(8)).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(8));
    }

    @Test
    void theOutputChangesExactlyTheDelayAfterAnInput() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER,
                Map.of(StandardComponentTypes.WIDTH, 8, "delay", 4)).settle();
        harness.set(A, 3).set(AMOUNT, 1).advance(3);
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 0));
        harness.advance(1);
        assertThat(harness.output(Y)).isEqualTo(Value.of(8, 6));
    }
}
