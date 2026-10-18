package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gaps found while auditing the tests of the components that came before the blocks: gates of every size against Java
 * on every input, the flip-flop's exact falling edge, the tri-state with an undefined enable, and components added to a
 * running circuit (they read the level their inputs already have).
 */
class ClassicComponentsTest {

    @ParameterizedTest
    @ValueSource(strings = {"and", "or", "xor", "nand", "nor"})
    void everyGateOfTwoToFourInputsMatchesJavaOnEveryInput(String operation) {
        for (int inputCount = 2; inputCount <= 4; inputCount++) {
            BlockHarness harness = new BlockHarness(operation + inputCount, Map.of());
            for (int inputs = 0; inputs < 1 << inputCount; inputs++) {
                int ones = Integer.bitCount(inputs);
                for (int pin = 0; pin < inputCount; pin++) {
                    harness.set(pin, inputs >>> pin & 1);
                }
                harness.advance(2);
                boolean expected = switch (operation) {
                    case "and" -> ones == inputCount;
                    case "or" -> ones > 0;
                    case "xor" -> ones % 2 == 1;
                    case "nand" -> ones != inputCount;
                    default -> ones == 0;
                };
                assertThat(harness.output(inputCount)).as("%s%d inputs %s", operation, inputCount,
                        Integer.toBinaryString(inputs)).isEqualTo(Value.of(expected));
            }
        }
    }

    @Test
    void theGatesAndTheInverterHonourTheirDelay() {
        for (String typeId : List.of("and3", "or2", "xor4", "nand2", "nor3", "not")) {
            BlockHarness harness = new BlockHarness(typeId, Map.of("delay", 4)).advance(4);
            int output = harness.type().pinCount() - 1;
            Value before = harness.output(output);
            // AND and NAND change when every input is 1; the others when the first one is
            int changed = typeId.startsWith("and") || typeId.startsWith("nand") ? output : 1;
            for (int pin = 0; pin < changed; pin++) {
                harness.set(pin, 1);
            }
            harness.advance(3);
            assertThat(harness.output(output)).as("%s before its delay", typeId).isEqualTo(before);
            harness.advance(1);
            assertThat(harness.output(output)).as("%s after its delay", typeId).isNotEqualTo(before);
        }
    }

    private static final int FLIP_FLOP_DATA = 0;
    private static final int FLIP_FLOP_OUTPUT = 1;
    private static final int FLIP_FLOP_CLOCK = 2;

