package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.PresentedEdit;
import cl.almejo.vsim.automation.present.StageOrigin;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.automation.present.StoryboardState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.ai.EditorWindows;
import cl.almejo.vsim.ui.ai.PreferenceProposals;
import cl.almejo.vsim.ui.ai.PresentationPreferences;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The panel's buttons over the real API and host (IA.2 Task 1): the window's preferences choose the mode. */
class AutomationPresentationControlsTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<OpenDocument> open = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> List.of(), new Windows(), new PreferenceProposals(preferences));
    private final AutomationApi api = new AutomationApi(host);
    private final PresentationControls controls = new AutomationPresentationControls(api, Runnable::run,
            Runnable::run);
    private final List<GridPoint> travels = new CopyOnWriteArrayList<>();
    private OpenDocument circuit;
    private DocumentId id;
    private TaskInfo task;

    @BeforeEach
    void openACircuitInPasoAPaso() throws Exception {
        circuit = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        open.add(circuit);
        id = host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS).getFirst().id();
        SwingUtilities.invokeAndWait(() -> {
            preferences.setAiPresenting(true);
            preferences.setAiPace(PresentationPace.STEP_BY_STEP);
        });
        task = api.beginTask(id, "Compuerta", List.of(new TaskStage("Compuertas", List.of("Coloco la NAND"))));
    }

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void theHostReadsTheModeFromTheWindowsPreferences() {
        assertThat(api.presentationSettings(id)).isEqualTo(new PresentationSettings(true,
                PresentationPace.STEP_BY_STEP, Duration.ofSeconds(1)));
    }

    @Test
    void siguienteAppliesTheProposedStep() throws Exception {
        PresentedEdit edit = api.editStep(request("op-1"), Optional.empty());
        assertThat(edit.status()).contains(StepStatus.PROPOSED);
        assertThat(componentCount()).isZero();

        controls.next(task.taskId());

        assertThat(api.awaitStep(task.taskId(), Duration.ofSeconds(5)).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(componentCount()).isEqualTo(1);
    }

    @Test
    void hastaFinDeEtapaAppliesThePendingStepToo() throws Exception {
        api.editStep(request("op-1"), Optional.empty());

        controls.toStageEnd(task.taskId());

        assertThat(api.awaitStep(task.taskId(), Duration.ofSeconds(5)).status()).isEqualTo(StepStatus.APPLIED);
    }

    @Test
    void detenerDropsTheStep() throws Exception {
        api.editStep(request("op-1"), Optional.empty());

        controls.stop(task.taskId());

        assertThat(api.awaitStep(task.taskId(), Duration.ofSeconds(5)).status()).isEqualTo(StepStatus.STOPPED);
        assertThat(componentCount()).isZero();
    }

    // --- the storyboard's buttons (IA.2 Task 2) ---

    @Test
    void deshacerEtapaUndoesTheStagesStepsAndItsFrame() throws Exception {
        TaskInfo story = twoStages();
        api.beginStage(story.taskId(), 0, Optional.empty());
        placeAndApply(story, "Compuertas", "op-1", 0);
        api.beginStage(story.taskId(), 1, Optional.empty()); // the first stage gets its frame
        assertThat(frameCount()).isEqualTo(1);

        List<StageUndoOutcome> outcomes = new ArrayList<>();

        controls.undoStage(story.taskId(), outcomes::add);

        assertThat(outcomes).containsExactly(new StageUndoOutcome(0, 2, StageUndoOutcome.StoppedAt.NOTHING, false));
        assertThat(componentCount()).isZero();
        assertThat(frameCount()).isZero();
    }

    /** Review I2: pressed again, it goes back one more stage. */
    @Test
    void deshacerEtapaGoesBackStageByStage() throws Exception {
        TaskInfo story = twoStages();
        api.beginStage(story.taskId(), 0, Optional.empty());
        placeAndApply(story, "Compuertas", "op-1", 0);
        StageOrigin second = api.beginStage(story.taskId(), 1, Optional.empty());
        placeAndApply(story, "Salida", "op-2", second.x());
        api.endTask(story.taskId(), false).task();
        List<StageUndoOutcome> outcomes = new ArrayList<>();
        List<StoryboardState> states = new ArrayList<>();

        controls.undoStage(story.taskId(), outcomes::add);
        controls.storyboardState(story.taskId(), states::add);
        controls.undoStage(story.taskId(), outcomes::add);
        controls.storyboardState(story.taskId(), states::add);

        assertThat(outcomes).extracting(StageUndoOutcome::stageIndex).containsExactly(1, 0);
        assertThat(states).containsExactly(new StoryboardState(true, false), new StoryboardState(false, false));
        assertThat(componentCount()).isZero();
    }

    @Test
    void dejarSoloElResultadoKeepsTheLastStageAsOneUndoStep() throws Exception {
        TaskInfo story = twoStages();
        api.beginStage(story.taskId(), 0, Optional.empty());
        placeAndApply(story, "Compuertas", "op-1", 0);
        StageOrigin second = api.beginStage(story.taskId(), 1, Optional.empty());
        placeAndApply(story, "Salida", "op-2", second.x());
        api.endTask(story.taskId(), false).task();
        assertThat(frameCount()).isEqualTo(2);

        List<Boolean> kept = new ArrayList<>();
        controls.keepOnlyResult(story.taskId(), kept::add);

        assertThat(kept).containsExactly(true);

        assertThat(frameCount()).isEqualTo(1);
        assertThat(componentCount()).isEqualTo(1);
        SwingUtilities.invokeAndWait(() -> circuit.history().undo());
        assertThat(frameCount()).isEqualTo(2);
        assertThat(componentCount()).isEqualTo(2);
    }

    @Test
    void aStageThatBeginsInPresentarTakesTheWindowsToItsOrigin() {
        TaskInfo story = twoStages();

        StageOrigin origin = api.beginStage(story.taskId(), 0, Optional.empty());

        assertThat(travels).containsExactly(new GridPoint(origin.x(), origin.y()));
    }

    private TaskInfo twoStages() {
        return api.beginTask(id, "Latch", List.of(new TaskStage("Compuertas", List.of("Coloco la NAND")),
                new TaskStage("Salida", List.of("Coloco la NAND"))));
    }

    private void placeAndApply(TaskInfo story, String stage, String operationId, int x) throws Exception {
        long version = host.onEditorThread(access -> circuit.contentVersion()).get(5, TimeUnit.SECONDS);
        api.editStep(new EditRequest(id, new DocumentVersion(version), operationId,
                Optional.of(new TaskStep(story.taskId(), stage, "Coloco la NAND", "Coloco la NAND")),
                List.of(new EditOperation.PlacePart(Optional.empty(), "nand2", x, 4, Rotation.R0, Map.of(), "")),
                false), Optional.empty());
        controls.next(story.taskId());
        assertThat(api.awaitStep(story.taskId(), Duration.ofSeconds(5)).status()).isEqualTo(StepStatus.APPLIED);
    }

    private int frameCount() throws Exception {
        AtomicInteger count = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> count.set(circuit.document().frames().size()));
        return count.get();
    }

    @Test
    void theCallsNeverRunOnTheCallersThread() {
        AtomicInteger queued = new AtomicInteger();
        PresentationControls deferred = new AutomationPresentationControls(api, work -> queued.incrementAndGet(),
                Runnable::run);

        deferred.next(task.taskId());
        deferred.stop(task.taskId());

        assertThat(queued).hasValue(2);
    }

    private int componentCount() throws Exception {
        AtomicInteger count = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> count.set(circuit.document().components().size()));
        return count.get();
    }

    private EditRequest request(String operationId) throws Exception {
        long version = host.onEditorThread(access -> circuit.contentVersion()).get(5, TimeUnit.SECONDS);
        return new EditRequest(id, new DocumentVersion(version), operationId,
                Optional.of(new TaskStep(task.taskId(), "Compuertas", "Coloco la NAND", "Coloco la NAND")),
                List.of(new EditOperation.PlacePart(Optional.of("gate"), "nand2", 0, 0, Rotation.R0, Map.of(), "")),
                false);
    }

    /** One window whose preferences are the test's. */
    private final class Windows implements EditorWindows {

        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException("not in this test");
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return Optional.empty();
        }

        @Override
        public PresentationSettings presentationSettings(OpenDocument document) {
            return PresentationPreferences.read(preferences);
        }

        @Override
        public void setPresenting(OpenDocument document, boolean presenting) {
            preferences.setAiPresenting(presenting);
        }

        @Override
        public void travelToStageOrigin(OpenDocument document, GridPoint origin) {
            travels.add(origin);
        }
    }
}
