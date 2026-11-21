package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.RecoveryTestDrafts;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.machine.MachineTestModes;
import cl.almejo.vsim.ui.machine.MachineTestRecovery;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recovery copy carries a machine's open test draft (fix round 2 of hito 4): the tests written in as «Salir de la
 * prueba» would write them, the draft's diagram apart; every draft edit counts for autosave; and the recovered circuit
 * reopens the machine in test mode with the draft as it was.
 */
class MachineTestRecoveryTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final MachineTestRecovery recovery = new MachineTestRecovery();
    private final List<OpenDocument> documents = new ArrayList<>();
    private WindowTabs tabs;
    private WindowTabs otherTabs;
    private OpenDocument document;
    private StateMachine real;

    @TempDir
    Path folder;

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
        documents.add(document);
        document.history().markSaved();
        real = machineOf(document.document());
        tabs.addTab(document, Optional.empty());
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        otherTabs.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    private static StateMachine machineOf(CircuitDocument circuit) {
        return circuit.definition(MachineFixtures.DEFINITION_ID).flatMap(ComponentDefinition::machine).orElseThrow();
    }

    private EditorView testing() {
        EditorView inside = tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()), Optional.empty());
        MachineMode.of(inside).orElseThrow().startTest();
        return inside;
    }

    /** A test with a changed test and a changed initial state. */
    private EditorView testingWithChanges() {
        EditorView inside = testing();
        MachineTestModes.setFirstInput(inside);
        MachineMode.of(inside).orElseThrow().canvas().edit(machine -> machine.withInitialStateId(Optional.of("s1")));
        return inside;
    }

    @Test
    void theRecoveryCopyCarriesTheDraftApartAndTheTestsWrittenIn() throws Exception {
        testingWithChanges();

        assertThat(document.isModified()).isFalse();
        assertThat(recovery.holds(document)).isTrue();
        String copy = recovery.serialized(document);

        StateMachine copied = machineOf(new CircuitReader(registry).read(copy));
        assertThat(copied.tests()).hasSize(1);
        assertThat(copied.initialStateId()).isEqualTo(real.initialStateId());
        StateMachine draft = RecoveryTestDrafts.drafts(copy).get(MachineFixtures.DEFINITION_ID);
        assertThat(draft.initialStateId()).contains("s1");
        assertThat(draft.tests()).hasSize(1);
        // the circuit and its saved form are untouched
        assertThat(machineOf(document.document())).isEqualTo(real);
        assertThat(document.serialized()).doesNotContain(RecoveryTestDrafts.FIELD);
    }

    @Test
    void everyDraftEditCountsAndADraftLikeTheCircuitHoldsNothing() {
        EditorView inside = testing();
        long untouched = recovery.changeCount(document);
        assertThat(recovery.holds(document)).isFalse();

        MachineTestModes.setFirstInput(inside);

        long edited = recovery.changeCount(document);
        assertThat(edited).isNotEqualTo(untouched);
        assertThat(recovery.holds(document)).isTrue();

        inside.actions().undo().actionPerformed(null);

        assertThat(recovery.changeCount(document)).isNotEqualTo(edited);
        assertThat(recovery.holds(document)).isFalse();
        assertThat(recovery.serialized(document)).isEqualTo(document.serialized());
    }

    @Test
    void aRecoveredCopyReopensTheMachineInTestModeWithTheDraft() throws Exception {
        EditorView inside = testingWithChanges();
        Path copy = folder.resolve("copia.vsim");
        Files.writeString(copy, recovery.serialized(document));
        MachineTestModes.leaveTheTest(inside);

        OpenDocument recovered = OpenDocument.openRecovered(copy, null, registry);
        documents.add(recovered);
        Map<String, StateMachine> drafts = MachineTestRecovery.draftsIn(copy);
        otherTabs.addTab(recovered, Optional.empty());

        MachineTestRecovery.resume(otherTabs, recovered, drafts);

        EditorView reopened = otherTabs.activeView();
        assertThat(reopened.openDocument().isBody()).isTrue();
        assertThat(MachineMode.of(reopened).orElseThrow().isTesting()).isTrue();
        StateMachine draft = MachineTestModes.draftOf(reopened).orElseThrow();
        assertThat(draft.initialStateId()).contains("s1");
        assertThat(draft.tests()).hasSize(1);
        StateMachine recoveredMachine = machineOf(recovered.document());
        assertThat(recoveredMachine.initialStateId()).isEqualTo(real.initialStateId());
        assertThat(recoveredMachine.tests()).hasSize(1);
        assertThat(String.valueOf(reopened.actions().undo().getValue(Action.NAME)))
                .isEqualTo("Deshacer (prueba): recuperar el borrador");
    }
}
