package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlainText;
import cl.almejo.vsim.automation.edit.AiTask;
import cl.almejo.vsim.automation.edit.EditService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The AI's questions to the user in VSim (IA.3, {@code vsim_ask_user}), like the preference proposals: at most one
 * open question per task, shown in the «Presentar» panel or the status strip until the user answers it there or it is
 * withdrawn (by the AI, when the user answered in the chat; or because the task stopped or ended, its window closed or
 * the AI was turned off). {@link #await} long-polls at most {@link #MAXIMUM_ANSWER_WAIT}. While a question waits for
 * the user, «De corrido» proposes the task's next step instead of applying it, and the time does not count as the AI's
 * silence (see {@link PresentationService}). Listeners hear each question asked and closed through {@code events}.
 */
public final class Questions {

    /** The longest {@code await_answer} waits in one call: well under the client's tool timeout. */
    public static final Duration MAXIMUM_ANSWER_WAIT = Duration.ofSeconds(30);
    public static final int MAXIMUM_QUESTION_LENGTH = 300;
    public static final int MAXIMUM_CHOICES = 4;
    public static final int MAXIMUM_CHOICE_LENGTH = 40;
    /** A written answer longer than this is cut: the field is one line. */
    public static final int MAXIMUM_ANSWER_LENGTH = 500;
    /** Questions closed already kept for a late {@code await_answer}. */
    static final int MAXIMUM_REMEMBERED_QUESTIONS = 100;

    private final EditService edits;
    private final StepPacer pacer;
    private final List<PresentationListener> listeners;
    private final Executor events;
    private final Duration maximumWait;
    /** Guarded by {@code this}: every question by id, in the order asked. */
    private final Map<String, Entry> questions = new LinkedHashMap<>();
    private int nextQuestion = 1;

    /**
     * @param listeners   the live list of listeners (the API's presentation listeners)
     * @param events      where listeners are called, in order
     * @param maximumWait the longest {@link #await} waits (at most {@link #MAXIMUM_ANSWER_WAIT}; tests make it short)
     */
    public Questions(EditService edits, StepPacer pacer, List<PresentationListener> listeners, Executor events,
                     Duration maximumWait) {
        this.edits = Objects.requireNonNull(edits, "edits");
        this.pacer = Objects.requireNonNull(pacer, "pacer");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        this.events = Objects.requireNonNull(events, "events");
        Objects.requireNonNull(maximumWait, "maximumWait");
        this.maximumWait = maximumWait.compareTo(MAXIMUM_ANSWER_WAIT) > 0 ? MAXIMUM_ANSWER_WAIT : maximumWait;
    }

    /**
     * {@code vsim_ask_user}: shows {@code text} to the user with a button per choice and, when {@code allowText}, a
     * field to write the answer. {@code INVALID_REFERENCE} for an unknown task, {@code NOT_ALLOWED} when the task does
     * not run or a question of it still waits, {@code INVALID_PARAMETER} for a bad question.
     */
    public Question ask(String taskId, String text, List<String> choices, boolean allowText) {
        Objects.requireNonNull(choices, "choices");
        String question = line(text, "question", MAXIMUM_QUESTION_LENGTH);
        List<String> labels = choicesOf(choices);
        if (labels.isEmpty() && !allowText) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A question without choices needs "
                    + "allowText true: the user must have a way to answer", Map.of("allowText", false));
        }
        AiTask task = edits.requireTask(taskId);
        task.heard(pacer.now());
        EditService.requireRunning(task);
        Question asked;
        synchronized (this) {
            Optional<Entry> open = openEntry(taskId);
            if (open.isPresent()) {
                String waiting = open.get().question.questionId();
                throw new AutomationException(AutomationError.NOT_ALLOWED, "The question " + waiting + " of task "
                        + taskId + " still waits for the user: call vsim_await_answer, or vsim_withdraw_question "
                        + "if the user answered in the chat, before asking another", Map.of("questionId", waiting));
            }
            asked = new Question("question-" + nextQuestion++, taskId, task.info().document(), question, labels,
                    allowText);
            questions.put(asked.questionId(), new Entry(asked));
            forgetOldOnes();
        }
        announce(listener -> listener.questionAsked(asked));
        return asked;
    }

    /**
     * {@code vsim_await_answer}: the answer to {@code questionId}, waiting for it at most {@code wait} (capped):
     * {@code ANSWERED} with the answer, {@code WITHDRAWN}, or {@code STILL_WAITING} (ask again).
     * {@code INVALID_REFERENCE} for an unknown question.
     */
    public AnswerOutcome await(String questionId, Duration wait) {
        Objects.requireNonNull(wait, "wait");
        Entry entry = requireEntry(questionId);
        heardFrom(entry.question.taskId());
        Duration capped = wait.isNegative() ? Duration.ZERO : wait.compareTo(maximumWait) > 0 ? maximumWait : wait;
        try {
            return entry.outcome.get(capped.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            return AnswerOutcome.of(AnswerStatus.STILL_WAITING);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AnswerOutcome.of(AnswerStatus.STILL_WAITING);
        } catch (ExecutionException exception) {
            return AnswerOutcome.of(AnswerStatus.WITHDRAWN);
        }
    }

    /**
     * The user answered in VSim: true when {@code answer} closed the question; false (nothing changes) when it is
     * closed already or the answer is not one it takes (a choice it does not offer, a text it does not allow, an
     * empty text). A written answer is one line, cut at {@link #MAXIMUM_ANSWER_LENGTH}.
     */
    public boolean answer(String questionId, QuestionAnswer answer) {
        Objects.requireNonNull(answer, "answer");
        Entry entry;
        QuestionAnswer taken;
        synchronized (this) {
            entry = questions.get(questionId);
            if (entry == null || entry.outcome.isDone()) {
                return false;
            }
            Optional<QuestionAnswer> valid = validated(entry.question, answer);
            if (valid.isEmpty()) {
                return false;
            }
            taken = valid.get();
            entry.outcome.complete(AnswerOutcome.answered(taken));
        }
        closed(entry, true);
        return true;
    }

    /** {@code vsim_withdraw_question}: the user answered elsewhere; nothing when it is closed already. */
    public void withdraw(String questionId) {
        Entry entry = requireEntry(questionId);
        heardFrom(entry.question.taskId());
        withdraw(entry);
    }

    /** The task stopped or ended: its open question is withdrawn. */
    public void withdrawTask(String taskId) {
        withdrawAll(entry -> entry.question.taskId().equals(taskId));
    }

    /** The circuit's last window closed: the questions of its tasks are withdrawn. */
    public void withdrawDocument(DocumentId document) {
        Objects.requireNonNull(document, "document");
        withdrawAll(entry -> entry.question.document().equals(document));
    }

    /** The AI was turned off or VSim quits: every open question is withdrawn. */
    public void withdrawAll() {
        withdrawAll(entry -> true);
    }

    /** True while a question of the task waits for the user. */
    public synchronized boolean hasOpenQuestion(String taskId) {
        return openEntry(taskId).isPresent();
    }

    /** The question of the task that waits for the user, if any. */
    public synchronized Optional<Question> openQuestion(String taskId) {
        return openEntry(taskId).map(entry -> entry.question);
    }

    private void withdrawAll(Predicate<Entry> which) {
        List<Entry> open;
        synchronized (this) {
            open = questions.values().stream().filter(entry -> !entry.outcome.isDone()).filter(which).toList();
        }
        open.forEach(this::withdraw);
    }

    private void withdraw(Entry entry) {
        if (entry.outcome.complete(AnswerOutcome.of(AnswerStatus.WITHDRAWN))) {
            closed(entry, false);
        }
    }

    /** The question closed: the AI's silence counts again from now, and listeners hear it. */
    private void closed(Entry entry, boolean answered) {
        heardFrom(entry.question.taskId());
        Question question = entry.question;
        announce(listener -> listener.questionClosed(question.taskId(), question.questionId(), answered));
    }

    private void heardFrom(String taskId) {
        edits.task(taskId).ifPresent(task -> task.heard(pacer.now()));
    }

    private Optional<Entry> openEntry(String taskId) {
        return questions.values().stream()
                .filter(entry -> entry.question.taskId().equals(taskId) && !entry.outcome.isDone())
                .findFirst();
    }

    private synchronized Entry requireEntry(String questionId) {
        Objects.requireNonNull(questionId, "questionId");
        Entry entry = questions.get(questionId);
        if (entry == null) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "There is no question " + questionId
                    + "; ask one with vsim_ask_user", Map.of("questionId", questionId));
        }
        return entry;
    }

    /** Called holding the lock: closed questions beyond the remembered ones are forgotten, oldest first. */
    private void forgetOldOnes() {
        List<String> closed = questions.values().stream().filter(entry -> entry.outcome.isDone())
                .map(entry -> entry.question.questionId()).toList();
        for (int index = 0; index < closed.size() && questions.size() > MAXIMUM_REMEMBERED_QUESTIONS; index++) {
            questions.remove(closed.get(index));
        }
    }

    private static Optional<QuestionAnswer> validated(Question question, QuestionAnswer answer) {
        if (answer.choice().isPresent()) {
            return question.choices().contains(answer.choice().get())
                    ? Optional.of(QuestionAnswer.choice(answer.choice().get())) : Optional.empty();
        }
        if (!question.allowText()) {
            return Optional.empty();
        }
        String written = answer.text().orElse("").strip().replaceAll("\\s+", " ");
        if (written.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QuestionAnswer.text(written.length() > MAXIMUM_ANSWER_LENGTH
                ? written.substring(0, MAXIMUM_ANSWER_LENGTH) : written));
    }

    private static List<String> choicesOf(List<String> choices) {
        if (choices.size() > MAXIMUM_CHOICES) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "At most " + MAXIMUM_CHOICES
                    + " choices", Map.of("choices", choices.size()));
        }
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String choice : choices) {
            String label = line(choice, "choice", MAXIMUM_CHOICE_LENGTH);
            if (!seen.add(label)) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "The choice «" + label
                        + "» is there twice", Map.of("choice", label));
            }
            labels.add(label);
        }
        return List.copyOf(labels);
    }

    /** {@code text} as one plain line within {@code maximum} characters, or refused. */
    private static String line(String text, String what, int maximum) {
        Objects.requireNonNull(text, what);
        String line = text.strip().replaceAll("\\s+", " ");
        if (line.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The " + what + " is empty",
                    Map.of(what, ""));
        }
        if (line.length() > maximum) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A " + what + " has at most " + maximum
                    + " characters", Map.of("length", line.length(), "maximum", maximum));
        }
        return PlainText.require(line, what);
    }

    private void announce(Consumer<PresentationListener> event) {
        events.execute(() -> listeners.forEach(event));
    }

    /** One question and what became of it. */
    private static final class Entry {

        private final Question question;
        private final CompletableFuture<AnswerOutcome> outcome = new CompletableFuture<>();

        Entry(Question question) {
            this.question = question;
        }
    }
}
