package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link SimulationThread#setInputs} (M6.1-C): a truth-table row tried in the live circuit, applied like a click. */
class SimulationThreadSetInputsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);

    private FlatComponent component(String id, String typeId) {
        ComponentType type = registry.require(typeId).specializedFor(Map.of());
        return new FlatComponent(id, type, Map.of());
    }

    /** Switches "a" (net "na", read by NOT "gate" whose output is "out") and "b" (net "nb"); both start at 1. */
    private FlatCircuit circuit() {
        return new FlatCircuit(List.of(component("a", StandardComponentTypes.SWITCH),
                component("b", StandardComponentTypes.SWITCH), component("gate", StandardComponentTypes.NOT)),
                List.of(new FlatNet("na", List.of(new FlatPinRef("a", 0), new FlatPinRef("gate", 0)), 1, false),
                        new FlatNet("nb", List.of(new FlatPinRef("b", 0)), 1, false),
                        new FlatNet("out", List.of(new FlatPinRef("gate", 1)), 1, false)));
    }

    @Test
    void theValuesAreAppliedAtTheCurrentTimeAndPublishedWithTheNextSnapshot() {
        thread.update(circuit());
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("na")).isEqualTo(Value.ONE);

        thread.setInputs(Map.of("a", Value.ZERO, "b", Value.ZERO));
        assertThat(thread.pendingRequests()).as("queued like a click").isEqualTo(1);
        thread.tick();

        SimulationSnapshot snapshot = thread.latestSnapshot();
        assertThat(snapshot.time()).isZero();
        assertThat(snapshot.netValue("na")).isEqualTo(Value.ZERO);
        assertThat(snapshot.netValue("nb")).isEqualTo(Value.ZERO);
        assertThat(snapshot.componentState("a")).isEqualTo(new VisibleState.SwitchState(Value.ZERO));
        thread.step(10);
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("out")).as("the gate follows as after a click").isEqualTo(Value.ONE);
    }

    @Test
    void anIdThatIsGoneOrIsNotAnInputIsSkippedAndTheSimulationGoesOn() {
        thread.update(circuit());
        thread.resume();
        thread.tick();

        thread.setInputs(Map.of("ghost", Value.ONE, "gate", Value.ONE, "b", Value.ZERO));
        thread.tick();

        SimulationSnapshot snapshot = thread.latestSnapshot();
        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.running()).isTrue();
        assertThat(snapshot.netValue("nb")).isEqualTo(Value.ZERO);
    }
}
