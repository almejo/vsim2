package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.machine.MachineEvaluator.MachineStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.machine.MachineExamples.arrow;
import static cl.almejo.vsim.core.machine.MachineExamples.state;
import static org.assertj.core.api.Assertions.assertThat;

class MachineEvaluatorTest {

    @Test
    void theDetectorWalksTheMockupsSequence() {
        StateMachine machine = MachineExamples.twoOnesDetector();
        String current = "s0";
        List<String> visited = new ArrayList<>();
        for (boolean x : List.of(false, true, true, true, false, true)) {
            current = MachineEvaluator.next(machine, current, Map.of("x", x)).orElseThrow().nextStateId();
            visited.add(machine.state(current).orElseThrow().name());
        }
        assertThat(visited).containsExactly("S0", "S1", "S2", "S2", "S0", "S1");
    }

    @Test
    void theStepNamesTheArrowItTakes() {
        MachineStep step = MachineEvaluator.next(MachineExamples.twoOnesDetector(), "s1", Map.of("x", true))
                .orElseThrow();
        assertThat(step.nextStateId()).isEqualTo("s2");
        assertThat(step.transitionId()).contains("t2");
    }

    @Test
    void theOtherwiseArrowIsTakenWhenNoConditionHolds() {
        MachineStep step = MachineEvaluator.next(MachineExamples.twoOnesDetector(), "s1", Map.of("x", false))
                .orElseThrow();
        assertThat(step.nextStateId()).isEqualTo("s0");
        assertThat(step.transitionId()).contains("t3");
    }

    @Test
    void theOtherwiseArrowLosesToATrueConditionEvenWhenItComesFirst() {
        StateMachine machine = twoStates(List.of(arrow("t0", "a", "a", ""), arrow("t1", "a", "b", "go")));
        assertThat(MachineEvaluator.next(machine, "a", Map.of("go", true)).orElseThrow().nextStateId())
                .isEqualTo("b");
    }

    @Test
    void withoutAMatchingArrowTheMachineStays() {
        StateMachine machine = twoStates(List.of(arrow("t0", "a", "b", "go")));
        MachineStep step = MachineEvaluator.next(machine, "a", Map.of("go", false)).orElseThrow();
        assertThat(step.nextStateId()).isEqualTo("a");
        assertThat(step.transitionId()).isEmpty();
    }

    @Test
    void aBadConditionNeverHolds() {
        StateMachine machine = twoStates(List.of(arrow("t0", "a", "b", "go +")));
        assertThat(MachineEvaluator.next(machine, "a", Map.of("go", true)).orElseThrow().nextStateId())
                .isEqualTo("a");
    }

    @Test
    void anUnknownStateHasNoStep() {
        assertThat(MachineEvaluator.next(MachineExamples.twoOnesDetector(), "s9", Map.of())).isEmpty();
    }

    @Test
    void mooreOutputsComeFromTheStateAndUnassignedOutputsAreZero() {
        StateMachine machine = MachineExamples.twoOnesDetector().withPinNames(List.of("x"), List.of("Z", "Y"));
        assertThat(MachineEvaluator.outputs(machine, "s2", Map.of("x", false)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("Z", true, "Y", false));
        assertThat(MachineEvaluator.outputs(machine, "s0", Map.of("x", false)))
                .containsEntry("Z", false).containsEntry("Y", false);
    }

    @Test
    void mealyOutputsComeFromTheArrowTheInputsSelect() {
        MachineTransition mealy = new MachineTransition("t0", "a", "b", "go",
                List.of(new OutputAssignment("W", true)));
        StateMachine machine = twoStates(List.of(mealy)).withPinNames(List.of("go"), List.of("W"));
        assertThat(MachineEvaluator.outputs(machine, "a", Map.of("go", true))).containsEntry("W", true);
        assertThat(MachineEvaluator.outputs(machine, "a", Map.of("go", false))).containsEntry("W", false);
        MachineStep step = MachineEvaluator.next(machine, "a", Map.of("go", true)).orElseThrow();
        assertThat(step.outputs()).containsEntry("W", true);
    }

    private static StateMachine twoStates(List<MachineTransition> transitions) {
        return new StateMachine(List.of(state("a", "A", 0, List.of()), state("b", "B", 100, List.of())),
                transitions, Optional.of("a"), List.of(), List.of(), false, false, StateMachine.EDGE_FALLING,
                List.of());
    }
}
