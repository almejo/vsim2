package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.GateHarness;
import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
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
        assertThat(registry.all()).hasSize(38);
        assertThat(registry.all()).extracting(ComponentType::id).contains(
                "and2", "or3", "xor4", "nand2", "nor4", "not", "flip-flop-data", "tri-state", "clock",
                "switch", "constant", "led", "seven-segments-display", "seven-segments-display-double", "time-diagram", "bus-splitter", "bus-joiner",
                "adder", "alu", "comparator", "mux", "decoder", "register", "counter", "tunnel-in", "tunnel-out");
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
        assertThat(harness.output(1)).as("a fresh flip-flop drives 0").isEqualTo(Value.ZERO);
        assertThat(harness.set(2, Value.ZERO).advance(2).output(1)).isEqualTo(Value.ONE);
        assertThat(harness.set(0, Value.ZERO).advance(2).output(1)).isEqualTo(Value.ONE);
        harness.set(2, Value.ONE).advance(2).set(2, Value.ZERO).advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.ZERO);
    }

    @Test
    void aFlipFlopWithDFromNotQTogglesOnEachFallingEdge() {
        // Q = 0 at the start, so D = NOT Q = 1 and the first falling edge sets Q (with Q = Z it would never start).
        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(
                List.of(new FlatComponent("clock", registry.require(StandardComponentTypes.CLOCK),
                                Map.of("time-up", 5, "time-down", 5)),
                        new FlatComponent("flipFlop", registry.require(StandardComponentTypes.FLIP_FLOP_DATA), Map.of()),
                        new FlatComponent("inverter", registry.require(StandardComponentTypes.NOT), Map.of())),
                List.of(new FlatNet("clock", List.of(new FlatPinRef("clock", 0), new FlatPinRef("flipFlop", 2)), 1,
                                false),
                        new FlatNet("q", List.of(new FlatPinRef("flipFlop", 1), new FlatPinRef("inverter", 0)), 1,
                                false),
                        new FlatNet("d", List.of(new FlatPinRef("inverter", 1), new FlatPinRef("flipFlop", 0)), 1,
                                false))));
        // the clock is low for 5, high for 5: falling edges at 10, 20, 30; Q follows one unit later
        assertThat(engine.snapshot().netValue("q")).isEqualTo(Value.ZERO);
        engine.runUntil(9);
        assertThat(engine.snapshot().netValue("q")).isEqualTo(Value.ZERO);
        engine.runUntil(11);
        assertThat(engine.snapshot().netValue("q")).isEqualTo(Value.ONE);
        engine.runUntil(21);
        assertThat(engine.snapshot().netValue("q")).isEqualTo(Value.ZERO);
        engine.runUntil(31);
        assertThat(engine.snapshot().netValue("q")).isEqualTo(Value.ONE);
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

    @Test
    void interfacePinsActLikeASwitchAndALedAtTheTopLevel() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        ComponentType input = standard.require(InterfacePins.INPUT_PIN);
        ComponentType output = standard.require(InterfacePins.OUTPUT_PIN);
        assertThat(input.category()).isEqualTo(ComponentCategory.INTERFACE);
        assertThat(output.category()).isEqualTo(ComponentCategory.INTERFACE);
        assertThat(input.pin(0).isDriver()).isTrue();
        assertThat(output.pin(0).isDriver()).isFalse();
        assertThat(input.resolveParameters(Map.of())).containsEntry("value", "0");

        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(
                List.of(new FlatComponent("in", input, Map.of()), new FlatComponent("out", output, Map.of())),
                List.of(new FlatNet("n", List.of(new FlatPinRef("in", 0), new FlatPinRef("out", 0)), 1, false))));
        engine.advance(1);
        assertThat(engine.snapshot().componentState("out")).isEqualTo(new VisibleState.LedState(Value.ZERO));
        engine.interact("in");
        engine.advance(1);
        assertThat(engine.snapshot().componentState("out")).isEqualTo(new VisibleState.LedState(Value.ONE));
    }

    @Test
    void wideInterfacePinsDriveAndToggleEveryBit() {
        Map<String, Object> eightBits = Map.of(InterfacePins.WIDTH, 8);
        ComponentType input = registry.require(InterfacePins.INPUT_PIN).specializedFor(eightBits);
        ComponentType output = registry.require(InterfacePins.OUTPUT_PIN).specializedFor(eightBits);
        assertThat(input.pin(0).width()).isEqualTo(8);
        assertThat(output.pin(0).width()).isEqualTo(8);
        assertThat(registry.require(InterfacePins.INPUT_PIN).pin(0).width()).isEqualTo(1);
        assertThat(registry.require(InterfacePins.OUTPUT_PIN).resolveParameters(Map.of()))
                .containsEntry(InterfacePins.WIDTH, 1);

        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(
                List.of(new FlatComponent("in", input, Map.of("value", "1", InterfacePins.WIDTH, 8)),
                        new FlatComponent("out", output, eightBits)),
                List.of(new FlatNet("n", List.of(new FlatPinRef("in", 0), new FlatPinRef("out", 0)), 8, false))));
        engine.advance(1);
        assertThat(engine.snapshot().componentState("out")).isEqualTo(new VisibleState.LedState(Value.of(8, 0xFF)));
        engine.interact("in");
        engine.advance(1);
        assertThat(engine.snapshot().componentState("out")).isEqualTo(new VisibleState.LedState(Value.of(8, 0)));
        engine.interact("in");
        engine.advance(1);
        assertThat(engine.snapshot().componentState("in")).isEqualTo(new VisibleState.SwitchState(Value.of(8, 0xFF)));

        GateHarness floating = new GateHarness(InterfacePins.INPUT_PIN, Map.of("value", "Z", InterfacePins.WIDTH, 4))
                .advance(0);
        assertThat(floating.output(0)).isEqualTo(Value.highImpedance(4));
        floating.interact();
        assertThat(floating.advance(0).output(0)).isEqualTo(Value.of(4, 0xF));
    }
}
