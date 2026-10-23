package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;
import cl.almejo.vsim.automation.edit.AiTask;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditOperation.AddGuide;
import cl.almejo.vsim.automation.edit.EditOperation.Delete;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresentationServiceTest {

    private static final String DOCUMENT = "circuit-1";
    private static final DocumentId DOCUMENT_ID = new DocumentId(DOCUMENT);
    private static final Duration PAUSE = Duration.ofSeconds(1);
    private static final List<TaskStage> PLAN = List.of(
            new TaskStage("Compuertas", List.of("Coloco la primera", "Coloco la segunda", "Coloco la tercera")),
            new TaskStage("Salida", List.of("Coloco la salida", "Coloco otra salida")));

    private final FakePresentingHost host = new FakePresentingHost();
    private final List<AutomationListener> taskListeners = new CopyOnWriteArrayList<>();
    private final List<PresentationListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> heard = new CopyOnWriteArrayList<>();
    private final List<StepPreview> proposed = new CopyOnWriteArrayList<>();
    private final List<StepPreview> applied = new CopyOnWriteArrayList<>();
    private FakePresentingHost.Circuit circuit;
    private EditService edits;
    private PresentationService service;
    private TaskInfo task;
    private int nextOperationId = 1;

    @BeforeEach
    void openACircuitAndBeginATask() {
        circuit = host.open(DOCUMENT);
        edits = new EditService(host, taskListeners, Duration.ofSeconds(5), Runnable::run);
        service = new PresentationService(host, edits, listeners, Runnable::run, Duration.ofSeconds(5));
        listeners.add(new Recorder());
        task = edits.beginTask(DOCUMENT_ID, "Latch", PLAN);
        service.taskBegun(task);
    }

    // --- working mode stays as IA.1 (Global Constraints) ---

    @Test
    void inWorkingModeAStepIsAppliedAtOnceAndNotPaced() {
        PresentedEdit edit = service.edit(step(0, 0, place("gate", 0, 0)));

        assertThat(edit.status()).isEmpty();
        assertThat(edit.receipt().applied()).isTrue();
        assertThat(circuit.history().canUndo()).isTrue();
        assertThat(proposed).isEmpty();
    }

    // --- «Paso a paso»: nothing changes while proposed (Review Focus 1) ---

    @Test
    void aProposedStepChangesNeitherTheCircuitNorTheHistory() {
        stepByStep();
        long versionBefore = circuit.version().value();

        PresentedEdit edit = service.edit(step(0, 0, place("gate", 0, 0)));

        assertThat(edit.proposed()).isTrue();
        assertThat(edit.receipt().applied()).isFalse();
        assertThat(circuit.document().elements()).isEmpty();
        assertThat(circuit.history().canUndo()).isFalse();
        assertThat(circuit.version().value()).isEqualTo(versionBefore);
        assertThat(proposed).singleElement().satisfies(preview -> {
            assertThat(preview.taskId()).isEqualTo(task.taskId());
            assertThat(preview.stageIndex()).isZero();
            assertThat(preview.stepIndex()).isZero();
            assertThat(preview.text()).isEqualTo("Coloco la primera");
            assertThat(preview.reason()).contains("Para empezar");
            assertThat(preview.ghostElements()).hasSize(1);
            assertThat(preview.changedBounds()).isPresent();
        });
        assertThat(heard).contains("planChanged 0 0");
    }

    @Test
    void siguienteAppliesExactlyTheGhostThatWasShownAsOneUndoStep() {
        stepByStep();
        PresentedEdit edit = service.edit(step(0, 0, place("first", 0, 0), place("second", 10, 0)));
        List<DocumentElement> ghosts = proposed.getFirst().ghostElements();

        service.next(task.taskId());

        assertThat(circuit.document().elements()).containsExactlyInAnyOrderElementsOf(ghosts);
        StepOutcome outcome = service.awaitStep(task.taskId(), Duration.ZERO);
        assertThat(outcome.status()).isEqualTo(StepStatus.APPLIED);
        assertThat(outcome.receipt()).hasValueSatisfying(receipt -> {
            assertThat(receipt.applied()).isTrue();
            assertThat(receipt.aliases()).isEqualTo(edit.receipt().aliases());
        });
        assertThat(applied).singleElement().isEqualTo(proposed.getFirst());
        circuit.history().undo();
        assertThat(circuit.document().elements()).isEmpty();
        assertThat(circuit.history().canUndo()).isFalse();
    }

    @Test
    void aStepThatOnlyDeletesShowsTheRemovedIds() {
        PresentedEdit placed = service.edit(step(0, 0, place("gate", 0, 0)));
        String gateId = placed.receipt().aliases().get("gate");
        stepByStep();

        service.edit(step(0, 1, new Delete(List.of(gateId))));

        assertThat(proposed.getLast().removedIds()).containsExactly(new ElementId(gateId));
        assertThat(proposed.getLast().ghostElements()).isEmpty();
        assertThat(proposed.getLast().changedBounds()).isPresent();
        assertThat(circuit.document().elements()).hasSize(1);
    }

    @Test
    void aUserEditWhileTheGhostWaitsDiscardsItAsStale() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        userAddsALabel();

        assertThat(heard).contains("discarded STALE");
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STALE);
        service.next(task.taskId());
        assertThat(circuit.document().elements()).singleElement().isInstanceOf(TextLabel.class);
        assertThat(applied).isEmpty();
    }

    @Test
    void afterAStaleDiscardTheAiProposesAgainAgainstTheNewVersion() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        userAddsALabel();

        PresentedEdit again = service.edit(step(0, 0, place("gate", 0, 0)));
        service.next(task.taskId());

        assertThat(again.proposed()).isTrue();
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(circuit.document().elements()).hasSize(2);
    }

    @Test
    void oneStepWaitsAtATimeAndARetryOfItGetsTheSameAnswer() {
        stepByStep();
        EditRequest first = step(0, 0, place("gate", 0, 0));
        PresentedEdit proposal = service.edit(first);

        assertThat(service.edit(first)).isEqualTo(proposal);
        assertThatThrownBy(() -> service.edit(step(0, 1, place("other", 10, 0))))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
        assertThat(proposed).hasSize(1);
    }

    @Test
    void aLayoutOnlyGuideIsNeverAStep() {
        stepByStep();

        PresentedEdit edit = service.edit(step(0, 0, new AddGuide(Guide.Orientation.VERTICAL, 4, true)));

        assertThat(edit.status()).isEmpty();
        assertThat(edit.receipt().applied()).isTrue();
        assertThat(proposed).isEmpty();
    }

    /** Review M1: a layout-only guide would be a history change under the ghost, staling the AI's own step. */
    @Test
    void aLayoutOnlyGuideWhileAGhostWaitsIsRefusedAndTheGhostStays() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        assertThatThrownBy(() -> service.edit(step(0, 1, new AddGuide(Guide.Orientation.VERTICAL, 4, true))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
                    assertThat(refusal.getMessage()).contains("still waits for «Siguiente»");
                });
        assertThat(heard).doesNotContain("discarded STALE");
        service.next(task.taskId());
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(circuit.document().guides()).isEmpty();
    }

    // --- waits that always end (Review Focus 2) ---

    @Test
    void awaitStepAnswersStillWaitingAtTheLimitAndNeverWaitsLongerThanTheMaximum() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        PresentationService quick = new PresentationService(host, edits, listeners, Runnable::run,
                Duration.ofMillis(5));

        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STILL_WAITING);
        assertThat(PresentationService.MAXIMUM_STEP_WAIT).isEqualTo(Duration.ofSeconds(30));
        assertThat(quick.capped(Duration.ofHours(1))).isEqualTo(Duration.ofMillis(5));
        assertThat(circuit.document().elements()).isEmpty();
    }

    @Test
    void detenerEndsAWaitingAwaitWithStoppedAndRefusesTheNextEdit() throws Exception {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        CompletableFuture<StepOutcome> waiting = CompletableFuture.supplyAsync(
                () -> service.awaitStep(task.taskId(), Duration.ofSeconds(5)));

        service.stop(task.taskId());

        assertThat(waiting.get(5, TimeUnit.SECONDS).status()).isEqualTo(StepStatus.STOPPED);
        assertThat(heard).contains("discarded STOPPED");
        assertThat(circuit.document().elements()).isEmpty();
        assertThatThrownBy(() -> service.edit(step(0, 0, place("gate", 0, 0))))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
    }

    @Test
    void closingTheWindowTurningTheAiOffAndEndingTheTaskEachEndTheWait() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        service.documentClosed(DOCUMENT_ID);
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STOPPED);

        service.edit(step(0, 0, place("gate", 0, 0)));
        service.stopAll();
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STOPPED);

        service.edit(step(0, 0, place("gate", 0, 0)));
        service.taskEnding(task.taskId());
        edits.endTask(task.taskId());
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STOPPED);
        assertThat(circuit.document().elements()).isEmpty();
    }

    /**
     * Pulido T1: a step that reached VSim as the AI was turned off never starts a wait: the await answers STOPPED at
     * once (not STILL_WAITING after the wait) and its ghost goes, until the AI is on again.
     */
    @Test
    void afterTheAiWasTurnedOffAnAwaitAnswersStoppedAtOnceUntilItIsOnAgain() {
        stepByStep();
        service.stopAll();
        service.edit(step(0, 0, place("gate", 0, 0)));

        assertThat(service.awaitStep(task.taskId(), Duration.ofMillis(200)).status())
                .isEqualTo(StepStatus.STOPPED);
        assertThat(service.hasPendingStep(task.taskId())).as("the ghost went").isFalse();
        assertThat(heard).contains("discarded STOPPED");

        service.resume();
        service.edit(step(0, 0, place("gate", 0, 0)));
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STILL_WAITING);
    }

    @Test
    void theWaitsForSiguienteAreCountedWhileTheyWait() throws Exception {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        assertThat(service.stepWaits()).isZero();

        CompletableFuture<StepOutcome> waiting = CompletableFuture.supplyAsync(
                () -> service.awaitStep(task.taskId(), Duration.ofSeconds(5)));
        awaitStepWaits(1);
        service.next(task.taskId());

        assertThat(waiting.get(5, TimeUnit.SECONDS).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(service.stepWaits()).isZero();
    }

    private void awaitStepWaits(int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (service.stepWaits() != count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("never " + count + " waits for «Siguiente»");
            }
            Thread.onSpinWait();
        }
    }

    @Test
    void anAwaitWithNoStepProposedIsRefused() {
        assertThatThrownBy(() -> service.awaitStep(task.taskId(), Duration.ZERO))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
        assertThatThrownBy(() -> service.awaitStep("task-404", Duration.ZERO))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    // --- «De corrido»: paced by the injectable pacer ---

    @Test
    void deCorridoAppliesEachStepAfterThePauseSinceTheLastOne() throws Exception {
        continuous();
        PresentedEdit first = service.edit(step(0, 0, place("first", 0, 0)));
        assertThat(first.status()).contains(StepStatus.APPLIED);

        CompletableFuture<PresentedEdit> second = CompletableFuture.supplyAsync(
                () -> service.edit(step(0, 1, place("second", 10, 0))));
        assertThat(host.pacer().nextRequestedDelay()).isEqualTo(PAUSE);
        assertThat(second).isNotDone();
        assertThat(host.onEditor(() -> circuit.document().elements())).hasSize(1);
        assertThat(heard).contains("paced " + PAUSE.toMillis()); // the panel says «Siguiente paso en …»

        host.pacer().advance(PAUSE);

        assertThat(second.get(5, TimeUnit.SECONDS).status()).contains(StepStatus.APPLIED);
        assertThat(circuit.document().elements()).hasSize(2);
        assertThat(applied).hasSize(2);
        assertThat(proposed).isEmpty();
    }

    @Test
    void deCorridoDoesNotWaitAgainWhenThePauseHasPassedAlready() {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        host.pacer().advance(Duration.ofSeconds(3));

        assertThat(service.edit(step(0, 1, place("second", 10, 0))).status()).contains(StepStatus.APPLIED);
    }

    @Test
    void detenerDuringThePauseRefusesTheStepAndChangesNothing() throws Exception {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        CompletableFuture<PresentedEdit> second = CompletableFuture.supplyAsync(
                () -> service.edit(step(0, 1, place("second", 10, 0))));
        host.pacer().nextRequestedDelay();

        service.stop(task.taskId());

        assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AutomationException.class);
        assertThat(circuit.document().elements()).hasSize(1);
    }

    /** Review M2: «Siguiente paso en …» goes away when the paced step is refused (the user edited meanwhile). */
    @Test
    void aPacedStepRefusedBecauseTheUserEditedIsAnnouncedAsStale() throws Exception {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        CompletableFuture<PresentedEdit> second = CompletableFuture.supplyAsync(
                () -> service.edit(step(0, 1, place("second", 10, 0))));
        host.pacer().nextRequestedDelay();
        userAddsALabel();

        host.pacer().advance(PAUSE);

        assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AutomationException.class);
        assertThat(heard).contains("discarded STALE");
    }

    // --- mode changes mid-task (Review Focus 5) ---

    @Test
    void switchingToDeCorridoAppliesTheWaitingGhost() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        continuous();
        service.settingsChanged(DOCUMENT_ID, host.presentationSettings(DOCUMENT_ID));

        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(circuit.document().elements()).hasSize(1);
        assertThat(service.edit(step(0, 1, place("next", 10, 0))).status()).isNotEqualTo(Optional.of(
                StepStatus.PROPOSED));
    }

    @Test
    void leavingPresentarAppliesTheWaitingGhostAndTheNextStepsAreNotPaced() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        host.present(DOCUMENT, PresentationSettings.WORKING);
        service.settingsChanged(DOCUMENT_ID, PresentationSettings.WORKING);

        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(service.edit(step(0, 1, place("next", 10, 0))).status()).isEmpty();
        assertThat(circuit.document().elements()).hasSize(2);
    }

    @Test
    void switchingToPasoAPasoProposesFromTheNextStep() {
        continuous();
        assertThat(service.edit(step(0, 0, place("first", 0, 0))).status()).contains(StepStatus.APPLIED);

        stepByStep();

        assertThat(service.edit(step(0, 1, place("second", 10, 0))).proposed()).isTrue();
    }

    // --- stage ends ---

    @Test
    void hastaFinDeEtapaPacesTheRestOfTheStageAndTheNextStageWaitsAgain() {
        stepByStep();
        service.edit(step(0, 0, place("first", 0, 0)));

        service.toStageEnd(task.taskId());
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        host.pacer().advance(PAUSE);
        assertThat(service.edit(step(0, 1, place("second", 10, 0))).status()).contains(StepStatus.APPLIED);
        host.pacer().advance(PAUSE);
        assertThat(service.edit(step(0, 2, place("third", 20, 0))).status()).contains(StepStatus.APPLIED);

        assertThat(service.edit(step(1, 0, place("output", 30, 0))).proposed()).isTrue();
    }

    @Test
    void deCorridoStopsAtAStageEndOnlyWhenItsCheckFailed() {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        host.pacer().advance(PAUSE);
        service.stageChecked(task.taskId(), Optional.of("Compuertas"), false, "the truth table differs");

        assertThat(service.edit(step(1, 0, place("output", 30, 0))).proposed()).isTrue();
        assertThat(heard).contains("checked 0 false the truth table differs");
        service.next(task.taskId());
        host.pacer().advance(PAUSE);
        assertThat(service.edit(step(1, 1, place("other", 40, 0))).status()).contains(StepStatus.APPLIED);
    }

    @Test
    void deCorridoGoesOnAfterAPassedCheck() {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        host.pacer().advance(PAUSE);
        service.stageChecked(task.taskId(), Optional.of("Compuertas"), true, "ok");

        assertThat(service.edit(step(1, 0, place("output", 30, 0))).status()).contains(StepStatus.APPLIED);
    }

    /** IA.3 Speed: a layout-only fault is reported, but «De corrido» goes on to the next stage. */
    @Test
    void aLayoutOnlyFailureLetsDeCorridoGoOn() {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        host.pacer().advance(PAUSE);

        boolean stops = service.stageChecked(task.taskId(), Optional.of("Compuertas"), false, false,
                "1 layout faults");

        assertThat(stops).isFalse();
        assertThat(heard).contains("checked 0 false 1 layout faults");
        assertThat(service.edit(step(1, 0, place("output", 30, 0))).status()).contains(StepStatus.APPLIED);
    }

    @Test
    void aFunctionalFailureStopsDeCorridoAndAFunctionalPassLetsItGoAgain() {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        host.pacer().advance(PAUSE);

        assertThat(service.stageChecked(task.taskId(), Optional.of("Compuertas"), false, true, "table")).isTrue();
        assertThat(service.stageChecked(task.taskId(), Optional.of("Compuertas"), false, false, "layout only"))
                .as("fixed: only the layout is left").isFalse();

        assertThat(service.edit(step(1, 0, place("output", 30, 0))).status()).contains(StepStatus.APPLIED);
    }

    // --- a gone client (IA.3, Review Focus 4) ---

    @Test
    void aGhostTheAiStopsAskingAboutIsStoppedAfterTwoMinutes() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        taskListeners.add(new AutomationListener() {
            @Override
            public void taskStarted(TaskInfo started) {
            }

            @Override
            public void taskEnded(TaskInfo ended, java.util.Set<String> changedElementIds) {
            }

            @Override
            public void taskStopped(TaskInfo stoppedTask) {
                stopped.add("plain");
            }

            @Override
            public void taskStopped(TaskInfo stoppedTask, TaskStopReason reason) {
                stopped.add(reason.name());
            }
        });
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        host.pacer().advance(PresentationService.SILENCE_LIMIT.minusSeconds(1));
        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.RUNNING);
        host.pacer().advance(Duration.ofSeconds(1));

        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.STOPPED);
        assertThat(stopped).containsExactly(TaskStopReason.AI_SILENT.name());
        assertThat(heard).contains("discarded STOPPED");
        assertThat(service.hasPendingStep(task.taskId())).isFalse();
        assertThat(circuit.document().elements()).isEmpty();
    }

    @Test
    void anAiThatKeepsPollingIsNeverStoppedHoweverLongTheUserTakes() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        for (int poll = 0; poll < 20; poll++) {
            host.pacer().advance(PresentationService.MAXIMUM_STEP_WAIT);
            assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.STILL_WAITING);
        }

        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.RUNNING);
        service.next(task.taskId());
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
    }

    @Test
    void aStepAppliedInTimeIsNeverStoppedLater() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));
        service.next(task.taskId());

        host.pacer().advance(PresentationService.SILENCE_LIMIT.multipliedBy(2));

        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.RUNNING);
    }

    // --- saving keeps a waiting step (IA.3) ---

    @Test
    void savingWhileAGhostWaitsKeepsItAndSiguienteAppliesIt() {
        stepByStep();
        service.edit(step(0, 0, place("gate", 0, 0)));

        host.onEditor(() -> {
            circuit.history().markSaved();
            return null;
        });

        assertThat(heard).doesNotContain("discarded STALE");
        service.next(task.taskId());
        assertThat(service.awaitStep(task.taskId(), Duration.ZERO).status()).isEqualTo(StepStatus.APPLIED);
        assertThat(circuit.document().elements()).hasSize(1);
    }

    @Test
    void thePlanFollowsTheStepsByStageTitleAndStepText() {
        stepByStep();
        service.edit(step(1, 1, place("gate", 0, 0)));

        assertThat(heard).contains("planChanged 1 1");
        service.next(task.taskId());
        assertThat(heard).contains("planChanged 1 2");
        assertThat(applied.getFirst().stageIndex()).isEqualTo(1);
        assertThat(applied.getFirst().stepIndex()).isEqualTo(1);
    }

    // --- the AI's narration (IA.3 Task 7) ---

    @Test
    void inWorkingModeANarrationIsHeardAtOnceAndNotPresented() {
        Narration narration = service.say(task.taskId(), "  Ahora copio\nel circuito  ");

        assertThat(narration.text()).isEqualTo("Ahora copio el circuito");
        assertThat(narration.presenting()).isFalse();
        assertThat(heard).contains("narrated 0 Ahora copio el circuito false");
        assertThat(host.pacer().requestedDelays()).isEmpty();
    }

    @Test
    void inPasoAPasoANarrationShowsAtOnceInTheCurrentStage() {
        stepByStep();
        service.stageBegun(task.taskId(), 1);

        service.say(task.taskId(), "Ahora la salida");

        assertThat(heard).contains("narrated 1 Ahora la salida true");
        assertThat(host.pacer().requestedDelays()).isEmpty();
    }

    @Test
    void inDeCorridoANarrationIsPacedLikeAShortStep() throws Exception {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));

        CompletableFuture<Narration> said = CompletableFuture.supplyAsync(
                () -> service.say(task.taskId(), "Ahora la segunda"));
        assertThat(host.pacer().nextRequestedDelay()).isEqualTo(PAUSE);
        assertThat(said).isNotDone();
        assertThat(heard).noneMatch(event -> event.startsWith("narrated"));
        host.pacer().advance(PAUSE);
        assertThat(said.get(5, TimeUnit.SECONDS).text()).isEqualTo("Ahora la segunda");
        assertThat(heard).contains("narrated 0 Ahora la segunda true");

        // the next step waits the pause after the line, so a recording can read it
        CompletableFuture<PresentedEdit> second = CompletableFuture.supplyAsync(
                () -> service.edit(step(0, 1, place("second", 10, 0))));
        assertThat(host.pacer().nextRequestedDelay()).isEqualTo(PAUSE);
        host.pacer().advance(PAUSE);
        assertThat(second.get(5, TimeUnit.SECONDS).status()).contains(StepStatus.APPLIED);
    }

    @Test
    void detenerDuringANarrationsPauseRefusesIt() throws Exception {
        continuous();
        service.edit(step(0, 0, place("first", 0, 0)));
        CompletableFuture<Narration> said = CompletableFuture.supplyAsync(
                () -> service.say(task.taskId(), "Ahora la segunda"));
        host.pacer().nextRequestedDelay();

        service.stop(task.taskId());

        assertThatThrownBy(() -> said.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(AutomationException.class);
        assertThat(heard).noneMatch(event -> event.startsWith("narrated"));
    }

    @Test
    void theSameLineSaidAgainBeforeAStepIsShownOnce() {
        service.say(task.taskId(), "Ahora la salida");
        service.say(task.taskId(), "Ahora la salida");
        assertThat(heard.stream().filter(event -> event.startsWith("narrated"))).hasSize(1);

        service.edit(step(0, 0, place("gate", 0, 0)));
        service.say(task.taskId(), "Ahora la salida");
        assertThat(heard.stream().filter(event -> event.startsWith("narrated"))).hasSize(2);
    }

    // --- a narration given with the edit shows only for an edit VSim accepted (IA.3 review I3) ---

    @Test
    void aRefusedEditSaysNothingInAnyMode() {
        for (Runnable mode : List.<Runnable>of(() -> { }, this::stepByStep, this::continuous)) {
            mode.run();
            EditRequest refused = step(0, 0, new Delete(List.of("c404")));

            assertThatThrownBy(() -> service.edit(refused, Optional.of("Ahora borro la compuerta")))
                    .isInstanceOf(AutomationException.class);
        }
        assertThat(heard).noneMatch(event -> event.startsWith("narrated"));
    }

    @Test
    void aStaleEditSaysNothing() {
        service.edit(step(0, 0, place("first", 0, 0)));
        EditRequest current = step(0, 1, place("second", 10, 0));
        EditRequest stale = new EditRequest(current.document(), new cl.almejo.vsim.automation.DocumentVersion(0),
                current.operationId(), current.step(), current.operations(), false);

        assertThatThrownBy(() -> service.edit(stale, Optional.of("Ahora la segunda")))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.STALE_DOCUMENT));
        assertThat(heard).noneMatch(event -> event.startsWith("narrated"));
    }

    @Test
    void anAcceptedEditsNarrationIsShownWithItsGhostOrBeforeItsStep() {
        service.edit(step(0, 0, place("first", 0, 0)), Optional.of("Primero una compuerta"));
        assertThat(heard).contains("narrated 0 Primero una compuerta false");

        stepByStep();
        PresentedEdit proposedEdit = service.edit(step(0, 1, place("second", 10, 0)),
                Optional.of("Ahora la segunda"));
        assertThat(proposedEdit.proposed()).isTrue();
        assertThat(heard).contains("narrated 0 Ahora la segunda true");
    }

    @Test
    void inDeCorridoAnEditsNarrationComesFirstThenThePauseThenTheStep() throws Exception {
        continuous();

        CompletableFuture<PresentedEdit> first = CompletableFuture.supplyAsync(
                () -> service.edit(step(0, 0, place("first", 0, 0)), Optional.of("Primero una compuerta")));
        assertThat(host.pacer().nextRequestedDelay()).isEqualTo(PAUSE);
        assertThat(heard).contains("narrated 0 Primero una compuerta true").noneMatch(
                event -> event.startsWith("applied"));
        host.pacer().advance(PAUSE);
        assertThat(first.get(5, TimeUnit.SECONDS).status()).contains(StepStatus.APPLIED);
        assertThat(heard.indexOf("narrated 0 Primero una compuerta true"))
                .isLessThan(heard.indexOf("applied Coloco la primera"));
    }

    @Test
    void aRetriedEditDoesNotSayItsLineAgain() {
        EditRequest request = step(0, 0, place("first", 0, 0));
        service.edit(request, Optional.of("Primero una compuerta"));

        service.edit(request, Optional.of("Primero una compuerta"));

        assertThat(heard.stream().filter(event -> event.startsWith("narrated"))).hasSize(1);
    }

    @Test
    void anEditWithALineVsimRefusesChangesNothing() {
        assertThatThrownBy(() -> service.edit(step(0, 0, place("first", 0, 0)), Optional.of("<html>hola")))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThat(circuit.document().elements()).isEmpty();
    }

    @Test
    void anEmptyALongAnHtmlLineAndAStoppedTaskAreRefused() {
        assertThatThrownBy(() -> service.say(task.taskId(), "   "))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThatThrownBy(() -> service.say(task.taskId(),
                "x".repeat(PresentationService.MAXIMUM_NARRATION_LENGTH + 1)))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThatThrownBy(() -> service.say(task.taskId(), "<html><img src=x>"))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThatThrownBy(() -> service.say("task-404", "Hola"))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
        service.stop(task.taskId());
        assertThatThrownBy(() -> service.say(task.taskId(), "Hola"))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
        assertThat(heard).noneMatch(event -> event.startsWith("narrated"));
    }

    // --- helpers ---

    private void stepByStep() {
        host.present(DOCUMENT, new PresentationSettings(true, PresentationPace.STEP_BY_STEP, PAUSE));
    }

    private void continuous() {
        host.present(DOCUMENT, new PresentationSettings(true, PresentationPace.CONTINUOUS, PAUSE));
    }

    private void userAddsALabel() {
        host.onEditor(() -> {
            circuit.history().execute(new AddElementsCommand(List.of(new TextLabel(
                    circuit.document().newLabelId(), new GridPoint(50, 50), "mío"))));
            return null;
        });
    }

    private EditRequest step(int stageIndex, int stepIndex, EditOperation... operations) {
        TaskStage stage = PLAN.get(stageIndex);
        String text = stage.steps().get(stepIndex);
        return new EditRequest(DOCUMENT_ID, host.onEditor(circuit::version), "op-" + nextOperationId++,
                Optional.of(new TaskStep(task.taskId(), stage.title(), text, text, Optional.of("Para empezar"))),
                List.of(operations), false);
    }

    private static PlacePart place(String alias, int x, int y) {
        return new PlacePart(Optional.of(alias), "nand2", x, y, Rotation.R0, Map.of(), "");
    }

    private final class Recorder implements PresentationListener {

        @Override
        public void planChanged(TaskInfo changed, List<TaskStage> stages, int currentStage, int currentStep) {
            heard.add("planChanged " + currentStage + " " + currentStep);
        }

        @Override
        public void stepProposed(StepPreview preview) {
            proposed.add(preview);
        }

        @Override
        public void stepApplied(StepPreview preview) {
            applied.add(preview);
            heard.add("applied " + preview.text());
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            heard.add("discarded " + why);
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            heard.add("checked " + stageIndex + " " + passed + " " + summary);
        }

        @Override
        public void stepPaced(String taskId, Duration delay) {
            heard.add("paced " + delay.toMillis());
        }

        @Override
        public void narrated(Narration narration) {
            heard.add("narrated " + narration.stageIndex() + " " + narration.text() + " " + narration.presenting());
        }
    }
}
