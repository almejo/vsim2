package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Two tri-states on one bus: only enabled drivers count, so a disabled one never causes a short circuit. */
class TriStateBusTest {

    private final ComponentType triState = StandardComponentTypes.registry().require("tri-state");
    private final ComponentType source = TestComponentTypes.registry().require(TestComponentTypes.SOURCE);
    private final SimulationEngine engine = new SimulationEngine();

    private void build(String secondEnable) {
        engine.update(new FlatCircuit(
                List.of(new FlatComponent("first", triState, Map.of()),
                        new FlatComponent("second", triState, Map.of()),
                        new FlatComponent("firstInput", source, Map.of("value", "1")),
                        new FlatComponent("firstEnable", source, Map.of("value", "1")),
                        new FlatComponent("secondInput", source, Map.of("value", "0")),
                        new FlatComponent("secondEnable", source, Map.of("value", secondEnable))),
                List.of(net("firstIn", "first", 0, "firstInput"),
                        net("firstEn", "first", 2, "firstEnable"),
                        net("secondIn", "second", 0, "secondInput"),
                        net("secondEn", "second", 2, "secondEnable"),
                        new FlatNet("bus", List.of(new FlatPinRef("first", 1), new FlatPinRef("second", 1)), 1, false))));
    }

    private static FlatNet net(String id, String component, int pin, String sourceId) {
        return new FlatNet(id, List.of(new FlatPinRef(component, pin), new FlatPinRef(sourceId, 0)), 1, false);
    }

    @Test
    void disabledTriStateDoesNotConflict() {
        build("0");
        engine.advance(3);
        assertThat(engine.snapshot().netValue("bus")).isEqualTo(Value.ONE);
        assertThat(engine.snapshot().problems()).isEmpty();
    }

    @Test
    void enablingBothWithDifferentValuesIsAShortCircuit() {
        build("0");
        engine.advance(3);
        build("1");
        engine.advance(3);
        assertThat(engine.snapshot().shortCircuits()).singleElement()
                .satisfies(shortCircuit -> assertThat(shortCircuit.drivers()).hasSize(2));
    }
}
