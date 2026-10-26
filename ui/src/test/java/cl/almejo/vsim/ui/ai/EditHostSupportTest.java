package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.PinTarget;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditHostSupportTest {

    private final List<OpenDocument> open = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open));
    private final AutomationApi api = new AutomationApi(host);
    private OpenDocument circuit;
    private DocumentId id;

    @BeforeEach
    void openACircuit() throws Exception {
        Messages.initialize(Messages.SPANISH);
        circuit = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        open.add(circuit);
        id = host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS).getFirst().id();
    }

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void anEditIsOneUndoStepOfTheOpenCircuitNamedAsTheAisChange() throws Exception {
        EditReceipt receipt = api.editStep(request("op-1", Optional.empty(), placePart("gate", "nand2", 0, 0),
                placePart("lamp", "led", 10, -1),
                new EditOperation.Connect(PinTarget.named("new:gate", "out"), PinTarget.named("new:lamp", "in"),
                        List.of())), Optional.empty()).receipt();

        onEditorThread(() -> {
            assertThat(circuit.document().components()).hasSize(2);
            assertThat(circuit.document().wires()).isNotEmpty();
            assertThat(receipt.version().value()).isEqualTo(circuit.contentVersion());
            assertThat(circuit.history().undoDescription()).contains("command.aiEdit");
            assertThat(Messages.commandName("command.aiEdit")).isEqualTo("cambio de la IA");
            circuit.history().undo();
            assertThat(circuit.document().isEmpty()).isTrue();
        });
    }

    @Test
    void theEditRunsOnTheEditorThread() throws Exception {
        assertThat(host.onCircuit(id, editable -> SwingUtilities.isEventDispatchThread()).get(5, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    void theAisUndoWorksFromAButtonOnTheEditorThreadWithoutWaitingForItself() throws Exception {
        TaskInfo task = api.beginTask(id, "Compuerta", List.of());
        api.editStep(request("op-1", Optional.of(new TaskStep(task.taskId(), "1", "1", "Coloco la NAND")),
                placePart("gate", "nand2", 0, 0)), Optional.empty()).receipt();
        AtomicReference<UndoOutcome> outcome = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> outcome.set(api.undoTask(task.taskId())));

        assertThat(outcome.get()).isEqualTo(new UndoOutcome(1, false));
        onEditorThread(() -> assertThat(circuit.document().isEmpty()).isTrue());
    }

    @Test
    void anEditPreparedBeforeTheUsersMoveIsStale() throws Exception {
        EditRequest prepared = request("op-1", Optional.empty(), placePart("gate", "nand2", 0, 0));
        SwingUtilities.invokeAndWait(() -> place(circuit, StandardComponentTypes.NOT, 20, 0, Map.of()));

        assertThatThrownBy(() -> api.editStep(prepared, Optional.empty()).receipt())
                .isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.STALE_DOCUMENT));
        onEditorThread(() -> assertThat(circuit.document().components()).hasSize(1));
    }

    /** IA.3: saving changes no content, so an edit prepared before the user's Ctrl+S is still current. */
    @Test
    void anEditPreparedBeforeTheUserSavesIsStillCurrent() throws Exception {
        EditRequest prepared = request("op-1", Optional.empty(), placePart("gate", "nand2", 0, 0));
        SwingUtilities.invokeAndWait(() -> circuit.history().markSaved());

        assertThat(api.editStep(prepared, Optional.empty()).receipt().applied()).isTrue();
        onEditorThread(() -> assertThat(circuit.document().components()).hasSize(1));
    }

    @Test
    void aClosedCircuitCannotBeEdited() throws Exception {
        EditRequest request = request("op-1", Optional.empty(), placePart("gate", "nand2", 0, 0));
        open.remove(circuit);
        circuit.close();

        assertThatThrownBy(() -> api.editStep(request, Optional.empty()).receipt())
                .isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
    }

    @Test
    void theIdOfAnEditedCircuitStaysTheSame() throws Exception {
        api.editStep(request("op-1", Optional.empty(), placePart("gate", "nand2", 0, 0)), Optional.empty()).receipt();

        List<DocumentSummary> documents = host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS);

        assertThat(documents).extracting(DocumentSummary::id).containsExactly(id);
        onEditorThread(() -> assertThat(circuit.document().component(new ElementId("c1"))).isPresent());
    }

    private EditRequest request(String operationId, Optional<TaskStep> step, EditOperation... operations)
            throws Exception {
        long version = host.onEditorThread(access -> circuit.contentVersion()).get(5, TimeUnit.SECONDS);
        return new EditRequest(id, new cl.almejo.vsim.automation.DocumentVersion(version), operationId, step,
                List.of(operations), false);
    }

    private static EditOperation.PlacePart placePart(String alias, String typeId, int x, int y) {
        return new EditOperation.PlacePart(Optional.of(alias), typeId, x, y, Rotation.R0, Map.of(), "");
    }

    private static void onEditorThread(Runnable check) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                check.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
