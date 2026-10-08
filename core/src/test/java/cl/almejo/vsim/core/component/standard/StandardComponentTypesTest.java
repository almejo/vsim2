package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.GateHarness;
import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StandardComponentTypesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Value parse(String text) {
        return TestComponentTypes.parse(text);
    }

    private GridPoint pinPosition(String typeId, int pinIndex) {
        return registry.require(typeId).pin(pinIndex).position();
    }

    @Test
    void registryHasEveryType() {
        assertThat(registry.all()).hasSize(24);
        assertThat(registry.all()).extracting(ComponentType::id).contains(
                "and2", "or3", "xor4", "nand2", "nor4", "not", "flip-flop-data", "tri-state", "clock",
                "switch", "led", "seven-segments-display", "seven-segments-display-double", "time-diagram");
    }

    @Test
    void geometryMatchesVsim() {
        assertThat(registry.require("and2").pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 0), new GridPoint(0, 4), new GridPoint(4, 2));
        assertThat(registry.require("and3").pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 0), new GridPoint(0, 2), new GridPoint(0, 4), new GridPoint(4, 2));
        assertThat(registry.require("and4").pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 0), new GridPoint(0, 1), new GridPoint(0, 2),
                        new GridPoint(0, 3), new GridPoint(4, 2));
        assertThat(pinPosition("switch", 0)).isEqualTo(new GridPoint(4, 1));
        assertThat(pinPosition("led", 0)).isEqualTo(new GridPoint(1, 3));
        assertThat(pinPosition("clock", 0)).isEqualTo(new GridPoint(2, 2));
        assertThat(pinPosition("flip-flop-data", 2)).isEqualTo(new GridPoint(2, 4));
        assertThat(pinPosition("seven-segments-display", 0)).isEqualTo(new GridPoint(4, 0));
        assertThat(pinPosition("seven-segments-display", 3)).isEqualTo(new GridPoint(1, 0));
        assertThat(pinPosition("seven-segments-display-double", 0)).isEqualTo(new GridPoint(8, 0));
        assertThat(pinPosition("seven-segments-display-double", 7)).isEqualTo(new GridPoint(1, 0));
        assertThat(pinPosition("time-diagram", 3)).isEqualTo(new GridPoint(0, 6));
    }

    @ParameterizedTest(name = "{0}({1},{2}) = {3}")
    @CsvSource({
            "and2,0,0,0", "and2,0,1,0", "and2,1,0,0", "and2,1,1,1", "and2,Z,1,1", "and2,Z,0,0", "and2,Z,Z,Z",
            "or2,0,0,0", "or2,0,1,1", "or2,1,1,1", "or2,Z,0,0", "or2,Z,1,1", "or2,Z,Z,Z",
            "xor2,0,0,0", "xor2,0,1,1", "xor2,1,1,0", "xor2,Z,1,1", "xor2,Z,Z,Z",
            "nand2,1,1,0", "nand2,0,1,1", "nand2,0,0,1", "nand2,Z,Z,Z",
            "nor2,0,0,1", "nor2,0,1,0", "nor2,1,1,0"
    })
    void twoInputTruthTables(String typeId, String first, String second, String expected) {
        GateHarness harness = new GateHarness(typeId).set(0, parse(first)).set(1, parse(second)).advance(2);
        assertThat(harness.output(2)).isEqualTo(parse(expected));
    }

    @Test
    void andWithOneFloatingInputPassesTheOther() {
        assertThat(new GateHarness("and2").set(0, Value.ONE).advance(2).output(2)).isEqualTo(Value.ONE);
    }

    @Test
    void wideGates() {
        GateHarness and4 = new GateHarness("and4");
        for (int pin = 0; pin < 4; pin++) {
            and4.set(pin, Value.ONE);
        }
        assertThat(and4.advance(2).output(4)).isEqualTo(Value.ONE);
        assertThat(and4.set(2, Value.ZERO).advance(2).output(4)).isEqualTo(Value.ZERO);

        GateHarness or3 = new GateHarness("or3").set(0, Value.ZERO).set(1, Value.ZERO).set(2, Value.ONE);
        assertThat(or3.advance(2).output(3)).isEqualTo(Value.ONE);
    }

    @Test
    void delayParameterIsHonored() {
        GateHarness harness = new GateHarness("and2", Map.of("delay", 5)).set(0, Value.ONE).set(1, Value.ONE);
        harness.advance(0).advance(4);
        assertThat(harness.output(2)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(harness.advance(1).output(2)).isEqualTo(Value.ONE);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,0", "Z,Z"})
    void notGate(String input, String expected) {
        assertThat(new GateHarness("not").set(0, parse(input)).advance(2).output(1)).isEqualTo(parse(expected));
    }

    @Test
    void triStateFollowsEnable() {
        GateHarness harness = new GateHarness("tri-state").set(0, Value.ONE).set(2, Value.ONE).advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.ONE);
        assertThat(harness.set(2, Value.ZERO).advance(2).output(1)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(harness.set(2, Value.ONE).advance(2).output(1)).isEqualTo(Value.ONE);
    }

    @Test
    void flipFlopLatchesOnFallingEdge() {
        GateHarness harness = new GateHarness("flip-flop-data").set(0, Value.ONE).set(2, Value.ONE).advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(harness.set(2, Value.ZERO).advance(2).output(1)).isEqualTo(Value.ONE);
        assertThat(harness.set(0, Value.ZERO).advance(2).output(1)).isEqualTo(Value.ONE);
        harness.set(2, Value.ONE).advance(2).set(2, Value.ZERO).advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.ZERO);
    }

    @Test
    void clockAlternatesWithItsTimes() {
        GateHarness harness = new GateHarness("clock", Map.of("time-up", 3, "time-down", 2));
        assertThat(harness.advance(0).output(0)).isEqualTo(Value.ZERO);
        assertThat(harness.advance(2).output(0)).isEqualTo(Value.ONE);
        assertThat(harness.advance(2).output(0)).isEqualTo(Value.ONE);
        assertThat(harness.advance(1).output(0)).isEqualTo(Value.ZERO);
        assertThat(harness.state()).isEqualTo(new VisibleState.ClockState(Value.ZERO));
    }

    @Test
    void switchDrivesItsValueAndToggles() {
        GateHarness harness = new GateHarness("switch").advance(0);
        assertThat(harness.output(0)).isEqualTo(Value.ONE);
        harness.interact();
        assertThat(harness.advance(0).output(0)).isEqualTo(Value.ZERO);
        assertThat(harness.state()).isEqualTo(new VisibleState.SwitchState(Value.ZERO));

        GateHarness floating = new GateHarness("switch", Map.of("value", "Z")).advance(0);
        assertThat(floating.output(0)).isEqualTo(Value.HIGH_IMPEDANCE);
        floating.interact();
        assertThat(floating.advance(0).output(0)).isEqualTo(Value.ONE);
    }

    @Test
    void ledShowsItsInput() {
        GateHarness harness = new GateHarness("led").set(0, Value.ONE).advance(1);
        assertThat(harness.state()).isEqualTo(new VisibleState.LedState(Value.ONE));
    }

    @Test
    void sevenSegmentsCombinesPinsLeastSignificantFirst() {
        GateHarness harness = new GateHarness("seven-segments-display")
                .set(0, Value.ONE).set(1, Value.ZERO).set(2, Value.ONE).set(3, Value.ZERO).advance(1);
        assertThat(harness.state()).isEqualTo(new VisibleState.SevenSegmentsState(Value.of(4, 0b0101)));

        GateHarness partial = new GateHarness("seven-segments-display").set(0, Value.ONE).advance(1);
        assertThat(partial.state()).isEqualTo(new VisibleState.SevenSegmentsState(new Value(4, 0b0001, 0b1110, false)));
    }

    @Test
    void timeDiagramKeepsTheLastSamples() {
        GateHarness harness = new GateHarness("time-diagram", Map.of("sample-interval", 10, "history", 10))
                .set(0, Value.ONE).advance(200);
        VisibleState.TimeDiagramState state = (VisibleState.TimeDiagramState) harness.state();
        assertThat(state.samples()).hasSize(10);
        assertThat(state.samples().getLast().values().getFirst()).isEqualTo(Value.ONE);
        assertThat(state.samples().getLast().time() - state.samples().get(8).time()).isEqualTo(10);
    }
}
