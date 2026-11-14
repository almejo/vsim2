package cl.almejo.vsim.core.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.core.machine.MachineExamples.arrow;
import static cl.almejo.vsim.core.machine.MachineExamples.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateMachineTest {

    @Test
    void aNewMachineHasOneInitialStateAndNoPins() {
        StateMachine machine = StateMachine.initial();
        assertThat(machine.states()).extracting(MachineState::name).containsExactly("S0");
        assertThat(machine.states().get(0).position()).isEqualTo(DiagramPoint.ORIGIN);
        assertThat(machine.initialStateId()).contains(machine.states().get(0).id());
        assertThat(machine.transitions()).isEmpty();
        assertThat(machine.inputNames()).isEmpty();
        assertThat(machine.outputNames()).isEmpty();
        assertThat(machine.edge()).isEqualTo(StateMachine.EDGE_FALLING);
    }

    @Test
    void theNamesInUseComeFromConditionsAndOutputsInOrderOfAppearance() {
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", "go · stop'"), arrow("t1", "b", "a", "go")),
                List.of(new OutputAssignment("Z", true), new OutputAssignment("Y", false)),
                List.of(new OutputAssignment("W", true)));
        assertThat(machine.inputNamesUsed()).containsExactly("go", "stop");
        assertThat(machine.outputNamesUsed()).containsExactly("Z", "Y", "W");
    }

    @Test
    void theClockAndResetAreNeverNamesInUse() {
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", "CLK + RST + x")),
                List.of(new OutputAssignment("RST", true)), List.of());
        assertThat(machine.inputNamesUsed()).containsExactly("x");
        assertThat(machine.outputNamesUsed()).isEmpty();
    }

    @Test
    void aBadConditionAddsNoNames() {
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", "x + + y")), List.of(), List.of());
        assertThat(machine.inputNamesUsed()).isEmpty();
    }

    @Test
    void anOutputUsedInAConditionIsNotAnInput() {
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", "Z x")),
                List.of(new OutputAssignment("Z", true)), List.of());
        assertThat(machine.inputNamesUsed()).containsExactly("x");
    }

    @Test
    void newNamesInUseAreAddedAfterThePinsAndUnusedPinsStay() {
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", "go")),
                List.of(new OutputAssignment("Z", true)), List.of())
                .withPinNames(List.of("old"), List.of("Q"));
        StateMachine withNames = machine.withUsedNamesAdded();
        assertThat(withNames.inputNames()).containsExactly("old", "go");
        assertThat(withNames.outputNames()).containsExactly("Q", "Z");
        assertThat(withNames.withUsedNamesAdded()).isEqualTo(withNames);
    }

    @Test
    void addingNamesStopsAtTheLimits() {
        List<String> many = new ArrayList<>();
        for (int index = 0; index < MachineLimits.MAXIMUM_INPUTS + 3; index++) {
            many.add("x" + index);
        }
        StateMachine machine = machineWith(List.of(arrow("t0", "a", "b", String.join(" + ", many))), List.of(),
                List.of()).withUsedNamesAdded();
        assertThat(machine.inputNames()).containsExactlyElementsOf(many.subList(0, MachineLimits.MAXIMUM_INPUTS));
    }

    @Test
    void theClockAndResetCannotBePinNames() {
        StateMachine machine = StateMachine.initial();
        assertThatThrownBy(() -> machine.withPinNames(List.of("CLK"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> machine.withPinNames(List.of(), List.of("RST")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StateMachine machineWith(List<MachineTransition> transitions, List<OutputAssignment> mooreOfA,
                                            List<OutputAssignment> mealyOfFirst) {
        List<MachineTransition> arrows = new ArrayList<>(transitions);
        if (!mealyOfFirst.isEmpty()) {
            arrows.set(0, arrows.get(0).withOutputs(mealyOfFirst));
        }
        return new StateMachine(List.of(state("a", "A", 0, mooreOfA), state("b", "B", 100, List.of())), arrows,
                Optional.of("a"), List.of(), List.of(), false, false, StateMachine.EDGE_FALLING, List.of());
    }
}
