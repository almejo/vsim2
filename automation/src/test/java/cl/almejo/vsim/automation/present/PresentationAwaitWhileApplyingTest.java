package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.command.HistoryListener;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code vsim_await_step} that arrives while «Siguiente» writes the step (IA.2 Task 5's race): it waits for that step
 * rather than finding nothing pending. Deterministic: the await is made from inside the write itself, on the history's
 * notification, when the step is no longer pending and its outcome is not known yet.
 */
class PresentationAwaitWhileApplyingTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");
    private static final List<TaskStage> PLAN = List.of(new TaskStage("Una etapa", List.of("Coloco una luz")));

    private final FakePresentingHost host = new FakePresentingHost();
    private final FakePresentingHost.Circuit circuit = host.open(DOCUMENT.value());
    private final AutomationApi api = new AutomationApi(host);

    @Test
    void anAwaitWhileTheStepIsWrittenWaitsForItInsteadOfBeingRefused() {
        host.present(DOCUMENT.value(), new PresentationSettings(true, PresentationPace.STEP_BY_STEP,
                Duration.ofSeconds(1)));
        TaskInfo task = api.beginTask(DOCUMENT, "Una luz", PLAN);
        PresentedEdit proposed = api.editStep(new EditRequest(DOCUMENT, host.onEditor(circuit::version), "op-1",
                Optional.of(new TaskStep(task.taskId(), "Una etapa", "Coloco una luz", "Coloco una luz")),
                List.of(new PlacePart(Optional.of("lamp"), "led", 0, 0, Rotation.R0, Map.of(), "")), false),
                        Optional.empty());
        assertThat(proposed.proposed()).isTrue();
        AtomicReference<StepOutcome> duringTheWrite = new AtomicReference<>();
        AtomicReference<RuntimeException> refusedDuringTheWrite = new AtomicReference<>();
        HistoryListener awaitWhileWriting = changed -> {
            if (duringTheWrite.get() == null && refusedDuringTheWrite.get() == null) {
                try {
                    duringTheWrite.set(api.awaitStep(task.taskId(), Duration.ZERO));
                } catch (RuntimeException refusal) {
                    refusedDuringTheWrite.set(refusal);
                }
            }
        };
        circuit.history().addListener(awaitWhileWriting);

        api.nextStep(task.taskId());

        circuit.history().removeListener(awaitWhileWriting);
        assertThat(refusedDuringTheWrite.get()).as("refused while the step was written").isNull();
        assertThat(duringTheWrite.get()).as("the step's outcome is not known yet while it is written")
                .isEqualTo(StepOutcome.of(StepStatus.STILL_WAITING));
        StepOutcome afterwards = api.awaitStep(task.taskId(), Duration.ZERO);
        assertThat(afterwards.status()).isEqualTo(StepStatus.APPLIED);
        assertThat(afterwards.receipt()).hasValueSatisfying(receipt -> assertThat(receipt.applied()).isTrue());
        assertThat(circuit.document().components()).hasSize(1);
    }
}
