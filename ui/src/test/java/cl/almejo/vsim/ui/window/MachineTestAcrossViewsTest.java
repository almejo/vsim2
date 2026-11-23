package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
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

import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One test mode per machine, shared by every view of it that tests (fix round 2 of hito 4, task G): «Nueva vista» of a
 * machine being tested tests too, and «Probar…» in another view of it joins the test; both views show and edit the
 * same draft, a switch between them asks nothing, and the window asks once about the draft only when the last view of
 * it goes or the user leaves to another place.
 */
class MachineTestAcrossViewsTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final MachineTestModes.ScriptedQuestions questions = new MachineTestModes.ScriptedQuestions();
    private WindowTabs tabs;
    private WindowTabs otherTabs;
    private OpenDocument document;
    private StateMachine real;
    private EditorView main;

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
        main = tabs.addTab(document, Optional.empty());
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

    private EditorView machineTab() {
        EditorView inside = tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
        MachineTestModes.answerWith(inside, questions);
        return inside;
    }

    private static MachineMode modeOf(EditorView view) {
        return MachineMode.of(view).orElseThrow();
    }

    /** The machine's tab testing, with a changed test and (when {@code diagram}) a changed initial state. */
    private EditorView testing(boolean diagram) {
        EditorView inside = machineTab();
        modeOf(inside).startTest();
        MachineTestModes.setFirstInput(inside);
        if (diagram) {
            modeOf(inside).canvas().edit(machine -> machine.withInitialStateId(Optional.of("s1")));
        }
        return inside;
    }

    /** «Nueva vista» of the tested machine: it answers through the same script. */
    private EditorView newView() {
        EditorView second = tabs.newViewOfActivePlace();
        MachineTestModes.answerWith(second, questions);
        return second;
    }

    @Test
    void aNewWindowOfAMachineTabShowsTheMachineNotOnlyTheCircuit() {
        EditorView inside = machineTab();
        otherTabs.addTab(document, Optional.empty());

        tabs.showActivePlaceIn(otherTabs);

        assertThat(otherTabs.activeView()).isNotSameAs(inside);
        assertThat(MachineMode.of(otherTabs.activeView())).isPresent();
        assertThat(otherTabs.documents()).containsExactly(document);
    }

    @Test
    void aNewWindowOfAMachineBeingTestedJoinsTheTest() {
        EditorView inside = testing(true);
        otherTabs.addTab(document, Optional.empty());

        tabs.showActivePlaceIn(otherTabs);
        EditorView there = otherTabs.activeView();
        MachineTestModes.answerWith(there, questions);

        assertThat(modeOf(there).isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(there)).isEqualTo(MachineTestModes.draftOf(inside));
        assertThat(circuitsMachine()).isEqualTo(real);
    }

    @Test
    void aNewWindowOfTheCircuitTabAddsNothingToTheNewWindow() {
        EditorView mainThere = otherTabs.addTab(document, Optional.empty());

        tabs.showActivePlaceIn(otherTabs);

        assertThat(otherTabs.activeView()).isSameAs(mainThere);
    }

    @Test
    void aNewViewOfAMachineBeingTestedTestsTheSameDraft() {
        EditorView inside = testing(true);

        EditorView second = newView();

        assertThat(modeOf(second).isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(second)).isEqualTo(MachineTestModes.draftOf(inside));
        assertThat(second.draftGuards().isEmpty()).isFalse();
        // an edit in one view is the other's too
        modeOf(second).canvas().edit(machine -> machine.withInitialStateId(Optional.of("s2")));
        assertThat(MachineTestModes.draftOf(inside).orElseThrow().initialStateId()).contains("s2");
        // and Deshacer in the first walks it back
        inside.actions().undo().actionPerformed(null);
        assertThat(MachineTestModes.draftOf(second).orElseThrow().initialStateId()).contains("s1");
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void switchingBetweenTwoViewsOfTheTestedMachineAsksNothing() {
        EditorView inside = testing(true);
        EditorView second = newView();

        tabs.activate(inside);
        tabs.activate(second);
        tabs.activate(inside);

        assertThat(questions.asked()).isEmpty();
        assertThat(tabs.activeView()).isSameAs(inside);

        // leaving to the circuit asks, once («Seguir probando»: closed, the tab stays)
        tabs.activate(main);

        assertThat(questions.asked()).hasSize(1);
        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(modeOf(second).isTesting()).isTrue();
    }

    @Test
    void probarInAViewOpenedBeforeJoinsTheTest() {
        EditorView inside = machineTab();
        EditorView second = newView();
        modeOf(inside).startTest();
        MachineTestModes.setFirstInput(inside);

        assertThat(modeOf(second).isTesting()).isFalse();

        modeOf(second).startTest();

        assertThat(modeOf(second).isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(second).orElseThrow().tests()).hasSize(1);
        assertThat(MachineTestModes.draftOf(second)).isEqualTo(MachineTestModes.draftOf(inside));
    }

    @Test
    void closingOneOfTheViewsAsksNothingAndTheOtherGoesOnTesting() {
        EditorView inside = testing(true);
        EditorView second = newView();

        tabs.closeTab(second);

        assertThat(questions.asked()).isEmpty();
        assertThat(tabs.views()).doesNotContain(second);
        assertThat(modeOf(inside).isTesting()).isTrue();
        assertThat(MachineTestModes.draftOf(inside).orElseThrow().initialStateId()).contains("s1");

        // the last view of it asks, as before
        tabs.closeTab(inside);

        assertThat(questions.asked()).hasSize(1);
        assertThat(tabs.views()).contains(inside);
    }

    @Test
    void leavingInOneViewLeavesInEveryViewAndWritesTheTestsOnce() {
        EditorView inside = testing(false);
        EditorView second = newView();
        Optional<Command> stepBefore = document.history().undoCommand();

        MachineTestModes.leaveTheTest(second);

        assertThat(modeOf(inside).isTesting()).isFalse();
        assertThat(modeOf(second).isTesting()).isFalse();
        assertThat(circuitsMachine().tests()).hasSize(1);
        // one step: undoing it leaves the history as it was
        document.history().undo();
        assertThat(document.history().undoCommand()).isEqualTo(stepBefore);
        assertThat(inside.draftGuards().isEmpty()).isTrue();
        assertThat(second.draftGuards().isEmpty()).isTrue();
    }

    @Test
    void closingTheGroupAsksOnceForBothViews() {
        testing(true);
        newView();
        questions.then("Dejar como estaba");

        assertThat(CloseSequence.confirm(tabs.inlineEditors(), tabs.drafts(), () -> true)).isTrue();

        assertThat(questions.asked()).hasSize(1);
        assertThat(circuitsMachine().initialStateId()).isEqualTo(real.initialStateId());
        assertThat(circuitsMachine().tests()).hasSize(1);
    }

    @Test
    void savingAsksOnceForBothViews() {
        testing(true);
        newView();

        DraftGuard.Answer answer = DraftGuards.settle(tabs.viewsOf(document).stream()
                .map(EditorView::draftGuards).toList(), DraftGuard.Reason.SAVE);

        assertThat(answer).isEqualTo(DraftGuard.Answer.CANCEL);
        assertThat(questions.asked()).hasSize(1);
    }

    @Test
    void anEditInTheOtherViewIsNotAChangeFromOutside() {
        EditorView inside = testing(false);
        EditorView second = newView();

        modeOf(second).canvas().edit(machine -> machine.withInitialStateId(Optional.of("s1")));

        assertThat(MachineTestModes.barMessage(inside)).doesNotContain("cambió afuera")
                .contains("cambiaste el diagrama");
        assertThat(MachineTestModes.barMessage(second)).doesNotContain("cambió afuera");
    }

    @Test
    void movingTheGroupWithTwoViewsTakesTheTestAlongInBoth() {
        testing(true);
        newView();

        tabs.moveGroupTo(document, otherTabs, 0);

        assertThat(questions.asked()).isEmpty();
        List<EditorView> arrived = otherTabs.viewsOf(document).stream()
                .filter(view -> view.openDocument().isBody()).toList();
        assertThat(arrived).hasSize(2);
        assertThat(arrived).allSatisfy(view -> {
            assertThat(modeOf(view).isTesting()).isTrue();
            assertThat(MachineTestModes.draftOf(view).orElseThrow().initialStateId()).contains("s1");
        });
        assertThat(circuitsMachine()).isEqualTo(real);
    }
}
