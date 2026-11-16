package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.machine.MachinePinEdits.PinNameProblem;
import cl.almejo.vsim.core.machine.MachinePinEdits.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Renaming, adding, removing and moving a machine's pins: pure functions that keep the pins' ids. */
class MachinePinEditsTest {

    private final StateMachine detector = MachineExamples.twoOnesDetector();

    @Test
    void renamingAnInputRewritesEveryConditionAndKeepsThePinsId() {
        StateMachine renamed = MachinePinEdits.renamed(detector, Side.INPUT, "x", "a");

        assertThat(renamed.inputs()).containsExactly(new MachinePin("x", "a"));
        assertThat(renamed.transitions()).extracting(MachineTransition::condition)
                .containsExactly("a'", "a", "a", "", "a", "a'");
    }

    @Test
    void aRenameTouchesOnlyTheWholeNameAndKeepsTheSpacing() {
        StateMachine machine = detector.withTransition("t0", arrow -> arrow.withCondition("x' + xy·x"));

        StateMachine renamed = MachinePinEdits.renamed(machine, Side.INPUT, "x", "a");

        assertThat(renamed.transition("t0").orElseThrow().condition()).isEqualTo("a' + xy·a");
    }

    @Test
    void aBadConditionIsRenamedTooSoTheTextStaysTheUsersButWithTheNewName() {
        StateMachine machine = detector.withTransition("t0", arrow -> arrow.withCondition("x +"));

        StateMachine renamed = MachinePinEdits.renamed(machine, Side.INPUT, "x", "a");

        assertThat(renamed.transition("t0").orElseThrow().condition()).isEqualTo("a +");
    }

    @Test
    void renamingAnOutputRewritesMooreAndMealyAssignments() {
        StateMachine machine = detector.withTransition("t1",
                arrow -> arrow.withOutputs(List.of(new OutputAssignment("Z", true))));

        StateMachine renamed = MachinePinEdits.renamed(machine, Side.OUTPUT, "Z", "Y");

        assertThat(renamed.outputs()).containsExactly(new MachinePin("Z", "Y"));
        assertThat(renamed.states()).allSatisfy(state ->
                assertThat(state.outputs()).extracting(OutputAssignment::output).containsExactly("Y"));
        assertThat(renamed.transition("t1").orElseThrow().outputs())
                .containsExactly(new OutputAssignment("Y", true));
    }

    @Test
    void renamingAnUnusedPinOntoAUsedOneTakesItsPlaceAndTheOtherPinGoesAway() {
        StateMachine machine = detector.withPinNames(List.of("x", "w"), List.of("Z"));
        StateMachine replaced = machine.withTransitions(machine.transitions().stream()
                .map(arrow -> arrow.withCondition(arrow.condition().replace("x", "w"))).toList());

        assertThat(MachinePinEdits.nameProblem(replaced, Side.INPUT, "x", "w")).isEmpty();
        StateMachine renamed = MachinePinEdits.renamed(replaced, Side.INPUT, "x", "w");

        assertThat(renamed.inputs()).containsExactly(new MachinePin("x", "w"));
    }

    @Test
    void aUsedPinRenamedOntoAnUnusedOneLeavesTheUnusedPinWithItsIdSoItsWireStays() {
        StateMachine machine = detector.withPinNames(List.of("x", "b"), List.of("Z"));

        StateMachine renamed = MachinePinEdits.renamed(machine, Side.INPUT, "x", "b");

        assertThat(renamed.inputs()).as("b was the wired one the student kept: its pin survives")
                .containsExactly(new MachinePin("b", "b"));
        assertThat(renamed.transitions()).allSatisfy(arrow -> assertThat(arrow.condition()).doesNotContain("x"));
        assertThat(MachinePinUses.of(renamed, "b").isUnused()).isFalse();
    }

