package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdderBehaviorTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int CARRY_IN = 2;
    private static final int SUM = 3;
    private static final int CARRY_OUT = 4;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    @Test
    void theAdderIsABlockWithBusesLeftCarryInOnTopAndCarryOutAtTheBottom() {
        ComponentType adder = StandardComponentTypes.registry().require(StandardComponentTypes.ADDER);
        assertThat(adder.category()).isEqualTo(ComponentCategory.BLOCKS);
        assertThat(adder.parameters()).extracting(parameter -> parameter.name()).containsExactly("width", "delay");
        assertThat(adder.pins()).extracting(PinSpec::name).containsExactly("A", "B", "Cin", "S", "Cout");
        assertThat(adder.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT, PinDirection.OUTPUT);
        assertThat(adder.pins()).extracting(PinSpec::width).containsExactly(4, 4, 1, 4, 1);
        int right = adder.size().width();
        int bottom = adder.size().height();
        assertThat(adder.pin(A).position().x()).isZero();
        assertThat(adder.pin(B).position().x()).isZero();
        assertThat(adder.pin(CARRY_IN).position().y()).isZero();
        assertThat(adder.pin(SUM).position().x()).isEqualTo(right);
        assertThat(adder.pin(CARRY_OUT).position().y()).isEqualTo(bottom);

        ComponentType wide = adder.specializedFor(width(32));
        assertThat(wide.pins()).extracting(PinSpec::width).containsExactly(32, 32, 1, 32, 1);
        assertThat(wide.size()).isEqualTo(adder.size());
        assertThat(wide.pins()).extracting(PinSpec::position).isEqualTo(adder.pins().stream()
                .map(PinSpec::position).toList());
        ComponentType single = adder.specializedFor(width(1));
        assertThat(single.pins()).extracting(PinSpec::width).containsOnly(1);
        assertThat(single.pin(SUM).position()).isEqualTo(new GridPoint(right, adder.pin(SUM).position().y()));
    }

    @Test
    void addsEveryFourBitPairWithAndWithoutCarry() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER, width(4)).settle();
        for (int first = 0; first < 16; first++) {
            for (int second = 0; second < 16; second++) {
                for (int carry = 0; carry <= 1; carry++) {
                    harness.set(A, first).set(B, second).set(CARRY_IN, carry).settle();
                    int total = first + second + carry;
                    String operation = first + " + " + second + " + " + carry;
                    assertThat(harness.output(SUM)).as(operation).isEqualTo(Value.of(4, total & 0xF));
                    assertThat(harness.output(CARRY_OUT)).as(operation).isEqualTo(Value.of(total > 15));
                }
            }
        }
    }

    @Test
    void aOneBitAdderIsAFullAdder() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER, width(1)).settle();
        harness.set(A, 1).set(B, 1).set(CARRY_IN, 1).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.ONE);
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ONE);
        harness.set(A, 1).set(B, 0).set(CARRY_IN, 0).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.ONE);
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ZERO);
    }

    @Test
    void thirtyTwoBitsCarryOutOfTheTopBitWithoutLeakingIntoTheSum() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER, width(32)).settle();
        harness.set(A, 0xFFFF_FFFFL).set(B, 0xFFFF_FFFFL).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(32, 0xFFFF_FFFEL));
        assertThat(harness.output(SUM).bits()).isEqualTo(0xFFFF_FFFEL);
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ONE);

        harness.set(CARRY_IN, 1).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ONE);

        harness.set(A, 0x8000_0000L).set(B, 0x8000_0000L).set(CARRY_IN, 0).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(32, 0));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ONE);

        harness.set(A, 0x7FFF_FFFFL).set(B, 1).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(32, 0x8000_0000L));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ZERO);
    }

    @Test
    void undefinedDataGivesZOnEveryOutput() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER, width(4)).settle();
        harness.set(A, Value.highImpedance(4)).set(B, 3).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.HIGH_IMPEDANCE);

        harness.set(A, new Value(4, 0b0001, 0b1000, false)).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.HIGH_IMPEDANCE);

        harness.set(A, 2).conflict(B).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.HIGH_IMPEDANCE);

        harness.set(B, 3).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(4, 5));
    }

    @Test
    void anUnconnectedOrReleasedCarryInCountsAsZeroAndAConflictOnItIsUndefined() {
        BlockHarness unconnected = new BlockHarness(StandardComponentTypes.ADDER, width(4), CARRY_IN).settle();
        unconnected.set(A, 9).set(B, 9).settle();
        assertThat(unconnected.output(CARRY_IN)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(unconnected.output(SUM)).isEqualTo(Value.of(4, 2));
        assertThat(unconnected.output(CARRY_OUT)).isEqualTo(Value.ONE);

        BlockHarness released = new BlockHarness(StandardComponentTypes.ADDER, width(4)).settle();
        released.set(A, 1).set(B, 1).set(CARRY_IN, 1).settle();
        assertThat(released.output(SUM)).isEqualTo(Value.of(4, 3));
        released.release(CARRY_IN).settle();
        assertThat(released.output(SUM)).isEqualTo(Value.of(4, 2));

        released.conflict(CARRY_IN).settle();
        assertThat(released.output(SUM)).isEqualTo(Value.highImpedance(4));
        assertThat(released.output(CARRY_OUT)).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void theOutputsChangeExactlyTheDelayAfterAnInput() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 7)).settle();
        assertThat(harness.output(SUM)).isEqualTo(Value.of(4, 0));

        harness.set(A, 5).advance(6);
        assertThat(harness.output(SUM)).isEqualTo(Value.of(4, 0));
        harness.advance(1);
        assertThat(harness.output(SUM)).isEqualTo(Value.of(4, 5));
    }

    @Test
    void anAdderComputesOnInitialization() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.ADDER, width(4)).advance(1);
        assertThat(harness.output(SUM)).isEqualTo(Value.of(4, 0));
        assertThat(harness.output(CARRY_OUT)).isEqualTo(Value.ZERO);
        assertThat(harness.state()).isEqualTo(VisibleState.NONE);
    }
}
