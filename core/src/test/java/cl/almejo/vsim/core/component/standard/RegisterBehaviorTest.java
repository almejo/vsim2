package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterBehaviorTest {

    private static final int DATA = 0;
    private static final int ENABLE = 1;
    private static final int CLEAR = 2;
    private static final int CLOCK = 3;
    private static final int OUTPUT = 4;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    /** A register whose inputs are all driven, EN at 1 and the rest at 0 (the harness starts every source at 0). */
    private static BlockHarness enabled(Map<String, Object> parameters) {
        return new BlockHarness(StandardComponentTypes.REGISTER, parameters).set(ENABLE, 1).settle();
    }

    /** A falling clock edge: high, settle, low, settle. */
    private static BlockHarness tick(BlockHarness harness) {
        return harness.set(CLOCK, 1).settle().set(CLOCK, 0).settle();
    }

    @Test
    void theRegisterIsABlockWithDataLeftQRightAndControlAtTheBottom() {
        ComponentType register = StandardComponentTypes.registry().require(StandardComponentTypes.REGISTER);
        assertThat(register.category()).isEqualTo(ComponentCategory.SEQUENTIAL);
        assertThat(register.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("width", "delay", "format");
        assertThat(register.pins()).extracting(PinSpec::name).containsExactly("D", "EN", "CLR", "CLK", "Q");
        assertThat(register.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT);
        assertThat(register.pins()).extracting(PinSpec::width).containsExactly(8, 1, 1, 1, 8);
        assertThat(register.pin(DATA).position().x()).isZero();
        assertThat(register.pin(OUTPUT).position().x()).isEqualTo(register.size().width());
        for (int control : new int[]{ENABLE, CLEAR, CLOCK}) {
            assertThat(register.pin(control).position().y()).isEqualTo(register.size().height());
        }
        ComponentType wide = register.specializedFor(width(32));
        assertThat(wide.pins()).extracting(PinSpec::width).containsExactly(32, 1, 1, 1, 32);
        assertThat(wide.size()).isEqualTo(register.size());
    }

    @Test
    void startsAtZeroAndLoadsDOnTheFallingEdgeOnly() {
        BlockHarness harness = enabled(width(4));
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0)));

        harness.set(DATA, 0xA).settle().set(CLOCK, 1).settle();
        assertThat(harness.output(OUTPUT)).as("a rising edge never changes Q").isEqualTo(Value.of(4, 0));

        harness.set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0xA));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0xA)));

        harness.set(DATA, 0x3).settle();
        assertThat(harness.output(OUTPUT)).as("D alone changes nothing").isEqualTo(Value.of(4, 0xA));
    }

    @Test
    void theDelayParameterIsHonoured() {
        Map<String, Object> parameters = new HashMap<>(width(4));
        parameters.put("delay", 5);
        BlockHarness harness = enabled(parameters);
        harness.set(DATA, 7).set(CLOCK, 1).settle().set(CLOCK, 0).advance(4);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        harness.advance(1);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 7));
    }

    @Test
    void enableLowHoldsAndAnUnconnectedEnableLoads() {
        BlockHarness harness = enabled(width(4));
        harness.set(DATA, 5).set(ENABLE, 1);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 5));
        harness.set(ENABLE, 0).set(DATA, 9);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 5));
        harness.release(ENABLE);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 9));

        BlockHarness unconnected = new BlockHarness(StandardComponentTypes.REGISTER, width(4), ENABLE, CLEAR).settle();
        unconnected.set(DATA, 6);
        tick(unconnected);
        assertThat(unconnected.output(OUTPUT)).isEqualTo(Value.of(4, 6));
    }

    @Test
    void clearIsAsynchronousEvenWhileTheClockIsHighAndWinsOverTheEdge() {
        BlockHarness harness = enabled(width(4));
        harness.set(DATA, 0xC);
        tick(harness);
        harness.set(CLOCK, 1).settle();
        harness.set(CLEAR, 1).settle();
        assertThat(harness.output(OUTPUT)).as("cleared at once, clock high").isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0)));
        harness.set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).as("CLR wins over the falling edge").isEqualTo(Value.of(4, 0));
        harness.set(CLEAR, 0).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0xC));
    }

    @Test
    void anUndefinedDataAtTheEdgeGivesZAndAnUndefinedControlKeepsTheState() {
        BlockHarness harness = enabled(width(4));
        harness.set(DATA, 3);
        tick(harness);
        harness.conflict(CLEAR).settle();
        assertThat(harness.output(OUTPUT)).as("a conflict on CLR keeps Q").isEqualTo(Value.of(4, 3));
        harness.set(DATA, 4);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("and ignores the clock").isEqualTo(Value.of(4, 3));
        harness.set(CLEAR, 0).conflict(ENABLE);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("a conflict on EN keeps Q").isEqualTo(Value.of(4, 3));

        harness.set(ENABLE, 1).release(DATA);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(4));
        harness.conflict(DATA);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.highImpedance(4));
        harness.set(DATA, 2);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 2));
    }

    @Test
    void widthOneAndWidthThirtyTwo() {
        BlockHarness single = enabled(width(1));
        single.set(DATA, 1);
        tick(single);
        assertThat(single.output(OUTPUT)).isEqualTo(Value.ONE);

        BlockHarness wide = enabled(width(32));
        wide.set(DATA, 0xFFFF_FFFFL);
        tick(wide);
        assertThat(wide.output(OUTPUT)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        wide.set(DATA, 0x8000_0001L);
        tick(wide);
        assertThat(wide.output(OUTPUT)).isEqualTo(Value.of(32, 0x8000_0001L));
    }

    @Test
    void restartingBringsItBackToZero() {
        BlockHarness harness = enabled(width(4));
        harness.set(DATA, 0xF);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0xF));
        harness.restart().settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0)));
    }
}
