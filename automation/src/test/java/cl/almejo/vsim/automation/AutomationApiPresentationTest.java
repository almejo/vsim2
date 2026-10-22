package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.automation.present.FakePresentingHost;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.PresentedEdit;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The API's presenting calls (IA.2 Task 1): what the MCP tools and the «Presentar» panel use. */
class AutomationApiPresentationTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");

    private final FakePresentingHost host = new FakePresentingHost();
    private final FakePresentingHost.Circuit circuit = host.open(DOCUMENT.value());
    private final AutomationApi api = new AutomationApi(host);
    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();

    @Test
    void aProposedStepIsHeardAppliedOnSiguienteAndAwaited() throws InterruptedException {
        Runnable removal = api.addPresentationListener(new Recorder());
        host.present(DOCUMENT.value(), new PresentationSettings(true, PresentationPace.STEP_BY_STEP,
                Duration.ofSeconds(1)));
        TaskInfo task = api.beginTask(DOCUMENT, "Latch", List.of(new TaskStage("Compuertas", List.of("Coloco"))));
        assertThat(next()).isEqualTo("plan 0 0");

        PresentedEdit edit = api.editStep(request(task, "op-1"));

        assertThat(edit.status()).contains(StepStatus.PROPOSED);
        assertThat(api.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STILL_WAITING);
        assertThat(next()).isEqualTo("plan 0 0");
        assertThat(next()).isEqualTo("proposed Coloco");
        api.nextStep(task.taskId());
        assertThat(next()).isEqualTo("applied Coloco");
        assertThat(api.awaitStep(task.taskId()).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(circuit.document().components()).hasSize(1);

        removal.run();
        api.endTask(task.taskId());
    }

    @Test
    void detenerDropsTheProposedStep() throws InterruptedException {
        api.addPresentationListener(new Recorder());
        host.present(DOCUMENT.value(), new PresentationSettings(true, PresentationPace.STEP_BY_STEP,
                Duration.ofSeconds(1)));
        TaskInfo task = api.beginTask(DOCUMENT, "Latch", List.of(new TaskStage("Compuertas", List.of("Coloco"))));
        api.editStep(request(task, "op-1"));

        api.stopTask(task.taskId());

        assertThat(api.awaitStep(task.taskId()).status()).isEqualTo(StepStatus.STOPPED);
        assertThat(circuit.document().components()).isEmpty();
    }

    @Test
    void theAiCanAskForPresentarAndReadTheMode() {
        assertThat(api.presentationSettings(DOCUMENT).presenting()).isFalse();

        PresentationSettings settings = api.setPresenting(DOCUMENT, true);

        assertThat(settings.presenting()).isTrue();
        assertThat(api.presentationSettings(DOCUMENT)).isEqualTo(settings);
    }

    @Test
    void inWorkingModeEditStillReturnsTheAppliedReceipt() {
        TaskInfo task = api.beginTask(DOCUMENT, "Latch", List.of(new TaskStage("Compuertas", List.of("Coloco"))));

        EditRequest request = request(task, "op-1");

        assertThat(api.edit(request).applied()).isTrue();
        assertThat(api.editStep(request).status()).isEmpty();
        assertThat(circuit.document().components()).hasSize(1);
    }

    private String next() throws InterruptedException {
        String event = heard.poll(5, TimeUnit.SECONDS);
        assertThat(event).as("an event").isNotNull();
        return event;
    }

    private EditRequest request(TaskInfo task, String operationId) {
        return new EditRequest(DOCUMENT, host.onEditor(circuit::version), operationId,
                Optional.of(new TaskStep(task.taskId(), "Compuertas", "Coloco", "Coloco")),
                List.of(new PlacePart(Optional.of("gate"), "nand2", 0, 0, Rotation.R0, Map.of(), "")), false);
    }

    private final class Recorder implements PresentationListener {

        @Override
        public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
            heard.add("plan " + currentStage + " " + currentStep);
        }

        @Override
        public void stepProposed(StepPreview preview) {
            heard.add("proposed " + preview.text());
        }

        @Override
        public void stepApplied(StepPreview preview) {
            heard.add("applied " + preview.text());
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            heard.add("discarded " + why);
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            heard.add("checked " + stageIndex);
        }
    }
}
