package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The bit extender and the bit selector of «Cableado». */
class WiringTypesTest {

    private static final int INPUT = 0;
    private static final int OUTPUT = 1;
    private static final int SELECT = 1;
    private static final int SELECTED = 2;

    private static Map<String, Object> extender(int inputWidth, int outputWidth, String fill) {
        return Map.of("inputWidth", inputWidth, "outputWidth", outputWidth, "fill", fill);
    }

    private static Map<String, Object> selector(int inputWidth, int outputWidth) {
        return Map.of("inputWidth", inputWidth, "outputWidth", outputWidth);
    }

    @Test
    void bothPartsLiveInCableadoAndTheExtenderHasNoDelay() {
        ComponentType extender = StandardComponentTypes.registry().require(WiringTypes.BIT_EXTENDER);
        ComponentType selector = StandardComponentTypes.registry().require(WiringTypes.BIT_SELECTOR);
        assertThat(extender.category()).isEqualTo(ComponentCategory.WIRING);
        assertThat(selector.category()).isEqualTo(ComponentCategory.WIRING);
        assertThat(extender.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("inputWidth", "outputWidth", "fill");
        assertThat(selector.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("inputWidth", "outputWidth", "delay");
        assertThat(extender.pins()).extracting(PinSpec::width).containsExactly(8, 16);
    }

    @Test
    void extendsWithZerosFromEightToSixteenBits() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_EXTENDER, extender(8, 16, "zeros"));
        harness.set(INPUT, 0xF5L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(16, 0x00F5L));
    }

    @Test
    void extendsWithTheSignBitFromEightToSixteenBits() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_EXTENDER, extender(8, 16, "sign"));
        harness.set(INPUT, 0xF5L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(16, 0xFFF5L));
        harness.set(INPUT, 0x75L).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(16, 0x0075L));
    }

    @Test
    void truncatingKeepsTheLowBitsWhateverTheFill() {
        for (String fill : new String[] {"zeros", "sign"}) {
            BlockHarness harness = new BlockHarness(WiringTypes.BIT_EXTENDER, extender(8, 3, fill));
            harness.set(INPUT, 0b1010_1101L).settle();
            assertThat(harness.output(OUTPUT)).as(fill).isEqualTo(Value.of(3, 0b101L));
        }
    }

    @Test
    void anUndefinedInputDrivesTheOutputAllZ() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_EXTENDER, extender(8, 16, "sign"));
        harness.release(INPUT).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(16));
        harness.conflict(INPUT).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(16));
    }

    @Test
    void theExtenderOutputFollowsTheInputWithoutDelay() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_EXTENDER, extender(4, 8, "zeros"));
        harness.set(INPUT, 0b1001L).advance(0);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(8, 0b1001L));
    }

    @Test
    void theSelectorTakesEightGroupsOfOneBit() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_SELECTOR, selector(8, 1));
        assertThat(harness.type().pins()).extracting(PinSpec::width).containsExactly(8, 3, 1);
        harness.set(INPUT, 0b1000_0101L);
        for (int group = 0; group < 8; group++) {
            harness.set(SELECT, group).settle();
            assertThat(harness.output(SELECTED)).as("group " + group)
                    .isEqualTo(Value.of(1, (0b1000_0101L >> group) & 1L));
        }
    }

    @Test
    void theSelectorTakesNibblesAndTheLastPartialGroupIsPaddedWithZeros() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_SELECTOR, selector(10, 4));
        assertThat(harness.type().pins()).extracting(PinSpec::width).containsExactly(10, 2, 4);
        harness.set(INPUT, 0b11_0110_1001L);
        harness.set(SELECT, 0).settle();
        assertThat(harness.output(SELECTED)).isEqualTo(Value.of(4, 0b1001L));
        harness.set(SELECT, 1).settle();
        assertThat(harness.output(SELECTED)).isEqualTo(Value.of(4, 0b0110L));
        harness.set(SELECT, 2).settle();
        assertThat(harness.output(SELECTED)).as("two real bits, two zeros").isEqualTo(Value.of(4, 0b0011L));
        harness.set(SELECT, 3).settle();
        assertThat(harness.output(SELECTED)).as("past the end").isEqualTo(Value.of(4, 0L));
    }

    @Test
    void aSingleGroupStillHasAOneBitSelectInput() {
        ComponentType type = StandardComponentTypes.registry().require(WiringTypes.BIT_SELECTOR)
                .specializedFor(selector(4, 8));
        assertThat(type.pins()).extracting(PinSpec::width).containsExactly(4, 1, 8);
    }

    @Test
    void anUndefinedSelectOrInputDrivesTheOutputAllZ() {
        BlockHarness harness = new BlockHarness(WiringTypes.BIT_SELECTOR, selector(8, 4));
        harness.set(INPUT, 0xABL).release(SELECT).settle();
        assertThat(harness.output(SELECTED)).isEqualTo(Value.highImpedance(4));
        harness.set(SELECT, 1).release(INPUT).settle();
        assertThat(harness.output(SELECTED)).isEqualTo(Value.highImpedance(4));
    }
}
