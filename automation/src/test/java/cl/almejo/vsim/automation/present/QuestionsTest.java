package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.AiTask;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
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

/** The AI's questions in VSim (IA.3 Task 8): one open per task, answered, withdrawn, and the pacing meanwhile. */
class QuestionsTest {

    private static final String DOCUMENT = "circuit-1";
    private static final DocumentId DOCUMENT_ID = new DocumentId(DOCUMENT);
    private static final Duration PAUSE = Duration.ofSeconds(1);
    private static final List<TaskStage> PLAN = List.of(
            new TaskStage("Contador", List.of("Coloco los flip-flops", "Conecto el reloj")));

    private final FakePresentingHost host = new FakePresentingHost();
    private final List<AutomationListener> taskListeners = new CopyOnWriteArrayList<>();
    private final List<PresentationListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> heard = new CopyOnWriteArrayList<>();
    private FakePresentingHost.Circuit circuit;
    private EditService edits;
    private Questions questions;
    private PresentationService service;
    private TaskInfo task;
    private int nextOperationId = 1;

    @BeforeEach
    void openACircuitAndBeginATask() {
        circuit = host.open(DOCUMENT);
        edits = new EditService(host, taskListeners, Duration.ofSeconds(5), Runnable::run);
        questions = new Questions(edits, host.stepPacer(), listeners, Runnable::run, Duration.ofSeconds(5));
        service = new PresentationService(host, edits, listeners, Runnable::run, Duration.ofSeconds(5),
                PresentationService.StageBoundary.NONE, questions::hasOpenQuestion);
        listeners.add(new Recorder());
        task = edits.beginTask(DOCUMENT_ID, "Contador", PLAN);
        service.taskBegun(task);
    }

