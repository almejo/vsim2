package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.StickyColor;
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

    @Test
    void aPinBornFromANameHasThatNameAsItsId() {
        StateMachine machine = StateMachine.initial().withPinNames(List.of("x"), List.of("Z"));

        assertThat(machine.inputs()).containsExactly(MachinePin.named("x"));
        assertThat(machine.outputs()).containsExactly(new MachinePin("Z", "Z"));
        assertThat(machine.pin(MachinePinEdits.Side.INPUT, "x")).contains(MachinePin.named("x"));
        assertThat(machine.pin(MachinePinEdits.Side.OUTPUT, "x")).isEmpty();
    }

    @Test
    void settingPinNamesKeepsTheIdsOfTheNamesThatStay() {
        StateMachine machine = StateMachine.initial().withPins(List.of(new MachinePin("x", "a")), List.of());

        StateMachine changed = machine.withPinNames(List.of("b", "a", "x"), List.of());

        assertThat(changed.inputs()).containsExactly(MachinePin.named("b"), new MachinePin("x", "a"),
                new MachinePin("x-2", "x"));
        assertThat(changed.inputNames()).containsExactly("b", "a", "x");
    }

    @Test
    void aPinIdUsedTwiceOnASideIsRefused() {
        assertThatThrownBy(() -> StateMachine.initial().withPins(
                List.of(new MachinePin("x", "a"), new MachinePin("x", "b")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStateHasNoColourUntilOneIsChosenAndEditsKeepIt() {
        MachineState state = new MachineState("s0", "S0", DiagramPoint.ORIGIN, List.of());
        assertThat(state.color()).isEmpty();

        MachineState coloured = state.withColor(Optional.of(StateColor.CYAN)).withName("Inicio")
                .withPosition(new DiagramPoint(5, 5)).withOutputs(List.of());

        assertThat(coloured.color()).contains(StateColor.CYAN);
    }

    @Test
    void notesAreKeptAndEditedById() {
        MachineNote note = new MachineNote("n1", DiagramPoint.ORIGIN, MachineNote.DEFAULT_WIDTH,
                MachineNote.DEFAULT_HEIGHT, StickyColor.YELLOW, "ojo");
        StateMachine machine = StateMachine.initial().withNotes(List.of(note));

        StateMachine edited = machine.withNote("n1", old -> old.withText("ojo con S0"));

        assertThat(StateMachine.initial().notes()).isEmpty();
        assertThat(edited.notes()).extracting(MachineNote::text).containsExactly("ojo con S0");
        assertThat(edited.withPinNames(List.of("x"), List.of()).withEdge(StateMachine.EDGE_RISING).notes())
                .hasSize(1);
    }

    @Test
    void aNoteIsNeverSmallerThanTheMinimumAndAPasteMovesIt() {
        MachineNote tiny = new MachineNote("n1", DiagramPoint.ORIGIN, 1, 1, StickyColor.PINK, "");
        assertThat(tiny.width()).isEqualTo(MachineNote.MINIMUM_WIDTH);
        assertThat(tiny.height()).isEqualTo(MachineNote.MINIMUM_HEIGHT);

        MachineNote placed = tiny.placed("n2", new DiagramPoint(10, 20));

        assertThat(placed.id()).isEqualTo("n2");
        assertThat(placed.position()).isEqualTo(new DiagramPoint(10, 20));
        assertThat(placed.color()).isEqualTo(StickyColor.PINK);
        assertThatThrownBy(() -> StateMachine.initial().withNotes(List.of(tiny, tiny)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
