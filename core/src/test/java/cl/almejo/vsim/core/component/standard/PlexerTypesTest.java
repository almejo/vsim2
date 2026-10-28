package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlexerTypesTest {

    private static Map<String, Object> demux(int outputs, int width) {
        return Map.of("outputs", String.valueOf(outputs), StandardComponentTypes.WIDTH, width);
    }

    private static Map<String, Object> encoder(int inputs) {
        return Map.of("inputs", String.valueOf(inputs));
    }

    @Test
    void theDemuxHasDataAndSelectInAndOneOutputPerChoice() {
        ComponentType type = StandardComponentTypes.registry().require("demux");
        assertThat(type.category()).isEqualTo(ComponentCategory.PLEXERS);
        assertThat(type.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("width", "outputs", "delay");
        assertThat(type.parameters().get(1).choices()).containsExactly("2", "4", "8");
        assertThat(type.pins()).extracting(PinSpec::name).containsExactly("D", "S", "Y0", "Y1");
        ComponentType eight = type.specializedFor(demux(8, 4));
        assertThat(eight.pins()).extracting(PinSpec::name)
                .containsExactly("D", "S", "Y0", "Y1", "Y2", "Y3", "Y4", "Y5", "Y6", "Y7");
        assertThat(eight.pin(1).width()).isEqualTo(3);
        assertThat(eight.pin(2).width()).isEqualTo(4);
        assertThat(eight.pin(1).position().y()).isEqualTo(eight.size().height());
    }

    @Test
    void theDemuxSendsDToTheSelectedOutputAndZeroToTheOthers() {
        for (int outputs : new int[]{2, 4, 8}) {
            BlockHarness harness = new BlockHarness("demux", demux(outputs, 4)).settle();
            harness.set(0, 11);
            for (int select = 0; select < outputs; select++) {
                harness.set(1, select).settle();
                for (int output = 0; output < outputs; output++) {
                    assertThat(harness.output(2 + output)).as(outputs + " outputs, S = " + select + ", Y" + output)
                            .isEqualTo(Value.of(4, output == select ? 11 : 0));
                }
            }
        }
    }

    @Test
    void anUndefinedSelectGivesEveryOutputZAndUndefinedDataOnlyTheSelectedOne() {
        BlockHarness harness = new BlockHarness("demux", demux(4, 4)).settle();
        harness.set(0, 5).set(1, 2).settle();
        harness.release(1).settle();
        for (int output = 0; output < 4; output++) {
            assertThat(harness.output(2 + output)).isEqualTo(Value.highImpedance(4));
        }
        harness.set(1, 2).release(0).settle();
        assertThat(harness.output(4)).isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(2)).isEqualTo(Value.of(4, 0));
        harness.conflict(1).settle();
        assertThat(harness.output(2)).isEqualTo(Value.highImpedance(4));
    }

    @Test
    void thePriorityEncoderGivesTheHighestSetInputAndValid() {
        for (int inputs : new int[]{2, 4, 8, 16}) {
            ComponentType type = StandardComponentTypes.registry().require("priority-encoder")
                    .specializedFor(encoder(inputs));
            int bits = Integer.numberOfTrailingZeros(inputs);
            assertThat(type.pinCount()).isEqualTo(inputs + 2);
            assertThat(type.pin(inputs).name()).isEqualTo("A");
            assertThat(type.pin(inputs).width()).isEqualTo(bits);
            assertThat(type.pin(inputs + 1).name()).isEqualTo("V");
            BlockHarness harness = new BlockHarness("priority-encoder", encoder(inputs)).settle();
            assertThat(harness.output(inputs)).isEqualTo(Value.of(bits, 0));
            assertThat(harness.output(inputs + 1)).isEqualTo(Value.ZERO);
            for (int index = 0; index < inputs; index++) {
                harness.set(index, 1).settle();
                assertThat(harness.output(inputs)).isEqualTo(Value.of(bits, index));
                assertThat(harness.output(inputs + 1)).isEqualTo(Value.ONE);
            }
            harness.set(0, 0).set(inputs - 1, 0).settle();
            assertThat(harness.output(inputs)).isEqualTo(Value.of(bits, inputs - 2));
        }
    }

    @Test
    void anUnconnectedInputCountsAsZeroAndANoiseAboveTheWinnerIsUndefined() {
        BlockHarness harness = new BlockHarness("priority-encoder", encoder(4), 3).settle();
        harness.set(1, 1).settle();
        assertThat(harness.output(4)).isEqualTo(Value.of(2, 1));
        assertThat(harness.output(5)).isEqualTo(Value.ONE);
        harness.set(2, 1).conflict(1).settle();
        assertThat(harness.output(4)).isEqualTo(Value.of(2, 2));
        harness.set(2, 0).settle();
        assertThat(harness.output(4)).isEqualTo(Value.highImpedance(2));
        assertThat(harness.output(5)).isEqualTo(Value.highImpedance(1));
    }

    @Test
    void theMuxWithEightInputsPicksEachOne() {
        Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, 4, "inputs", "8");
        BlockHarness harness = new BlockHarness(StandardComponentTypes.MUX, parameters).settle();
        for (int index = 0; index < 8; index++) {
            harness.set(index, index + 3);
        }
        for (int select = 0; select < 8; select++) {
            harness.set(8, select).settle();
            assertThat(harness.output(9)).isEqualTo(Value.of(4, select + 3));
        }
        assertThat(harness.type().pin(8).width()).isEqualTo(3);
        ComponentType two = StandardComponentTypes.registry().require(StandardComponentTypes.MUX);
        for (int index = 0; index < 2; index++) {
            assertThat(harness.type().pin(index).position()).isEqualTo(two.pin(index).position());
        }
        assertThat(harness.type().pin(9).position()).isEqualTo(two.pin(3).position());
    }
}