    @Test
    void theFlipFlopLatchesOnlyWhenTheClockGoesFromOneToZero() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.FLIP_FLOP_DATA, Map.of()).set(FLIP_FLOP_DATA, 1)
                .advance(2);
        harness.release(FLIP_FLOP_CLOCK).advance(2).set(FLIP_FLOP_CLOCK, 0).advance(2);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).as("Z to 0 is not an edge").isEqualTo(Value.ZERO);
        harness.conflict(FLIP_FLOP_CLOCK).advance(2).set(FLIP_FLOP_CLOCK, 0).advance(2);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).as("a conflict to 0 is not an edge").isEqualTo(Value.ZERO);
        harness.set(FLIP_FLOP_CLOCK, 1).advance(2).release(FLIP_FLOP_CLOCK).advance(2).set(FLIP_FLOP_CLOCK, 0)
                .advance(2);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).as("1, Z, 0 is not an edge").isEqualTo(Value.ZERO);
        harness.set(FLIP_FLOP_CLOCK, 1).advance(2);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).as("a rising edge does nothing").isEqualTo(Value.ZERO);
        harness.set(FLIP_FLOP_CLOCK, 0).advance(2);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).isEqualTo(Value.ONE);
    }

    @Test
    void theFlipFlopHonoursItsDelay() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.FLIP_FLOP_DATA, Map.of("delay", 6))
                .set(FLIP_FLOP_DATA, 1).set(FLIP_FLOP_CLOCK, 1).advance(1);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).as("power-on, at once").isEqualTo(Value.ZERO);
        harness.set(FLIP_FLOP_CLOCK, 0).advance(5);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).isEqualTo(Value.ZERO);
        harness.advance(1);
        assertThat(harness.output(FLIP_FLOP_OUTPUT)).isEqualTo(Value.ONE);
    }

    @Test
    void aTriStateWithAnUndefinedEnableDrivesNothing() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TRI_STATE, Map.of()).set(0, 1).set(2, 1)
                .advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.ONE);
        harness.release(2).advance(2);
        assertThat(harness.output(1)).as("enable Z").isEqualTo(Value.HIGH_IMPEDANCE);
        harness.conflict(2).advance(2);
        assertThat(harness.output(1)).as("enable in conflict").isEqualTo(Value.HIGH_IMPEDANCE);
        harness.set(2, 1).release(0).advance(2);
        assertThat(harness.output(1)).as("data Z passes as Z").isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void aTriStateHonoursItsDelay() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TRI_STATE, Map.of("delay", 3)).set(0, 1)
                .advance(5).set(2, 1).advance(2);
        assertThat(harness.output(1)).isEqualTo(Value.HIGH_IMPEDANCE);
        harness.advance(1);
        assertThat(harness.output(1)).isEqualTo(Value.ONE);
    }

    /**
     * A flip-flop, register or counter added while the circuit runs with its clock already high latches on the next
     * fall: it starts from the level the clock has, not from «unknown».
     */
    @Test
    void storingComponentsAddedWhileTheClockIsHighLatchOnTheNextFall() {
        record Case(String typeId, Map<String, Object> parameters, int dataPin, int clockPin, int outputPin,
                    Value data) {
        }
        List<Case> cases = List.of(
                new Case(StandardComponentTypes.FLIP_FLOP_DATA, Map.of(), 0, 2, 1, Value.ONE),
                new Case(StandardComponentTypes.REGISTER, Map.of(StandardComponentTypes.WIDTH, 4), 0, 3, 4,
                        Value.of(4, 9)),
                new Case(StandardComponentTypes.COUNTER, Map.of(StandardComponentTypes.WIDTH, 4), -1, 4, 5,
                        Value.of(4, 1)));
        for (Case component : cases) {
            ComponentType type = StandardComponentTypes.registry().require(component.typeId())
                    .specializedFor(component.parameters());
            int dataWidth = component.dataPin() < 0 ? 1 : type.pin(component.dataPin()).width();
            SimulationEngine engine = new SimulationEngine();
            List<FlatComponent> sources = List.of(source("clockSource", 1, "1"), source("dataSource", dataWidth, "0"));
            engine.update(new FlatCircuit(sources, List.of(
                    new FlatNet("clock", List.of(new FlatPinRef("clockSource", 0)), 1, false),
                    new FlatNet("data", List.of(new FlatPinRef("dataSource", 0)), dataWidth, false))));
            if (component.dataPin() >= 0) {
                engine.setInput("dataSource", component.data());
            }
            engine.advance(5);

            List<FlatComponent> withComponent = new ArrayList<>(sources);
            withComponent.add(new FlatComponent("dut", type, component.parameters()));
            List<FlatPinRef> dataPins = new ArrayList<>(List.of(new FlatPinRef("dataSource", 0)));
            if (component.dataPin() >= 0) {
                dataPins.add(new FlatPinRef("dut", component.dataPin()));
            }
            engine.update(new FlatCircuit(withComponent, List.of(
                    new FlatNet("clock", List.of(new FlatPinRef("clockSource", 0),
                            new FlatPinRef("dut", component.clockPin())), 1, false),
                    new FlatNet("data", dataPins, dataWidth, false),
                    new FlatNet("output", List.of(new FlatPinRef("dut", component.outputPin())),
                            type.pin(component.outputPin()).width(), false))));
            engine.advance(5);
            assertThat(engine.snapshot().netValue("output")).as("%s at power-on", component.typeId())
                    .isEqualTo(Value.of(component.data().width(), 0));

            engine.setInput("clockSource", Value.ZERO);
            engine.advance(5);
            assertThat(engine.snapshot().netValue("output")).as("%s after the first fall", component.typeId())
                    .isEqualTo(component.data());
        }
    }

    @Test
    void aLedAddedToADrivenNetShowsItsLevelAtOnce() {
        SimulationEngine engine = new SimulationEngine();
        List<FlatComponent> sources = List.of(source("source", 1, "1"));
        engine.update(new FlatCircuit(sources, List.of(new FlatNet("net", List.of(new FlatPinRef("source", 0)), 1,
                false))));
        engine.advance(2);
        List<FlatComponent> withLed = new ArrayList<>(sources);
        withLed.add(new FlatComponent("led", StandardComponentTypes.registry().require(StandardComponentTypes.LED),
                Map.of()));
        engine.update(new FlatCircuit(withLed, List.of(new FlatNet("net", List.of(new FlatPinRef("source", 0),
                new FlatPinRef("led", 0)), 1, false))));
        assertThat(engine.snapshot().componentState("led")).isEqualTo(new VisibleState.LedState(Value.ONE));
    }

    @Test
    void aCounterAddedWithItsEnableInConflictShowsAnUndefinedCarryAtOnce() {
        ComponentType counter = StandardComponentTypes.registry().require(StandardComponentTypes.COUNTER);
        SimulationEngine engine = new SimulationEngine();
        List<FlatComponent> sources = List.of(source("one", 1, "1"), source("zero", 1, "0"));
        FlatNet enableNet = new FlatNet("enable", List.of(new FlatPinRef("one", 0), new FlatPinRef("zero", 0)), 1,
                false);
        engine.update(new FlatCircuit(sources, List.of(enableNet)));
        engine.advance(2);
        assertThat(engine.snapshot().netValue("enable").conflict()).isTrue();

        List<FlatComponent> withCounter = new ArrayList<>(sources);
        withCounter.add(new FlatComponent("counter", counter, Map.of()));
        engine.update(new FlatCircuit(withCounter, List.of(
                new FlatNet("enable", List.of(new FlatPinRef("one", 0), new FlatPinRef("zero", 0),
                        new FlatPinRef("counter", 2)), 1, false),
                new FlatNet("carry", List.of(new FlatPinRef("counter", 6)), 1, false))));
        engine.advance(2);
        assertThat(engine.snapshot().netValue("carry")).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void anInputCannotBeGivenAValueOfAnotherWidth() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(List.of(source("pin", 4, "0")), List.of(
                new FlatNet("net", List.of(new FlatPinRef("pin", 0)), 4, false))));
        assertThatThrownBy(() -> engine.setInput("pin", Value.of(8, 3))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.setInput("pin", Value.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.snapshot().componentState("pin")).as("the refused value is not kept")
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 0)));
        engine.setInput("pin", Value.of(4, 3));
        assertThat(engine.snapshot().netValue("net")).isEqualTo(Value.of(4, 3));
    }

    private static FlatComponent source(String id, int width, String value) {
        Map<String, Object> parameters = Map.of("value", value, InterfacePins.WIDTH, width);
        return new FlatComponent(id, StandardComponentTypes.registry().require(InterfacePins.INPUT_PIN)
                .specializedFor(parameters), parameters);
    }
}
