package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The test mode's draft: one undo history for the diagram and the tests, in memory only. */
class MachineDraftTest {

    private final StateMachine base = MachineExamples.twoOnesDetector();
    private final MachineDraft draft = new MachineDraft(base);
    private final MachineTest test = MachineTest.empty("p1", "Prueba 1", MachineTests.DEFAULT_COLUMNS);

    @Test
    void aNewDraftIsTheBaseWithNothingToUndo() {
        assertThat(draft.current()).isSameAs(base);
        assertThat(draft.base()).isSameAs(base);
        assertThat(draft.canUndo()).isFalse();
        assertThat(draft.canRedo()).isFalse();
        assertThat(draft.undoName()).isEmpty();
        assertThat(draft.diagramChanged()).isFalse();
        assertThat(draft.testsChanged()).isFalse();
    }

    @Test
    void undoAndRedoWalkTheDiagramAndTheTestsTogether() {
        StateMachine moved = base.withState("s1", state -> state.withName("Uno"));
        StateMachine tested = moved.withTests(List.of(test));

        draft.edited(moved, "command.renameState");
        draft.edited(tested, "command.editTest");

        assertThat(draft.undoName()).contains("command.editTest");
        draft.undo();
        assertThat(draft.current()).isEqualTo(moved);
        assertThat(draft.undoName()).contains("command.renameState");
        assertThat(draft.redoName()).contains("command.editTest");
        draft.undo();
        assertThat(draft.current()).isEqualTo(base);
        assertThat(draft.canUndo()).isFalse();
        draft.redo();
        draft.redo();
        assertThat(draft.current()).isEqualTo(tested);
        assertThat(draft.canRedo()).isFalse();
    }

    @Test
    void aNewEditClearsTheRedo() {
        draft.edited(base.withEdge(StateMachine.EDGE_RISING), "command.editMachine");
        draft.undo();

        draft.edited(base.withTests(List.of(test)), "command.editTest");

        assertThat(draft.canRedo()).isFalse();
    }

    @Test
    void anEditThatChangesNothingIsNoStep() {
        draft.edited(base, "command.editMachine");

        assertThat(draft.canUndo()).isFalse();
    }

    @Test
    void aTestEditChangesTheTestsButNotTheDiagram() {
        MachineTest withValue = MachineTests.withExpected(test, 3, "Z", LogicValue.ONE);

        draft.edited(base.withTests(List.of(withValue)), "command.editTest");

        assertThat(draft.testsChanged()).isTrue();
        assertThat(draft.diagramChanged()).isFalse();
    }

    @Test
    void aDiagramEditChangesTheDiagramButNotTheTests() {
        draft.edited(base.withEdge(StateMachine.EDGE_RISING), "command.editMachine");

        assertThat(draft.diagramChanged()).isTrue();
        assertThat(draft.testsChanged()).isFalse();
    }

    @Test
    void undoingBackToTheBaseChangesNothing() {
        draft.edited(base.withEdge(StateMachine.EDGE_RISING), "command.editMachine");
        draft.undo();

        assertThat(draft.diagramChanged()).isFalse();
    }

    @Test
    void theHistoryKeepsOnlyTheLastTwoHundredSteps() {
        for (int step = 1; step <= MachineDraft.MAXIMUM_STEPS + 5; step++) {
            String name = "Paso " + step;
            draft.edited(base.withState("s0", state -> state.withName(name)), name);
        }

        int undone = 0;
        while (draft.canUndo()) {
            draft.undo();
            undone++;
        }

        assertThat(undone).isEqualTo(MachineDraft.MAXIMUM_STEPS);
        assertThat(draft.current()).isNotEqualTo(base);
    }

    @Test
    void rebasingKeepsTheDraftAndItsHistoryAndComparesWithTheNewBase() {
        StateMachine tested = base.withTests(List.of(MachineTests.withInput(test, 0, "x", true)));
        draft.edited(tested, "command.editTest");

        draft.rebased(tested);

        assertThat(draft.base()).isEqualTo(tested);
        assertThat(draft.current()).isEqualTo(tested);
        assertThat(draft.testsChanged()).isFalse();
        assertThat(draft.canUndo()).isTrue();
    }

    /** Review of hito 4, finding 2: after a rebase, an undo in the circuit makes the real machine differ again. */
    @Test
    void comparedWithTheRealMachineTheDraftSeesWhatTheCircuitLacksWhateverItsBase() {
        StateMachine tested = base.withTests(List.of(MachineTests.withInput(test, 0, "x", true)));
        StateMachine moved = tested.withInitialStateId(java.util.Optional.of("s1"));
        draft.edited(moved, "command.editMachine");
        draft.rebased(moved); // «Aplicar y guardar»

        // the circuit's step undone: the real machine is the old one again
        assertThat(draft.diagramChanged()).isFalse();
        assertThat(draft.diagramDiffersFrom(base)).isTrue();
        assertThat(draft.testsDifferFrom(base)).isTrue();
        assertThat(draft.diagramDiffersFrom(moved)).isFalse();
        assertThat(draft.testsDifferFrom(moved)).isFalse();
    }

    @Test
    void theTestsAreComparedFittedToTheRealMachinesPins() {
        // the draft gave a value to a pin the real machine lacks: that value is not a difference
        draft.edited(base.withTests(List.of(MachineTests.withInput(test, 0, "ghost", true))), "command.editTest");

        assertThat(draft.testsDifferFrom(base.withTests(List.of(test)))).isFalse();
        assertThat(draft.testsDifferFrom(base)).isTrue();
    }
}
