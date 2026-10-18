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

class CounterBehaviorTest {

    private static final int DATA = 0;
    private static final int LOAD = 1;
    private static final int ENABLE = 2;
    private static final int CLEAR = 3;
    private static final int CLOCK = 4;
    private static final int OUTPUT = 5;
    private static final int CARRY = 6;

    private static Map<String, Object> width(int bits) {
        return Map.of(StandardComponentTypes.WIDTH, bits);
    }

    /** A counter whose inputs are all driven, EN at 1 and the rest at 0 (the harness starts every source at 0). */
    private static BlockHarness enabled(Map<String, Object> parameters) {
        return new BlockHarness(StandardComponentTypes.COUNTER, parameters).set(ENABLE, 1).settle();
    }

    private static BlockHarness tick(BlockHarness harness) {
        return harness.set(CLOCK, 1).settle().set(CLOCK, 0).settle();
    }

    @Test
    void theCounterIsABlockWithDataLeftQAndCarryRightAndControlAtTheBottom() {
        ComponentType counter = StandardComponentTypes.registry().require(StandardComponentTypes.COUNTER);
        assertThat(counter.category()).isEqualTo(ComponentCategory.BLOCKS);
        assertThat(counter.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("width", "delay", "format");
        assertThat(counter.pins()).extracting(PinSpec::name).containsExactly("D", "LD", "EN", "CLR", "CLK", "Q", "C");
        assertThat(counter.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT,
                PinDirection.OUTPUT);
        assertThat(counter.pins()).extracting(PinSpec::width).containsExactly(8, 1, 1, 1, 1, 8, 1);
        assertThat(counter.pin(DATA).position().x()).isZero();
        assertThat(counter.pin(OUTPUT).position().x()).isEqualTo(counter.size().width());
        assertThat(counter.pin(CARRY).position().x()).isEqualTo(counter.size().width());
        for (int control : new int[]{LOAD, ENABLE, CLEAR, CLOCK}) {
            assertThat(counter.pin(control).position().y()).isEqualTo(counter.size().height());
        }
        assertThat(counter.specializedFor(width(32)).size()).isEqualTo(counter.size());
    }

    @Test
    void countsOnFallingEdgesAndWrapsAround() {
        BlockHarness harness = enabled(width(4));
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0)));
        for (int expected = 1; expected <= 20; expected++) {
            harness.set(CLOCK, 1).settle();
            assertThat(harness.output(OUTPUT)).as("rising edge").isEqualTo(Value.of(4, (expected - 1) % 16));
            harness.set(CLOCK, 0).settle();
            assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, expected % 16));
        }
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 4)));
    }

    @Test
    void enableLowHoldsAndLoadWinsOverEnable() {
        BlockHarness harness = enabled(width(4));
        tick(tick(harness));
        harness.set(ENABLE, 0);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 2));
        harness.set(DATA, 9).set(LOAD, 1);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("LD with EN = 0").isEqualTo(Value.of(4, 9));
        harness.set(ENABLE, 1).set(DATA, 12);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("LD wins over EN").isEqualTo(Value.of(4, 12));
        harness.set(LOAD, 0);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 13));
    }

    @Test
    void carryIsOneOnlyAtAllOnesWithEnable() {
        BlockHarness harness = enabled(width(4));
        assertThat(harness.output(CARRY)).isEqualTo(Value.ZERO);
        harness.set(DATA, 14).set(LOAD, 1);
        tick(harness);
        harness.set(LOAD, 0).settle();
        assertThat(harness.output(CARRY)).isEqualTo(Value.ZERO);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 15));
        assertThat(harness.output(CARRY)).isEqualTo(Value.ONE);
        harness.set(ENABLE, 0).settle();
        assertThat(harness.output(CARRY)).as("EN = 0").isEqualTo(Value.ZERO);
        harness.release(ENABLE).settle();
        assertThat(harness.output(CARRY)).as("EN unconnected counts as 1").isEqualTo(Value.ONE);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.output(CARRY)).isEqualTo(Value.ZERO);
    }

    @Test
    void clearIsAsynchronousWhileTheClockIsHighAndWinsOverLoad() {
        BlockHarness harness = enabled(width(4));
        tick(tick(tick(harness)));
        harness.set(CLOCK, 1).settle().set(CLEAR, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        harness.set(DATA, 7).set(LOAD, 1).set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).as("CLR wins over the edge").isEqualTo(Value.of(4, 0));
        harness.set(CLEAR, 0).set(LOAD, 0);
        tick(harness);
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 1));
    }

    @Test
    void undefinedInputs() {
        BlockHarness harness = enabled(width(4));
        tick(harness);
        harness.conflict(LOAD);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("a conflict on LD keeps Q").isEqualTo(Value.of(4, 1));
        harness.set(LOAD, 0).conflict(ENABLE).settle();
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("a conflict on EN keeps Q").isEqualTo(Value.of(4, 1));
        assertThat(harness.output(CARRY)).isEqualTo(Value.HIGH_IMPEDANCE);
        harness.set(ENABLE, 1).conflict(CLEAR);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("a conflict on CLR keeps Q").isEqualTo(Value.of(4, 1));
        harness.set(CLEAR, 0).release(DATA);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("no load: D does not matter").isEqualTo(Value.of(4, 2));
        harness.set(LOAD, 1);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("loading an undefined D").isEqualTo(Value.highImpedance(4));
        assertThat(harness.output(CARRY)).isEqualTo(Value.HIGH_IMPEDANCE);
        harness.set(LOAD, 0);
        tick(harness);
        assertThat(harness.output(OUTPUT)).as("counting from Z stays Z").isEqualTo(Value.highImpedance(4));
        harness.set(CLEAR, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.output(CARRY)).isEqualTo(Value.ZERO);
    }

    @Test
    void widthOneAndWidthThirtyTwoWrapWithoutLeaks() {
        BlockHarness single = enabled(width(1));
        tick(single);
        assertThat(single.output(OUTPUT)).isEqualTo(Value.ONE);
        assertThat(single.output(CARRY)).isEqualTo(Value.ONE);
        tick(single);
        assertThat(single.output(OUTPUT)).isEqualTo(Value.ZERO);

        BlockHarness wide = enabled(width(32));
        wide.set(DATA, 0xFFFF_FFFEL).set(LOAD, 1);
        tick(wide);
        wide.set(LOAD, 0);
        tick(wide);
        assertThat(wide.output(OUTPUT)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        assertThat(wide.output(CARRY)).isEqualTo(Value.ONE);
        tick(wide);
        assertThat(wide.output(OUTPUT)).isEqualTo(Value.of(32, 0));
        assertThat(wide.output(CARRY)).isEqualTo(Value.ZERO);
    }

    @Test
    void unconnectedControlsCountAndRestartingBringsItBackToZero() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.COUNTER, width(4), DATA, LOAD, ENABLE, CLEAR)
                .settle();
        tick(tick(harness));
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 2));
        harness.restart().settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(new VisibleState.StoredValueState(Value.of(4, 0)));
    }
}
