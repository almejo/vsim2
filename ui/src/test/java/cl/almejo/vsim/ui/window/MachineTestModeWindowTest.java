package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.machine.MachineTestModes;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's test mode on the window's ways out (review of hito 4): moving the group to another window takes the test
 * along, and Cancelar at «¿Guardar cambios?» leaves the test mode and the circuit's history as they were.
 */
class MachineTestModeWindowTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;
    private WindowTabs otherTabs;
    private OpenDocument document;
    private StateMachine real;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new MachineModeExtension()));
        tabs = new WindowTabs(host, place -> new EditorView(place, services, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
        otherTabs = new WindowTabs(otherHost, place -> new EditorView(place, services, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        real = circuitsMachine();
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        otherTabs.disposeAll();
        document.close();
    }

    private StateMachine circuitsMachine() {
        return document.document().definition(MachineFixtures.DEFINITION_ID)
                .flatMap(ComponentDefinition::machine).orElseThrow();
    }

    /** The circuit's tab and the machine's tab, the latter testing with a changed diagram and a changed test. */
    private EditorView testingWithChanges(WindowTabs where, MachineTestModes.ScriptedQuestions questions) {
        where.addTab(document, Optional.empty());
        EditorView inside = where.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
        MachineTestModes.answerWith(inside, questions); // before the test starts: it asks through these
        MachineMode mode = MachineMode.of(inside).orElseThrow();
        mode.startTest();
        MachineTestModes.setFirstInput(inside);
        mode.canvas().edit(machine -> machine.withInitialStateId(Optional.of("s1")));
        return inside;
    }

    /** Review finding 1: the draft and its unwritten tests travel with the tab, and the circuit is untouched. */
    @Test
    void movingTheGroupToAnotherWindowTakesTheTestAlong() {
        MachineTestModes.ScriptedQuestions questions = new MachineTestModes.ScriptedQuestions();
        testingWithChanges(tabs, questions);

        tabs.moveGroupTo(document, otherTabs, 0);

        assertThat(questions.asked()).isEmpty();
        EditorView arrived = otherTabs.viewsOf(document).stream()
                .filter(view -> view.openDocument().isBody()).findFirst().orElseThrow();
        assertThat(MachineMode.of(arrived).orElseThrow().isTesting()).isTrue();
        StateMachine draft = MachineTestModes.draftOf(arrived).orElseThrow();
        assertThat(draft.initialStateId()).contains("s1");
        assertThat(draft.tests()).hasSize(1);
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(arrived.draftGuards().isEmpty()).isFalse();
        // it goes on there as before: Deshacer walks the draft's steps
        assertThat(String.valueOf(arrived.actions().undo().getValue(javax.swing.Action.NAME)))
                .isEqualTo("Deshacer (prueba): editar la máquina");
    }

    private String stateName(String stateId) {
        return circuitsMachine().state(stateId).orElseThrow().name();
    }

    private EditorView machineTab() {
        tabs.addTab(document, Optional.empty());
        return tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
    }

    /**
     * Review finding 12: Ctrl+W with a state's name half typed in test mode: the name is put in the draft first, so
     * the close asks about the changed diagram, and «Aplicar» keeps it.
     */
    @Test
    void closingWithANameHalfTypedInTestModeAsksAndAplicarKeepsIt() {
        EditorView inside = machineTab();
        MachineTestModes.ScriptedQuestions questions = MachineTestModes.answerWith(inside,
                new MachineTestModes.ScriptedQuestions().then("Aplicar"));
        MachineMode.of(inside).orElseThrow().startTest();
        MachineTestModes.typeStateName(inside, "s1", "UNO");

        tabs.closeTab(inside);

        assertThat(questions.asked()).hasSize(1);
        assertThat(stateName("s1")).isEqualTo("UNO");
        assertThat(tabs.views()).doesNotContain(inside);
    }

    /** Review finding 12, outside test mode: Ctrl+W puts the half-typed name in the circuit, as a circuit's edit. */
    @Test
    void closingWithANameHalfTypedPutsItInTheCircuit() {
        EditorView inside = machineTab();
        MachineTestModes.typeStateName(inside, "s1", "UNO");

        tabs.closeTab(inside);

        assertThat(stateName("s1")).isEqualTo("UNO");
    }

    /** A step in the circuit's redo, so a Cancelar can be seen to keep it. */
    private Command aStepToRedo() {
        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID,
                real.withInitialStateId(Optional.of("s2"))));
        document.history().undo();
        return document.history().redoCommand().orElseThrow();
    }

    private void assertEverythingIsAsItWas(EditorView inside, Optional<Command> undo, Command redo) {
        assertThat(MachineMode.of(inside).orElseThrow().isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(inside).orElseThrow().initialStateId()).contains("s1");
        assertThat(MachineTestModes.draftOf(inside).orElseThrow().tests()).hasSize(1);
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(document.history().undoCommand()).isEqualTo(undo);
        assertThat(document.history().redoCommand()).contains(redo);
        assertThat(document.isModified()).isFalse();
    }

    /** Review finding 3: «Aplicar», then Cancelar at «¿Guardar cambios?»: the test goes on, the circuit as it was. */
    @Test
    void cancelarAtTheSaveQuestionAfterAplicarLeavesEverythingAsItWas() {
        Command redo = aStepToRedo();
        document.history().markSaved();
        Optional<Command> undo = document.history().undoCommand();
        EditorView inside = testingWithChanges(tabs, new MachineTestModes.ScriptedQuestions().then("Aplicar"));
        List<StateMachine> seenBySaveQuestion = new ArrayList<>();

        boolean closes = CloseSequence.confirm(tabs.inlineEditors(), tabs.drafts(), () -> {
            seenBySaveQuestion.add(circuitsMachine());
            return false;
        });

        assertThat(closes).isFalse();
        // the save question saw what was applied, so «Guardar» would have saved it
        assertThat(seenBySaveQuestion.getFirst().initialStateId()).contains("s1");
        assertThat(seenBySaveQuestion.getFirst().tests()).hasSize(1);
        assertEverythingIsAsItWas(inside, undo, redo);
    }

    /** Review finding 3: only the tests changed (written without a question), then Cancelar: nothing is left. */
    @Test
    void cancelarAtTheSaveQuestionOfTheGroupLeavesTheTestsInTheDraftOnly() {
        Command redo = aStepToRedo();
        document.history().markSaved();
        Optional<Command> undo = document.history().undoCommand();
        tabs.addTab(document, Optional.empty());
        EditorView inside = tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
        MachineMode.of(inside).orElseThrow().startTest();
        MachineTestModes.setFirstInput(inside);
        host.answerToSave = false;

        assertThat(tabs.closeGroup(document)).isFalse();

        assertThat(host.saveQuestions).containsExactly(document);
        assertThat(MachineMode.of(inside).orElseThrow().isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(inside).orElseThrow().tests()).hasSize(1);
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(document.history().undoCommand()).isEqualTo(undo);
        assertThat(document.history().redoCommand()).contains(redo);
    }

    /** Review finding 3: «Dejar como estaba» and Cancelar; then the close goes on and the tests are kept. */
    @Test
    void dejarComoEstabaThenCancelarThenClosingKeepsTheTests() {
        Command redo = aStepToRedo();
        document.history().markSaved();
        Optional<Command> undo = document.history().undoCommand();
        MachineTestModes.ScriptedQuestions questions = new MachineTestModes.ScriptedQuestions()
                .then("Dejar como estaba").then("Dejar como estaba");
        EditorView inside = testingWithChanges(tabs, questions);

        assertThat(CloseSequence.confirm(tabs.inlineEditors(), tabs.drafts(), () -> false)).isFalse();
        assertEverythingIsAsItWas(inside, undo, redo);

        assertThat(CloseSequence.confirm(tabs.inlineEditors(), tabs.drafts(), () -> true)).isTrue();
        assertThat(questions.asked()).hasSize(2);
        assertThat(MachineMode.of(inside).orElseThrow().isTesting()).isFalse();
        assertThat(circuitsMachine().initialStateId()).isEqualTo(real.initialStateId());
        assertThat(circuitsMachine().tests()).hasSize(1);
    }
}
