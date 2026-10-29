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

/** A push button pressed and released through the simulation thread: 1 only while it is held. */
class SimulationThreadPushButtonTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);

    /** Push button "button" on net "pressed", read by a NOT "gate" whose output is "out". */
    private FlatCircuit circuit() {
        ComponentType button = registry.require(StandardComponentTypes.PUSH_BUTTON);
        Map<String, Object> negated = Map.of(StandardComponentTypes.NEGATE, true);
        ComponentType not = registry.require(StandardComponentTypes.BUFFER).specializedFor(negated);
        return new FlatCircuit(List.of(new FlatComponent("button", button, Map.of()),
                new FlatComponent("gate", not, negated)),
                List.of(new FlatNet("pressed", List.of(new FlatPinRef("button", 0), new FlatPinRef("gate", 0)), 1,
                                false),
                        new FlatNet("out", List.of(new FlatPinRef("gate", 1)), 1, false)));
    }

    @Test
    void itStartsAtZeroAndGivesOneOnlyWhileHeld() {
        thread.update(circuit());
        thread.resume();
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("pressed")).isEqualTo(Value.ZERO);

        thread.pressButton("button");
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("pressed")).isEqualTo(Value.ONE);
        assertThat(thread.latestSnapshot().componentState("button"))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));

        thread.releaseButton("button");
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("pressed")).isEqualTo(Value.ZERO);
    }

    @Test
    void aPressWhilePausedIsAStepWithTheNetThatChanged() {
        thread.update(circuit());
        thread.tick();

        thread.pressButton("button");
        thread.tick();

        assertThat(thread.latestSnapshot().netValue("pressed")).isEqualTo(Value.ONE);
        assertThat(thread.latestStep()).isPresent();
        assertThat(thread.latestStep().orElseThrow().result().changedNets()).contains("pressed");
    }

    @Test
    void theInputsOfATruthTableRowOrOfTheAiSetItLikeASwitch() {
        thread.update(circuit());
        thread.tick();

        thread.setInputs(Map.of("button", Value.ONE));
        thread.tick();

        assertThat(thread.latestSnapshot().netValue("pressed")).isEqualTo(Value.ONE);
    }

    @Test
    void aPressOfAButtonThatIsGoneChangesNothing() {
        thread.update(circuit());
        thread.resume();
        thread.tick();

        thread.pressButton("ghost");
        thread.tick();

        assertThat(thread.latestSnapshot().failure()).isEmpty();
        assertThat(thread.latestSnapshot().running()).isTrue();
    }
}
