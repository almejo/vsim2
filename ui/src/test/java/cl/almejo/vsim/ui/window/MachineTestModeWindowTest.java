package cl.almejo.vsim.ui.window;

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
    private EditorView testingWithChanges(WindowTabs where) {
        where.addTab(document, Optional.empty());
        EditorView inside = where.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
        MachineMode mode = MachineMode.of(inside).orElseThrow();
        mode.startTest();
        MachineTestModes.setFirstInput(inside);
        mode.canvas().edit(machine -> machine.withInitialStateId(Optional.of("s1")));
        return inside;
    }

    /** Review finding 1: the draft and its unwritten tests travel with the tab, and the circuit is untouched. */
    @Test
    void movingTheGroupToAnotherWindowTakesTheTestAlong() {
        EditorView inside = testingWithChanges(tabs);
        MachineTestModes.ScriptedQuestions questions = MachineTestModes.answerWith(inside,
                new MachineTestModes.ScriptedQuestions());

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
}
