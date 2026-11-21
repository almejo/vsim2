package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.io.RecoveryTestDrafts;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineDraft;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.session.RecoveryDrafts;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The machines' test drafts in the recovery copies (fix round 2 of hito 4), so a crash while testing loses nothing.
 * While a machine of a circuit is being tested and its draft holds something the circuit does not, the circuit's
 * autosave copy carries it: the draft's tests written into the machine as «Salir de la prueba» would write them, and
 * the draft itself apart ({@link RecoveryTestDrafts}, a field only recovery copies have: the saved format does not
 * change). Every draft edit counts as a change for autosave. When such a copy is recovered, the machine's tab opens in
 * test mode on the draft as it was ({@link #resume}). EDT only.
 */
public final class MachineTestRecovery implements RecoveryDrafts {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineTestRecovery.class);

    @Override
    public boolean holds(OpenDocument document) {
        return !unwritten(document).isEmpty();
    }

    @Override
    public long changeCount(OpenDocument document) {
        return MachineTestsUnderWay.changeCount(document);
    }

    @Override
    public String serialized(OpenDocument document) {
        Map<String, StateMachine> drafts = unwritten(document);
        if (drafts.isEmpty()) {
            return document.serialized();
        }
        String content = document.serializedWith(definition -> withTestsOf(definition, drafts));
        return RecoveryTestDrafts.withDrafts(content, drafts);
    }

    /** {@code definition} with the tests of its draft in {@code drafts} written in, fitted to its pins; else itself. */
    private static ComponentDefinition withTestsOf(ComponentDefinition definition, Map<String, StateMachine> drafts) {
        StateMachine draft = drafts.get(definition.id());
        return draft == null ? definition : definition.machine()
                .map(real -> definition.withMachine(real.withTests(MachineTests.fittedTo(real, draft.tests()))))
                .orElse(definition);
    }

    /** The drafts of {@code document}'s machines being tested that hold something the circuit does not, by id. */
    private static Map<String, StateMachine> unwritten(OpenDocument document) {
        Map<String, StateMachine> drafts = new TreeMap<>();
        MachineTestsUnderWay.of(document).forEach((definitionId, test) -> {
            if (test.isOver()) {
                return;
            }
            MachineDraft draft = test.draft();
            document.document().definition(definitionId).flatMap(ComponentDefinition::machine)
                    .filter(real -> draft.diagramDiffersFrom(real) || draft.testsDifferFrom(real))
                    .ifPresent(real -> drafts.put(definitionId, draft.current()));
        });
        return drafts;
    }

    /** The drafts the recovery copy {@code copy} carries, by definition id; empty when none or unreadable. */
    public static Map<String, StateMachine> draftsIn(Path copy) {
        try {
            return RecoveryTestDrafts.drafts(Files.readString(copy));
        } catch (IOException exception) {
            LOGGER.warn("Could not read the test drafts of the recovery copy {}", copy, exception);
            return Map.of();
        }
    }

    /**
     * The recovered {@code document}'s machines with a draft in {@code drafts} open in test mode in {@code tabs} (the
     * window that shows the document), each on its draft; the last one stays in front. A draft whose machine is no
     * longer in the circuit is left out.
     */
    public static void resume(WindowTabs tabs, OpenDocument document, Map<String, StateMachine> drafts) {
        // every tab first, then the tests: entering the next machine never leaves a test with a changed diagram
        Map<EditorView, StateMachine> views = new LinkedHashMap<>();
        drafts.forEach((definitionId, draft) -> {
            Optional<ComponentDefinition> definition = document.document().definition(definitionId)
                    .filter(ComponentDefinition::isMachine);
            if (definition.isEmpty()) {
                LOGGER.warn("The recovered test draft of {} has no machine in the circuit", definitionId);
                return;
            }
            views.put(tabs.enter(document, definitionId, Optional.empty(), Optional.empty()), draft);
        });
        views.forEach((view, draft) -> MachineMode.of(view).ifPresent(mode -> mode.resumeTest(draft)));
    }
}
