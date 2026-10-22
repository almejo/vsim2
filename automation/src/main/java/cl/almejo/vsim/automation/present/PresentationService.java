package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PresentationHost;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.AiTask;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.automation.edit.PreparedEdit;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * «Presentar»'s pacing layer in front of the {@link EditService} (IA.2, spec §5, §5.1). In working mode, or for a
 * call that is not a step (validate only, no task, only layout guides, spec §7), an edit goes through as in IA.1. In
 * «Presentar» each edit call is one step:
 * <ul>
 *     <li>«De corrido»: applied once the pause since the previous step has passed (the {@link StepPacer}'s timer,
 *     never the editor thread), and answered once applied;</li>
 *     <li>«Paso a paso»: planned against the current version, nothing written, kept as the task's one pending step
 *     and shown as a ghost; the call answers {@link StepStatus#PROPOSED} at once. «Siguiente» writes exactly that
 *     plan if the circuit is still at that version; any change of its history while it waits discards it
 *     ({@link StepStatus#STALE}).</li>
 * </ul>
 * «Hasta fin de etapa» paces the rest of the current stage like «De corrido»; «De corrido» waits at the next stage
 * only when the stage's check failed. {@link #awaitStep} answers within {@link #MAXIMUM_STEP_WAIT}; every wait ends
 * when the user stops the task, the task ends, the window closes, the AI is turned off or «Presentar» is left.
 * Listeners hear the plan and each step through {@code events}, never on the caller's stack.
 */
public final class PresentationService {

    /** The longest {@code await_step} waits in one call: well under the client's tool timeout (spec §5.1). */
    public static final Duration MAXIMUM_STEP_WAIT = Duration.ofSeconds(30);

    /** Presentations of tasks that no longer run kept for a late {@code await_step}. */
    static final int MAXIMUM_REMEMBERED_TASKS = 100;

    /** How long past the longest pause a paced call may wait for the timer before applying anyway. */
    private static final Duration PAUSE_SLACK = Duration.ofSeconds(5);

    private final PresentationHost host;
    private final EditService edits;
    private final List<PresentationListener> listeners;
    private final Executor events;
    private final StepPacer pacer;
    private final Duration maximumWait;
    /** Guarded by {@code this}; never held while waiting for the editor thread. */
    private final Map<String, TaskPresentation> presentations = new LinkedHashMap<>();

    /**
     * @param listeners   the live list of listeners (the API's own)
     * @param events      where listeners are called, in order
     * @param maximumWait the longest {@link #awaitStep} waits (at most {@link #MAXIMUM_STEP_WAIT})
     */
    public PresentationService(PresentationHost host, EditService edits, List<PresentationListener> listeners,
                               Executor events, Duration maximumWait) {
        this.host = Objects.requireNonNull(host, "host");
        this.edits = Objects.requireNonNull(edits, "edits");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        this.events = Objects.requireNonNull(events, "events");
        this.pacer = Objects.requireNonNull(host.stepPacer(), "pacer");
        Objects.requireNonNull(maximumWait, "maximumWait");
        this.maximumWait = maximumWait.compareTo(MAXIMUM_STEP_WAIT) > 0 ? MAXIMUM_STEP_WAIT : maximumWait;
    }

    // --- edits ---

    /** One edit call as «Presentar» takes it (see the class comment); throws as {@link EditService#edit} does. */
    public PresentedEdit edit(EditRequest request) {
        Optional<AiTask> checked = edits.checkRequest(request);
        if (checked.isEmpty() || request.validateOnly() || onlyLayoutGuides(request.operations())) {
            return new PresentedEdit(edits.edit(request), Optional.empty());
        }
        AiTask task = checked.get();
        TaskStep step = request.step().orElseThrow();
        Decision decision = edits.onCircuit(request.document(), circuit -> decide(circuit, request, task, step));
        if (decision.answer().isPresent()) {
            return decision.answer().get();
        }
        waitForThePause(decision.presentation(), decision.delay());
        return edits.onCircuit(request.document(),
                circuit -> applyPaced(circuit, request, decision.presentation(), decision.stageIndex(),
                        decision.stepIndex()));
    }

    /** What the editor thread decided for one step: answered already, or applied after {@code delay}. */
    private record Decision(Optional<PresentedEdit> answer, TaskPresentation presentation, Duration delay,
                            int stageIndex, int stepIndex) {

        static Decision answered(PresentedEdit answer) {
            return new Decision(Optional.of(answer), null, Duration.ZERO, 0, 0);
        }
    }

    /** Editor thread: working mode, a retry, a proposal, or a paced step (applied at once when no pause is due). */
    private Decision decide(EditableCircuit circuit, EditRequest request, AiTask task, TaskStep step) {
        TaskPresentation presentation;
        synchronized (this) {
            presentation = presentationOf(task);
            PendingStep pending = presentation.pending;
            if (pending != null) {
                if (pending.prepared().request().equals(request)) {
                    return Decision.answered(pending.answer());
                }
                throw new AutomationException(AutomationError.NOT_ALLOWED, "The step «" + pending.preview().text()
                        + "» of task " + task.info().taskId() + " still waits for «Siguiente»: call vsim_await_step "
                        + "until it is applied, stale or stopped, then send the next step. Nothing was changed",
                        Map.of("taskId", task.info().taskId()));
            }
        }
        PresentationSettings settings = host.presentationSettings(request.document());
        Optional<EditReceipt> earlier = edits.replay(circuit, request);
        if (earlier.isPresent()) {
            return Decision.answered(new PresentedEdit(earlier.get(),
                    settings.presenting() ? Optional.of(StepStatus.APPLIED) : Optional.empty()));
        }
        if (!settings.presenting()) {
            return Decision.answered(new PresentedEdit(edits.editOn(circuit, request), Optional.empty()));
        }
        PreparedEdit prepared = edits.prepare(circuit, request);
        int stageIndex;
        int stepIndex;
        PresentationPace pace;
        Duration delay;
        synchronized (this) {
            stageIndex = stageIndexOf(task.stages(), step.stage(), presentation.currentStage);
            stepIndex = stepIndexOf(task.stages(), stageIndex, step,
                    presentation.appliedPerStage.getOrDefault(stageIndex, 0));
            pace = presentation.paceFor(settings.pace(), stageIndex);
            delay = presentation.delayBefore(settings.pause(), pacer.now());
        }
        StepPreview preview = previewOf(task, stageIndex, stepIndex, step, prepared);
        if (pace == PresentationPace.STEP_BY_STEP) {
            return Decision.answered(propose(circuit, presentation, prepared, preview));
        }
        if (delay.isZero()) {
            EditReceipt receipt = edits.commit(circuit, prepared);
            applied(presentation, preview, receipt);
            return Decision.answered(new PresentedEdit(receipt, Optional.of(StepStatus.APPLIED)));
        }
        return new Decision(Optional.empty(), presentation, delay, stageIndex, stepIndex);
    }

    /** Editor thread: keeps the step as the task's pending one and shows it; nothing is written. */
    private PresentedEdit propose(EditableCircuit circuit, TaskPresentation presentation, PreparedEdit prepared,
                                  StepPreview preview) {
        PendingStep pending = new PendingStep(prepared, preview, circuit.history(),
                changed -> discard(presentation, changed, StepStatus.STALE));
        synchronized (this) {
            presentation.pending = pending;
            presentation.lastOutcome = null;
            presentation.currentStage = preview.stageIndex();
            presentation.currentStep = preview.stepIndex();
        }
        announcePlan(presentation);
        announce(listener -> listener.stepProposed(preview));
        return pending.answer();
    }

    /**
     * Off the editor thread: waits for the pacer's timer, at most the longest pause and some slack, or less when the
     * user stops the task, leaves «Presentar» or the window closes (the step is then refused or applied at once).
     */
    private void waitForThePause(TaskPresentation presentation, Duration delay) {
        CompletableFuture<Void> pause = new CompletableFuture<>();
        synchronized (this) {
            presentation.pauses.add(pause);
        }
        pacer.after(delay).whenComplete((ignored, failure) -> pause.complete(null));
        try {
            pause.get(PresentationSettings.MAXIMUM_PAUSE.plus(PAUSE_SLACK).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException exception) {
            // The timer never fired: apply now rather than keep the AI waiting.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The step was interrupted before it was "
                    + "applied; read the circuit before trying again");
        } finally {
            synchronized (this) {
                presentation.pauses.remove(pause);
            }
        }
    }

    /** Editor thread: the paced step, planned again on the circuit as it is now (stopped or changed: refused). */
    private PresentedEdit applyPaced(EditableCircuit circuit, EditRequest request, TaskPresentation presentation,
                                     int stageIndex, int stepIndex) {
        Optional<EditReceipt> earlier = edits.replay(circuit, request);
        if (earlier.isPresent()) {
            return new PresentedEdit(earlier.get(), Optional.of(StepStatus.APPLIED));
        }
        PreparedEdit prepared = edits.prepare(circuit, request);
        EditReceipt receipt = edits.commit(circuit, prepared);
        applied(presentation, previewOf(presentation.task, stageIndex, stepIndex, request.step().orElseThrow(),
                prepared), receipt);
        return new PresentedEdit(receipt, Optional.of(StepStatus.APPLIED));
    }

    /** Editor thread: a step is in the circuit; the plan moves on. */
    private void applied(TaskPresentation presentation, StepPreview preview, EditReceipt receipt) {
        synchronized (this) {
            presentation.lastOutcome = StepOutcome.applied(receipt);
            presentation.lastAppliedAt = pacer.now();
            presentation.appliedPerStage.merge(preview.stageIndex(), 1, Integer::sum);
            presentation.currentStage = preview.stageIndex();
            presentation.currentStep = preview.stepIndex() + 1;
            if (presentation.heldAfterStage >= 0 && preview.stageIndex() > presentation.heldAfterStage) {
                presentation.heldAfterStage = -1;
            }
        }
        announce(listener -> listener.stepApplied(preview));
        announcePlan(presentation);
    }

    // --- «Siguiente», «Hasta fin de etapa», «Detener» ---

    /**
     * {@code await_step}: the outcome of the task's pending step, waiting for it at most {@code wait} (capped at the
     * maximum): {@code APPLIED} with the receipt, {@code STALE}, {@code STOPPED}, or {@code STILL_WAITING} (ask
     * again). With no step pending it answers the last step's outcome. {@code INVALID_REFERENCE} for an unknown task,
     * {@code NOT_ALLOWED} when the task never proposed a step.
     */
    public StepOutcome awaitStep(String taskId, Duration wait) {
        Objects.requireNonNull(wait, "wait");
        AiTask task = edits.requireTask(taskId);
        CompletableFuture<StepOutcome> outcome;
        synchronized (this) {
            TaskPresentation presentation = presentations.get(taskId);
            if (presentation != null && presentation.pending != null) {
                outcome = presentation.pending.outcome();
            } else if (presentation != null && presentation.applying != null) {
                outcome = presentation.applying.outcome();
            } else if (presentation != null && presentation.lastOutcome != null) {
                return presentation.lastOutcome;
            } else if (task.state() != AiTask.State.RUNNING) {
                return StepOutcome.of(StepStatus.STOPPED);
            } else {
                throw new AutomationException(AutomationError.NOT_ALLOWED, "No step of task " + taskId + " waits: "
                        + "vsim_await_step follows a vsim_edit answered PROPOSED", Map.of("taskId", taskId));
            }
        }
        try {
            return outcome.get(capped(wait).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            return StepOutcome.of(StepStatus.STILL_WAITING);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return StepOutcome.of(StepStatus.STILL_WAITING);
        } catch (ExecutionException exception) {
            return StepOutcome.of(StepStatus.STOPPED);
        }
    }

    /** {@code wait} within zero and the maximum wait. */
    Duration capped(Duration wait) {
        if (wait.isNegative()) {
            return Duration.ZERO;
        }
        return wait.compareTo(maximumWait) > 0 ? maximumWait : wait;
    }

    /**
     * «Siguiente ▸»: writes the pending step exactly as it was shown when the circuit is still at its version (else
     * it is discarded as stale). Nothing pending: nothing happens. Waits for the editor thread (on it, runs at once).
     */
    public void next(String taskId) {
        TaskPresentation presentation;
        PendingStep pending;
        synchronized (this) {
            presentation = presentations.get(taskId);
            if (presentation == null || presentation.pending == null) {
                return;
            }
            pending = presentation.pending;
        }
        try {
            edits.onCircuit(presentation.task.info().document(), circuit -> {
                applyPending(circuit, presentation, pending);
                return Boolean.TRUE;
            });
        } catch (AutomationException closedOrTurnedOff) {
            discard(presentation, pending, StepStatus.STOPPED);
        }
    }

    /** Editor thread: the pending step, if it still is, written as one undo step. */
    private void applyPending(EditableCircuit circuit, TaskPresentation presentation, PendingStep pending) {
        synchronized (this) {
            if (presentation.pending != pending) {
                return;
            }
            presentation.pending = null;
            // An await_step arriving while the step is written waits for it rather than finding nothing pending.
            presentation.applying = pending;
        }
        try {
            pending.stopWatching();
            EditReceipt receipt;
            try {
                receipt = edits.commit(circuit, pending.prepared());
            } catch (AutomationException refusal) {
                dropped(presentation, pending, refusal.error() == AutomationError.STALE_DOCUMENT ? StepStatus.STALE
                        : StepStatus.STOPPED);
                return;
            }
            applied(presentation, pending.preview(), receipt);
            pending.resolve(StepOutcome.applied(receipt));
        } finally {
            synchronized (this) {
                if (presentation.applying == pending) {
                    presentation.applying = null;
                }
            }
        }
    }

    /**
     * «Hasta fin de etapa»: the rest of the current stage goes at the «De corrido» pace as the AI sends it, starting
     * with the pending step; the next stage waits for «Siguiente» again.
     */
    public void toStageEnd(String taskId) {
        synchronized (this) {
            TaskPresentation presentation = presentations.get(taskId);
            if (presentation == null) {
                return;
            }
            presentation.continuousStage = presentation.pending != null ? presentation.pending.preview().stageIndex()
                    : presentation.currentStage;
        }
        next(taskId);
    }

    /** «Detener»: stops the task as IA.1 does and drops its pending step and paced step. */
    public void stop(String taskId) {
        edits.stopTask(taskId);
        TaskPresentation presentation;
        synchronized (this) {
            presentation = presentations.get(taskId);
        }
        if (presentation != null) {
            dropEverything(presentation, StepStatus.STOPPED);
        }
    }

    // --- the task's life ---

    /** A task began: listeners hear its plan at its first step. */
    public void taskBegun(TaskInfo info) {
        AiTask task = edits.requireTask(info.taskId());
        TaskPresentation presentation;
        synchronized (this) {
            presentation = presentationOf(task);
        }
        announcePlan(presentation);
    }

    /** The task's plan changed (a stage detailed when reached). */
    public void planUpdated(String taskId) {
        AiTask task = edits.requireTask(taskId);
        TaskPresentation presentation;
        synchronized (this) {
            presentation = presentationOf(task);
        }
        announcePlan(presentation);
    }

    /** The task is about to end: its pending step is dropped ({@code STOPPED}) and it is forgotten. */
    public void taskEnding(String taskId) {
        TaskPresentation presentation;
        synchronized (this) {
            presentation = presentations.remove(taskId);
        }
        if (presentation != null) {
            dropEverything(presentation, StepStatus.STOPPED);
        }
    }

    /**
     * VSim checked a stage of the task (spec §3): listeners hear it, and a failed check makes «De corrido» wait for
     * «Siguiente» at the next stage. {@code stage} is the stage's title (or number); the current stage when empty.
     */
    public void stageChecked(String taskId, Optional<String> stage, boolean passed, String summary) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(summary, "summary");
        Optional<AiTask> task = edits.task(taskId);
        if (task.isEmpty()) {
            return;
        }
        int stageIndex;
        synchronized (this) {
            TaskPresentation presentation = presentationOf(task.get());
            int current = presentation.currentStage;
            stageIndex = stage.map(title -> stageIndexOf(task.get().stages(), title, current)).orElse(current);
            if (!passed) {
                presentation.heldAfterStage = Math.max(presentation.heldAfterStage, stageIndex);
            } else if (presentation.heldAfterStage == stageIndex) {
                presentation.heldAfterStage = -1;
            }
        }
        announce(listener -> listener.stageChecked(taskId, stageIndex, passed, summary));
    }

    // --- the editor's side ---

    /**
     * The window of {@code document} changed its presentation mode. A step waiting for «Siguiente» is written now when
     * the user switches to «De corrido» or leaves «Presentar» (a mode change never loses a step); the following
     * steps take the new mode. Waits for the editor thread: call it off it.
     */
    public void settingsChanged(DocumentId document, PresentationSettings settings) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(settings, "settings");
        boolean applyPending = !settings.presenting() || settings.pace() == PresentationPace.CONTINUOUS;
        for (TaskPresentation presentation : presentationsOf(document)) {
            if (!settings.presenting()) {
                interruptPauses(presentation);
            }
            if (applyPending) {
                next(presentation.task.info().taskId());
            }
        }
    }

    /** The circuit's last window closed: its pending steps are dropped ({@code STOPPED}). */
    public void documentClosed(DocumentId document) {
        Objects.requireNonNull(document, "document");
        presentationsOf(document).forEach(presentation -> dropEverything(presentation, StepStatus.STOPPED));
    }

    /** The AI was turned off or VSim quits: every pending step is dropped ({@code STOPPED}). */
    public void stopAll() {
        List<TaskPresentation> all;
        synchronized (this) {
            all = new ArrayList<>(presentations.values());
        }
        all.forEach(presentation -> dropEverything(presentation, StepStatus.STOPPED));
    }

    private List<TaskPresentation> presentationsOf(DocumentId document) {
        synchronized (this) {
            return presentations.values().stream()
                    .filter(presentation -> presentation.task.info().document().equals(document))
                    .toList();
        }
    }

    // --- dropping ---

    private void dropEverything(TaskPresentation presentation, StepStatus why) {
        interruptPauses(presentation);
        PendingStep pending;
        synchronized (this) {
            pending = presentation.pending;
        }
        if (pending != null) {
            discard(presentation, pending, why);
        }
    }

    /** Any thread: drops {@code pending} if it still is the task's pending step. */
    private void discard(TaskPresentation presentation, PendingStep pending, StepStatus why) {
        synchronized (this) {
            if (presentation.pending != pending) {
                return;
            }
            presentation.pending = null;
        }
        dropped(presentation, pending, why);
    }

    private void dropped(TaskPresentation presentation, PendingStep pending, StepStatus why) {
        StepOutcome outcome = StepOutcome.of(why);
        synchronized (this) {
            presentation.lastOutcome = outcome;
        }
        pending.resolve(outcome);
        String taskId = presentation.task.info().taskId();
        announce(listener -> listener.stepDiscarded(taskId, why));
    }

    private void interruptPauses(TaskPresentation presentation) {
        List<CompletableFuture<Void>> pauses;
        synchronized (this) {
            pauses = new ArrayList<>(presentation.pauses);
        }
        pauses.forEach(pause -> pause.complete(null));
    }

    // --- the plan ---

    private void announcePlan(TaskPresentation presentation) {
        int stage;
        int step;
        synchronized (this) {
            stage = presentation.currentStage;
            step = presentation.currentStep;
        }
        TaskInfo info = presentation.task.info();
        List<TaskStage> stages = presentation.task.stages();
        announce(listener -> listener.planChanged(info, stages, stage, step));
    }

    private void announce(Consumer<PresentationListener> event) {
        events.execute(() -> listeners.forEach(event));
    }

    private static StepPreview previewOf(AiTask task, int stageIndex, int stepIndex, TaskStep step,
                                         PreparedEdit prepared) {
        String text = step.text().isBlank() ? step.step() : step.text();
        return new StepPreview(task.info().taskId(), stageIndex, stepIndex, text, step.reason(),
                prepared.ghostElements(), prepared.removedIds(), prepared.changedBounds());
    }

    /** The stage by its title (any case), else by its number from 1, else {@code fallback}. */
    static int stageIndexOf(List<TaskStage> stages, String stage, int fallback) {
        String wanted = stage.strip();
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).title().strip().equalsIgnoreCase(wanted)) {
                return index;
            }
        }
        Optional<Integer> number = numberOf(wanted);
        if (number.isPresent() && number.get() >= 1 && number.get() <= stages.size()) {
            return number.get() - 1;
        }
        return fallback;
    }

    /** The step by its text in the stage's plan (any case), else by its number from 1, else {@code fallback}. */
    static int stepIndexOf(List<TaskStage> stages, int stageIndex, TaskStep step, int fallback) {
        if (stageIndex < 0 || stageIndex >= stages.size()) {
            return fallback;
        }
        List<String> steps = stages.get(stageIndex).steps();
        for (int index = 0; index < steps.size(); index++) {
            String planned = steps.get(index).strip();
            if (planned.equalsIgnoreCase(step.step().strip()) || planned.equalsIgnoreCase(step.text().strip())) {
                return index;
            }
        }
        Optional<Integer> number = numberOf(step.step().strip());
        if (number.isPresent() && number.get() >= 1 && number.get() <= steps.size()) {
            return number.get() - 1;
        }
        return fallback;
    }

    private static Optional<Integer> numberOf(String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Guides added only to align are never a step (spec §7). */
    private static boolean onlyLayoutGuides(List<EditOperation> operations) {
        return !operations.isEmpty() && operations.stream()
                .allMatch(operation -> operation instanceof EditOperation.AddGuide guide && guide.layoutOnly());
    }

    /** Called holding the lock of {@code this}. */
    private TaskPresentation presentationOf(AiTask task) {
        TaskPresentation presentation = presentations.computeIfAbsent(task.info().taskId(),
                taskId -> new TaskPresentation(task));
        if (presentations.size() > MAXIMUM_REMEMBERED_TASKS) {
            List<String> finished = presentations.values().stream()
                    .filter(candidate -> candidate.task.state() != AiTask.State.RUNNING && candidate.pending == null)
                    .map(candidate -> candidate.task.info().taskId())
                    .toList();
            for (int index = 0; index < finished.size() && presentations.size() > MAXIMUM_REMEMBERED_TASKS;
                 index++) {
                presentations.remove(finished.get(index));
            }
        }
        return presentation;
    }

    /** One task as «Presentar» follows it. Guarded by the service's lock. */
    private static final class TaskPresentation {

        private final AiTask task;
        private PendingStep pending;
        /** The step «Siguiente» is writing right now, until its outcome is known. */
        private PendingStep applying;
        private StepOutcome lastOutcome;
        private int currentStage;
        private int currentStep;
        private final Map<Integer, Integer> appliedPerStage = new HashMap<>();
        /** «Hasta fin de etapa»: the stage paced like «De corrido» whatever the chosen pace; -1 for none. */
        private int continuousStage = -1;
        /** «De corrido» waits for «Siguiente» at the stages after this one, whose check failed; -1 for none. */
        private int heldAfterStage = -1;
        private Instant lastAppliedAt;
        private final Set<CompletableFuture<Void>> pauses = new HashSet<>();

        TaskPresentation(AiTask task) {
            this.task = task;
        }

        PresentationPace paceFor(PresentationPace chosen, int stageIndex) {
            if (continuousStage >= 0) {
                if (stageIndex == continuousStage) {
                    return PresentationPace.CONTINUOUS;
                }
                continuousStage = -1;
            }
            if (heldAfterStage >= 0 && stageIndex > heldAfterStage) {
                return PresentationPace.STEP_BY_STEP;
            }
            return chosen;
        }

        Duration delayBefore(Duration pause, Instant now) {
            if (lastAppliedAt == null) {
                return Duration.ZERO;
            }
            Duration left = Duration.between(now, lastAppliedAt.plus(pause));
            return left.isNegative() ? Duration.ZERO : left;
        }
    }
}
