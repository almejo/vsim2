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
    void anUndefinedAmountGivesZ() {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(8, "logical-left")).settle();
        harness.set(A, 1).set(AMOUNT, Value.highImpedance(3)).settle();
        assertThat(harness.output(Y)).isEqualTo(Value.highImpedance(8));
    }

    /** Like Logisim's: a defined amount moves every bit of A, Z bits included, and what comes in is defined. */
    @Test
    void zBitsOfTheDataMoveWithTheOthers() {
        // 8 bits: ZZZZ0101 (a byte read from a part that drives only the low half of the bus).
        Value lowNibble = new Value(8, 0b0101, 0xF0, false);
        assertThat(shiftedValue("logical-left", 4, lowNibble)).isEqualTo(Value.of(8, 0x50));
        assertThat(shiftedValue("logical-left", 1, lowNibble)).isEqualTo(new Value(8, 0b1010, 0xE0, false));
        assertThat(shiftedValue("logical-right", 2, new Value(8, 0b0101_0000, 0x0F, false)))
                .isEqualTo(new Value(8, 0b0001_0100, 0x03, false));
        // The sign bit is Z: an arithmetic shift copies it, Z.
        assertThat(shiftedValue("arithmetic-right", 2, new Value(8, 0, 0x80, false)))
                .isEqualTo(new Value(8, 0, 0xE0, false));
        assertThat(shiftedValue("rotate-left", 4, lowNibble)).isEqualTo(new Value(8, 0x50, 0x0F, false));
        assertThat(shiftedValue("rotate-right", 4, lowNibble)).isEqualTo(new Value(8, 0x50, 0x0F, false));
        assertThat(shiftedValue("logical-left", 0, lowNibble)).isEqualTo(lowNibble);
        harnessAllZ();
    }

    /** The lbu of LRV32IM: a byte with the upper bits floating, moved left 24 and back right 24, is the byte. */
    @Test
    void aByteWithTheRestFloatingComesBackDefined() {
        Value byteOnTheBus = new Value(32, 0x35, 0xFFFF_FF00L, false);
        Value left = shiftedValue(32, "logical-left", 24, byteOnTheBus);
        assertThat(left).isEqualTo(Value.of(32, 0x3500_0000L));
        assertThat(shiftedValue(32, "logical-right", 24, left)).isEqualTo(Value.of(32, 0x35));
    }

    private static void harnessAllZ() {
        assertThat(shiftedValue("logical-left", 1, Value.highImpedance(8))).isEqualTo(new Value(8, 0, 0xFE, false));
    }

    private static Value shiftedValue(String shiftType, int amount, Value data) {
        return shiftedValue(8, shiftType, amount, data);
    }

    private static Value shiftedValue(int bits, String shiftType, int amount, Value data) {
        BlockHarness harness = new BlockHarness(ArithmeticTypes.SHIFTER, parameters(bits, shiftType)).settle();
        harness.set(AMOUNT, amount).set(A, data).settle();
        return harness.output(Y);
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
