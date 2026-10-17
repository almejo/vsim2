package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecoderBehaviorTest {

    private static final int ADDRESS = 0;
    private static final int ENABLE = 1;
    private static final int FIRST_OUTPUT = 2;

    private static Map<String, Object> bits(int inputBits) {
        return Map.of("inputBits", inputBits);
    }

    private static ComponentType decoder() {
        return StandardComponentTypes.registry().require(StandardComponentTypes.DECODER);
    }

    @Test
    void theDecoderHasItsAddressOnTheLeftEnableAtTheBottomAndOneOutputPerCode() {
        ComponentType standard = decoder();
        assertThat(standard.category()).isEqualTo(ComponentCategory.BLOCKS);
        assertThat(standard.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("inputBits", "delay");
        assertThat(standard.parameters().getFirst().defaultValue()).isEqualTo(2);
        assertThat(standard.parameters().getFirst().minimum()).isEqualTo(1);
        assertThat(standard.parameters().getFirst().maximum()).isEqualTo(4);
        assertThat(standard.pins()).extracting(PinSpec::name).containsExactly("A", "EN", "Y0", "Y1", "Y2", "Y3");
        assertThat(standard.pins()).extracting(PinSpec::width).containsExactly(2, 1, 1, 1, 1, 1);
        assertThat(standard.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.OUTPUT, PinDirection.OUTPUT, PinDirection.OUTPUT,
                PinDirection.OUTPUT);

        int previousHeight = 0;
        for (int inputBits = 1; inputBits <= 4; inputBits++) {
            ComponentType type = standard.specializedFor(bits(inputBits));
            int outputs = 1 << inputBits;
            assertThat(type.pinCount()).isEqualTo(2 + outputs);
            assertThat(type.pin(ADDRESS).width()).isEqualTo(inputBits);
            assertThat(type.pin(ADDRESS).position().x()).isZero();
            assertThat(type.pin(ENABLE).position().y()).isEqualTo(type.size().height());
            List<GridPoint> outputPositions = new ArrayList<>();
            for (int output = 0; output < outputs; output++) {
                PinSpec pin = type.pin(FIRST_OUTPUT + output);
                assertThat(pin.name()).isEqualTo("Y" + output);
                assertThat(pin.position().x()).isEqualTo(type.size().width());
                assertThat(pin.position().y()).isBetween(1, type.size().height() - 1);
                outputPositions.add(pin.position());
            }
            assertThat(outputPositions).doesNotHaveDuplicates();
            assertThat(type.size().height()).isGreaterThan(previousHeight);
            previousHeight = type.size().height();
        }
    }

    @Test
    void everyAddressDrivesExactlyItsOutputWithOneToFourBits() {
        for (int inputBits = 1; inputBits <= 4; inputBits++) {
            BlockHarness harness = new BlockHarness(StandardComponentTypes.DECODER, bits(inputBits)).settle();
            int outputs = 1 << inputBits;
            harness.set(ENABLE, 1);
            for (int address = 0; address < outputs; address++) {
                harness.set(ADDRESS, address).settle();
                for (int output = 0; output < outputs; output++) {
                    assertThat(harness.output(FIRST_OUTPUT + output)).as(inputBits + " bits, A = " + address
                            + ", Y" + output).isEqualTo(Value.of(output == address));
                }
            }
        }
    }

    @Test
    void enableAtZeroTurnsEveryOutputOffEvenWithAnUndefinedAddress() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.DECODER, bits(2)).settle();
        harness.set(ADDRESS, 2).set(ENABLE, 0).settle();
        assertOutputs(harness, 4, Value.ZERO);
        harness.release(ADDRESS).settle();
        assertOutputs(harness, 4, Value.ZERO);
        harness.set(ADDRESS, 1).set(ENABLE, 1).settle();
        assertThat(harness.output(FIRST_OUTPUT + 1)).isEqualTo(Value.ONE);
    }

    @Test
    void anUnconnectedOrReleasedEnableMeansEnabledAndAConflictOnItIsUndefined() {
        BlockHarness unconnected = new BlockHarness(StandardComponentTypes.DECODER, bits(2), ENABLE).settle();
        unconnected.set(ADDRESS, 3).settle();
        assertThat(unconnected.output(ENABLE)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(unconnected.output(FIRST_OUTPUT + 3)).isEqualTo(Value.ONE);
        assertThat(unconnected.output(FIRST_OUTPUT)).isEqualTo(Value.ZERO);

        BlockHarness released = new BlockHarness(StandardComponentTypes.DECODER, bits(2)).settle();
        released.set(ADDRESS, 1).set(ENABLE, 0).settle();
        released.release(ENABLE).settle();
        assertThat(released.output(FIRST_OUTPUT + 1)).isEqualTo(Value.ONE);
        released.conflict(ENABLE).settle();
        assertOutputs(released, 4, Value.HIGH_IMPEDANCE);
    }

    @Test
    void anUndefinedAddressGivesZOnEveryOutput() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.DECODER, bits(3)).set(ENABLE, 1).settle();
        harness.release(ADDRESS).settle();
        assertOutputs(harness, 8, Value.HIGH_IMPEDANCE);
        harness.set(ADDRESS, new Value(3, 0b001, 0b100, false)).settle();
        assertOutputs(harness, 8, Value.HIGH_IMPEDANCE);
        harness.conflict(ADDRESS).settle();
        assertOutputs(harness, 8, Value.HIGH_IMPEDANCE);
        harness.set(ADDRESS, 5).settle();
        assertThat(harness.output(FIRST_OUTPUT + 5)).isEqualTo(Value.ONE);
        assertThat(harness.output(FIRST_OUTPUT + 4)).isEqualTo(Value.ZERO);
    }

    @Test
    void theOutputsChangeExactlyTheDelayAfterAnInputAndOnInitialization() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.DECODER,
                Map.of("inputBits", 1, "delay", 3), ENABLE).advance(3);
        assertThat(harness.output(FIRST_OUTPUT)).isEqualTo(Value.ONE);
        assertThat(harness.output(FIRST_OUTPUT + 1)).isEqualTo(Value.ZERO);
        harness.set(ADDRESS, 1).advance(2);
        assertThat(harness.output(FIRST_OUTPUT)).isEqualTo(Value.ONE);
        harness.advance(1);
        assertThat(harness.output(FIRST_OUTPUT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(FIRST_OUTPUT + 1)).isEqualTo(Value.ONE);
    }

    private static void assertOutputs(BlockHarness harness, int outputs, Value expected) {
        for (int output = 0; output < outputs; output++) {
            assertThat(harness.output(FIRST_OUTPUT + output)).as("Y" + output).isEqualTo(expected);
        }
    }
}