    @Test
    void aQuestionWithChoicesIsShownAndAnsweredByAButton() {
        Question question = questions.ask(task.taskId(), "¿Cuántos bits?", List.of("2", "3", "4"), true);

        assertThat(question.questionId()).isNotBlank();
        assertThat(question.document()).isEqualTo(DOCUMENT_ID);
        assertThat(heard).contains("asked ¿Cuántos bits? [2, 3, 4] true");
        assertThat(questions.await(question.questionId(), Duration.ZERO).status())
                .isEqualTo(AnswerStatus.STILL_WAITING);

        assertThat(questions.answer(question.questionId(), QuestionAnswer.choice("3"))).isTrue();

        AnswerOutcome outcome = questions.await(question.questionId(), Duration.ZERO);
        assertThat(outcome.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(outcome.answer()).hasValueSatisfying(answer -> {
            assertThat(answer.choice()).contains("3");
            assertThat(answer.text()).isEmpty();
        });
        assertThat(heard).contains("closed " + question.questionId() + " true");
        assertThat(questions.hasOpenQuestion(task.taskId())).isFalse();
    }

    @Test
    void aQuestionIsAnsweredByTextAndALateAwaitGetsTheSameAnswer() throws Exception {
        Question question = questions.ask(task.taskId(), "¿Qué nombre le pongo?", List.of(), true);
        CompletableFuture<AnswerOutcome> waiting = CompletableFuture.supplyAsync(
                () -> questions.await(question.questionId(), Duration.ofSeconds(5)));

        questions.answer(question.questionId(), QuestionAnswer.text("  Contador módulo 6  "));

        assertThat(waiting.get(5, TimeUnit.SECONDS).answer().orElseThrow().text()).contains("Contador módulo 6");
        assertThat(questions.await(question.questionId(), Duration.ZERO).status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(questions.answer(question.questionId(), QuestionAnswer.text("otra"))).isFalse();
    }

    @Test
    void anAnswerTheQuestionDoesNotTakeIsRefused() {
        Question choicesOnly = questions.ask(task.taskId(), "¿Sigo?", List.of("Sí", "No"), false);

        assertThat(questions.answer(choicesOnly.questionId(), QuestionAnswer.text("quizás"))).isFalse();
        assertThat(questions.answer(choicesOnly.questionId(), QuestionAnswer.choice("Tal vez"))).isFalse();
        assertThat(questions.answer(choicesOnly.questionId(), QuestionAnswer.text("   "))).isFalse();
        assertThat(questions.hasOpenQuestion(task.taskId())).isTrue();
    }

    @Test
    void stillWaitingComesAtThePollLimit() {
        Questions shortPolls = new Questions(edits, host.stepPacer(), listeners, Runnable::run,
                Duration.ofMillis(20));
        Question question = shortPolls.ask(task.taskId(), "¿Cuántos bits?", List.of(), true);

        assertThat(shortPolls.await(question.questionId(), Duration.ofMinutes(5)).status())
                .isEqualTo(AnswerStatus.STILL_WAITING);
        assertThat(Questions.MAXIMUM_ANSWER_WAIT).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void theAiWithdrawsAQuestionAnsweredInTheChat() {
        Question question = questions.ask(task.taskId(), "¿Cuántos bits?", List.of(), true);

        questions.withdraw(question.questionId());
        questions.withdraw(question.questionId()); // twice is fine

        assertThat(questions.await(question.questionId(), Duration.ZERO).status()).isEqualTo(AnswerStatus.WITHDRAWN);
        assertThat(heard).contains("closed " + question.questionId() + " false");
        assertThat(questions.ask(task.taskId(), "¿Y el reloj?", List.of(), true)).isNotNull();
    }

    @Test
    void stoppingTheTaskClosingTheWindowAndTurningTheAiOffWithdrawTheQuestion() {
        Question first = questions.ask(task.taskId(), "¿Uno?", List.of(), true);
        questions.withdrawTask(task.taskId());
        assertThat(questions.await(first.questionId(), Duration.ZERO).status()).isEqualTo(AnswerStatus.WITHDRAWN);

        Question second = questions.ask(task.taskId(), "¿Dos?", List.of(), true);
        questions.withdrawDocument(DOCUMENT_ID);
        assertThat(questions.await(second.questionId(), Duration.ZERO).status()).isEqualTo(AnswerStatus.WITHDRAWN);

        Question third = questions.ask(task.taskId(), "¿Tres?", List.of(), true);
        questions.withdrawAll();
        assertThat(questions.await(third.questionId(), Duration.ZERO).status()).isEqualTo(AnswerStatus.WITHDRAWN);
    }

    @Test
    void oneQuestionAtATimePerTaskAndOnlyForARunningTask() {
        Question question = questions.ask(task.taskId(), "¿Cuántos bits?", List.of(), true);

        assertThatThrownBy(() -> questions.ask(task.taskId(), "¿Y otra?", List.of(), true))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
                    assertThat(refusal.recovery()).containsEntry("questionId", question.questionId());
                });
        edits.stopTask(task.taskId());
        questions.withdrawTask(task.taskId());
        assertThatThrownBy(() -> questions.ask(task.taskId(), "¿Sigo?", List.of(), true))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
    }

    @Test
    void aBadQuestionIsRefused() {
        assertRefused(() -> questions.ask(task.taskId(), "  ", List.of(), true), AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask(task.taskId(), "x".repeat(Questions.MAXIMUM_QUESTION_LENGTH + 1),
                List.of(), true), AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask(task.taskId(), "<html><img src=x>", List.of(), true),
                AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask(task.taskId(), "¿Cuál?", List.of("a", "b", "c", "d", "e"), true),
                AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask(task.taskId(), "¿Cuál?", List.of(), false),
                AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask(task.taskId(), "¿Cuál?", List.of("a", "a"), true),
                AutomationError.INVALID_PARAMETER);
        assertRefused(() -> questions.ask("task-404", "¿Cuál?", List.of(), true), AutomationError.INVALID_REFERENCE);
        assertRefused(() -> questions.await("question-404", Duration.ZERO), AutomationError.INVALID_REFERENCE);
        assertThat(heard).noneMatch(event -> event.startsWith("asked"));
    }

    // --- the pacing and the heartbeat meanwhile ---

    @Test
    void deCorridoProposesTheNextStepWhileAQuestionWaits() {
        host.present(DOCUMENT, new PresentationSettings(true, PresentationPace.CONTINUOUS, PAUSE));
        questions.ask(task.taskId(), "¿Cuántos bits?", List.of(), true);

        PresentedEdit edit = service.edit(step(place("gate", 0, 0)));

        assertThat(edit.status()).contains(StepStatus.PROPOSED);
    }

    @Test
    void aQuestionWaitingForTheUserIsNotTheAiGoingSilent() {
        host.present(DOCUMENT, new PresentationSettings(true, PresentationPace.STEP_BY_STEP, PAUSE));
        service.edit(step(place("gate", 0, 0)));
        Question question = questions.ask(task.taskId(), "¿Cuántos bits?", List.of(), true);

        host.pacer().advance(PresentationService.SILENCE_LIMIT.multipliedBy(3));
        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.RUNNING);

        // once answered, the silence counts again from then
        questions.answer(question.questionId(), QuestionAnswer.text("4"));
        host.pacer().advance(PresentationService.SILENCE_LIMIT.minusSeconds(1));
        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.RUNNING);
        host.pacer().advance(PresentationService.SILENCE_LIMIT);
        assertThat(edits.requireTask(task.taskId()).state()).isEqualTo(AiTask.State.STOPPED);
    }

    private static void assertRefused(Runnable call, AutomationError error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(error));
    }

    private EditRequest step(EditOperation... operations) {
        String text = PLAN.getFirst().steps().getFirst();
        return new EditRequest(DOCUMENT_ID, host.onEditor(circuit::version),
                "op-" + nextOperationId++, Optional.of(new TaskStep(task.taskId(), PLAN.getFirst().title(), text,
                text, Optional.empty())), List.of(operations), false);
    }

    private static PlacePart place(String alias, int x, int y) {
        return new PlacePart(Optional.of(alias), "nand2", x, y, Rotation.R0, Map.of(), "");
    }

    private final class Recorder implements PresentationListener {

        @Override
        public void planChanged(TaskInfo changed, List<TaskStage> stages, int currentStage, int currentStep) {
        }

        @Override
        public void stepProposed(StepPreview preview) {
        }

        @Override
        public void stepApplied(StepPreview preview) {
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
        }

        @Override
        public void questionAsked(Question question) {
            heard.add("asked " + question.text() + " " + question.choices() + " " + question.allowText());
        }

        @Override
        public void questionClosed(String taskId, String questionId, boolean answered) {
            heard.add("closed " + questionId + " " + answered);
        }
    }
}