    @Test
    void renamingOntoAnotherUsedPinIsRefused() {
        StateMachine machine = detector.withTransition("t3", arrow -> arrow.withCondition("y"));

        assertThat(MachinePinEdits.nameProblem(machine.withUsedNamesAdded(), Side.INPUT, "x", "y"))
                .contains(PinNameProblem.TAKEN_INPUT);
    }

    @Test
    void theNameProblemsAreReported() {
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "x", "2a")).contains(PinNameProblem.NOT_A_NAME);
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "x", "CLK")).contains(PinNameProblem.RESERVED);
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "x", "Z")).contains(PinNameProblem.TAKEN_OUTPUT);
        assertThat(MachinePinEdits.nameProblem(detector, Side.OUTPUT, "", "x")).contains(PinNameProblem.TAKEN_INPUT);
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "", "x")).contains(PinNameProblem.TAKEN_INPUT);
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "x", "x")).isEmpty();
        assertThat(MachinePinEdits.nameProblem(detector, Side.INPUT, "", "b")).isEmpty();
    }

    @Test
    void addingPastTheLimitIsTooMany() {
        StateMachine full = detector;
        for (int index = 1; index < MachineLimits.MAXIMUM_INPUTS; index++) {
            full = MachinePinEdits.added(full, Side.INPUT, "i" + index);
        }

        assertThat(full.inputs()).hasSize(MachineLimits.MAXIMUM_INPUTS);
        assertThat(MachinePinEdits.nameProblem(full, Side.INPUT, "", "extra")).contains(PinNameProblem.TOO_MANY);
        assertThat(MachinePinEdits.nameProblem(full, Side.INPUT, "x", "a")).isEmpty();
    }

    @Test
    void anAddedPinGoesLastWithItsNameAsId() {
        StateMachine added = MachinePinEdits.added(detector, Side.INPUT, "b");

        assertThat(added.inputs()).containsExactly(MachinePin.named("x"), MachinePin.named("b"));
    }

    @Test
    void aNewNameWhoseIdIsTakenGetsTheNextFreeId() {
        StateMachine renamed = MachinePinEdits.renamed(detector, Side.INPUT, "x", "a");

        StateMachine added = MachinePinEdits.added(renamed, Side.INPUT, "x");

        assertThat(added.inputs()).containsExactly(new MachinePin("x", "a"), new MachinePin("x-2", "x"));
    }

    @Test
    void removingAnUnusedPinDropsIt() {
        StateMachine machine = MachinePinEdits.added(detector, Side.INPUT, "b");

        assertThat(MachinePinEdits.removed(machine, Side.INPUT, "b").inputs()).containsExactly(MachinePin.named("x"));
    }

    @Test
    void removingAUsedPinThrows() {
        assertThatThrownBy(() -> MachinePinEdits.removed(detector, Side.INPUT, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachinePinEdits.removed(detector, Side.OUTPUT, "Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movingAPinKeepsEveryId() {
        StateMachine machine = MachinePinEdits.renamed(detector.withPinNames(List.of("x", "y", "w"), List.of("Z")),
                Side.INPUT, "y", "b");

        StateMachine moved = MachinePinEdits.moved(machine, Side.INPUT, 2, 0);

        assertThat(moved.inputs()).containsExactly(MachinePin.named("w"), MachinePin.named("x"),
                new MachinePin("y", "b"));
    }

    @Test
    void theUsesOfANameAreTheStatesAndArrowsThatHoldIt() {
        MachinePinUses.Uses inputUses = MachinePinUses.of(detector, "x");
        MachinePinUses.Uses outputUses = MachinePinUses.of(detector, "Z");

        assertThat(inputUses.transitionIds()).containsExactly("t0", "t1", "t2", "t4", "t5");
        assertThat(inputUses.stateIds()).isEmpty();
        assertThat(inputUses.count()).isEqualTo(5);
        assertThat(outputUses.stateIds()).containsExactly("s0", "s1", "s2");
        assertThat(MachinePinUses.of(detector, "nadie").isUnused()).isTrue();
    }
}
