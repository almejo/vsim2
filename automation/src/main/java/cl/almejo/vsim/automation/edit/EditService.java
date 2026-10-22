package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditHost;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.command.SetGuidesCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The AI's edits and tasks (spec §3, §4.1, §8; plan IA.1 Task 3). An edit call is carried out on a copy of the circuit
 * and written as one undo step, all on the editor thread in one go: the version check, the copy, the plan and the
 * write happen with nothing in between, so the user's edits can never slip under it (A§6.1). A failed call changes
 * nothing; a retried one returns its first receipt. Tasks group the edits: «Detener» stops one (what it applied stays,
 * its next edits are refused) and «Deshacer lo que hizo la IA» undoes its steps while they are on top of the history.
 * Listeners hear tasks begin, end and stop through {@code events}, never on the caller's own call stack.
 */
public final class EditService {

    /** Tasks kept after they end, so a late «Deshacer lo que hizo la IA» still finds its task. */
    static final int MAXIMUM_FINISHED_TASKS = 100;

    private static final int MAXIMUM_OPERATION_ID_LENGTH = 200;
    private static final String ALIAS_PREFIX = EditPlanner.ALIAS_PREFIX;

    /** The stage of a step VSim writes that belongs to no stage. */
    public static final int NO_STAGE = AiEditCommand.NO_STAGE;

    /** The undo step of «Dejar solo el resultado». */
    public static final String KEEP_ONLY_RESULT_DESCRIPTION = "command.aiKeepResult";

    /** The undo step of a stage's frame: like any other change of the AI. */
    public static final String STAGE_FRAME_DESCRIPTION = AiEditCommand.DESCRIPTION;

    private final EditHost host;
    private final List<AutomationListener> listeners;
    private final EditorWait editorWait;
    private final Executor events;
    private final Map<DocumentId, OperationLog> logs = new ConcurrentHashMap<>();
    private final Map<String, AiTask> tasks = new LinkedHashMap<>();
    private final AtomicLong lastTaskNumber = new AtomicLong();
    /**
     * Per circuit, the last step VSim wrote by itself for the storyboard (a stage's frame, IA.2): an edit prepared
     * against the version just before it is still current right after it, so the frame never makes the AI's next
     * step stale. Any other change of the circuit makes it stale as always.
     */
    private final Map<DocumentId, VersionStep> transparentSteps = new ConcurrentHashMap<>();

    /** The version before and after a step VSim wrote by itself. */
    private record VersionStep(DocumentVersion before, DocumentVersion after) {
    }

    /**
     * @param listeners the live list of listeners (the API's own)
     * @param events    where listeners are called, in order
     */
    public EditService(EditHost host, List<AutomationListener> listeners, Duration editorTimeout, Executor events) {
        this.host = Objects.requireNonNull(host, "host");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        this.editorWait = new EditorWait(Objects.requireNonNull(editorTimeout, "editorTimeout"));
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Carries out one edit call. Throws {@link AutomationException}: {@code STALE_DOCUMENT} (with
     * {@code currentVersion}) when the circuit changed since {@code request.expected()}, {@code OPERATION_CONFLICT}
     * for a reused operation id, {@code NOT_ALLOWED} when its task was stopped, and the operation's own error otherwise
     * — always with the circuit as it was.
     */
    public EditReceipt edit(EditRequest request) {
        Optional<AiTask> task = checkRequest(request);
        return editorWait.await(host.onCircuit(request.document(), circuit -> edit(circuit, request, task)));
    }

    /**
     * The checks of {@link #edit(EditRequest)} done before reaching the editor thread: the operation id, and the task
     * the step names (it exists and works on the request's circuit). Returns that task.
     */
    public Optional<AiTask> checkRequest(EditRequest request) {
        Objects.requireNonNull(request, "request");
        requireOperationId(request.operationId());
        Optional<AiTask> task = request.step().map(step -> requireTask(step.taskId()));
        task.ifPresent(found -> requireSameDocument(found, request.document()));
        return task;
    }

    /** Editor thread: everything from the version check to the write, with nothing in between. */
    private EditReceipt edit(EditableCircuit circuit, EditRequest request, Optional<AiTask> task) {
        Optional<EditReceipt> earlier = replay(circuit, request);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        PreparedEdit prepared = prepare(circuit, request, task);
        if (request.validateOnly()) {
            return prepared.proposal();
        }
        return commit(circuit, prepared);
    }

    // --- the steps of an edit, for the presenting layer (IA.2): all on the editor thread ---

    /**
     * Runs {@code work} on the editor thread with circuit {@code document} and waits for it here, off that thread, at
     * most the editor timeout; what {@code work} threw reaches the caller as it is.
     */
    public <T> T onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        return editorWait.await(host.onCircuit(document, work));
    }

    /**
     * Editor thread: the whole edit call of {@link #edit(EditRequest)} (its checks done there already: operation id,
     * task, document): replay, version check, plan and write.
     */
    public EditReceipt editOn(EditableCircuit circuit, EditRequest request) {
        return edit(circuit, request, request.step().map(step -> requireTask(step.taskId())));
    }

    /**
     * Editor thread: the receipt of an earlier call with the same operation id and arguments (a retry); empty for a
     * new id, {@code OPERATION_CONFLICT} for the same id with other arguments. Validate-only calls are never replayed.
     */
    public Optional<EditReceipt> replay(EditableCircuit circuit, EditRequest request) {
        if (request.validateOnly()) {
            return Optional.empty();
        }
        return logOf(request.document()).replay(request);
    }

    /**
     * Editor thread: checks the task runs and the version, and plans the call on a copy; nothing is written. Throws as
     * {@link #edit(EditRequest)} does.
     */
    public PreparedEdit prepare(EditableCircuit circuit, EditRequest request) {
        return prepare(circuit, request, request.step().map(step -> requireTask(step.taskId())));
    }

    private PreparedEdit prepare(EditableCircuit circuit, EditRequest request, Optional<AiTask> task) {
        task.ifPresent(EditService::requireRunning);
        requireVersion(request.document(), circuit, request.expected());
        EditPlanner.PlannedEdit planned;
        ReplaceContentCommand content;
        try {
            planned = EditPlanner.plan(circuit.document(), circuit.registry(), circuit.library(),
                    request.operations());
            content = ReplaceContentCommand.between(circuit.document(), planned.candidate(),
                    AiEditCommand.DESCRIPTION);
        } catch (AutomationException | IllegalArgumentException refusal) {
            throw refusal;
        } catch (RuntimeException unexpected) {
            // Planning works on a copy: whatever went wrong, the circuit was not touched (review M13).
            throw new AutomationException(AutomationError.NOT_ALLOWED, "VSim could not carry out this edit ("
                    + unexpected.getClass().getSimpleName() + "). Nothing was changed; read the circuit and try "
                    + "another way");
        }
        return new PreparedEdit(request, task, circuit.version(), planned, content, changesOf(content),
                circuit.document(), circuit.registry());
    }

    /**
     * Editor thread: writes {@code prepared} exactly as it was planned, as one undo step of its task. Refused, writing
     * nothing, when its task no longer runs or the circuit moved from the version it was planned against
     * ({@code STALE_DOCUMENT}).
     */
    public EditReceipt commit(EditableCircuit circuit, PreparedEdit prepared) {
        prepared.task().ifPresent(EditService::requireRunning);
        requireVersion(prepared.request().document(), circuit, prepared.version());
        EditRequest request = prepared.request();
        OperationLog log = logOf(request.document());
        if (prepared.changesNothing()) {
            EditReceipt receipt = receipt(true, circuit.version(), prepared.planned(), prepared.changes(),
                    List.of("nothing changed: no undo step was added"));
            log.record(request, receipt);
            return receipt;
        }
        int stageIndex = prepared.task().map(found -> stageOf(found, request)).orElse(AiEditCommand.NO_STAGE);
        AiEditCommand command = new AiEditCommand(prepared.content(),
                prepared.task().map(found -> found.info().taskId()), stageIndex, AiEditCommand.DESCRIPTION);
        circuit.history().execute(command);
        EditReceipt receipt = receipt(true, circuit.version(), prepared.planned(), prepared.changes(), List.of());
        log.record(request, receipt);
        prepared.task().ifPresent(found -> {
            found.recordEdit(command, prepared.changes(), prepared.planned().layoutGuides());
            found.recordHowItWasMade(howItWasMade(request, prepared.planned().aliases()), stageIndex);
        });
        return receipt;
    }

    /** The stage of its task a step belongs to: the one it names, else the one the AI said it works on. */
    private static int stageOf(AiTask task, EditRequest request) {
        return request.step()
                .map(step -> TaskStage.indexIn(task.stages(), step.stage(), task.currentStage()))
                .orElse(task.currentStage());
    }

    /** The parts a {@code CreateComponent} without replacing kept as «cómo se hizo» (spec §6), by their real ids. */
    private static List<String> howItWasMade(EditRequest request, Map<String, String> aliases) {
        List<String> kept = new ArrayList<>();
        for (EditOperation operation : request.operations()) {
            if (operation instanceof EditOperation.CreateComponent created && !created.replaceSelection()) {
                for (String target : created.targets()) {
                    kept.add(target.startsWith(ALIAS_PREFIX)
                            ? aliases.getOrDefault(target.substring(ALIAS_PREFIX.length()), target) : target);
                }
            }
        }
        return kept;
    }

    // --- steps VSim writes for a task by itself (the storyboard, IA.2) ---

    /**
     * Editor thread: writes {@code candidate} (a changed copy of the circuit) as one undo step of {@code task}, in
     * stage {@code stageIndex} ({@link #NO_STAGE} for none), described by the i18n key {@code description}; the
     * elements it adds count as the stage's. It is written even when the task no longer runs («Dejar solo el
     * resultado» comes at its end). {@code transparent}: an edit prepared against the version just before it stays
     * current (a stage's frame never makes the AI's next step stale). Returns what it changed; nothing when the copy
     * is the same as the circuit (no undo step).
     */
    public EditReceipt.ElementChanges writeTaskStep(EditableCircuit circuit, AiTask task, int stageIndex,
                                                    CircuitDocument candidate, String description,
                                                    boolean transparent) {
        ReplaceContentCommand content = ReplaceContentCommand.between(circuit.document(), candidate, description);
        if (content.changesNothing()) {
            return EditReceipt.ElementChanges.NONE;
        }
        EditReceipt.ElementChanges changes = changesOf(content);
        DocumentVersion before = circuit.version();
        AiEditCommand command = new AiEditCommand(content, Optional.of(task.info().taskId()), stageIndex,
                description);
        circuit.history().execute(command);
        task.recordEdit(command, changes, List.of());
        if (transparent) {
            DocumentVersion after = circuit.version();
            // Several frames in a row (stages that got theirs together) are still one step for the AI.
            transparentSteps.compute(task.info().document(), (document, earlier) ->
                    earlier != null && earlier.after().equals(before) ? new VersionStep(earlier.before(), after)
                            : new VersionStep(before, after));
        }
        return changes;
    }

    private OperationLog logOf(DocumentId document) {
        return logs.computeIfAbsent(document, newDocument -> new OperationLog());
    }

    static EditReceipt receipt(boolean applied, DocumentVersion version, EditPlanner.PlannedEdit planned,
                                       EditReceipt.ElementChanges changes, List<String> extraWarnings) {
        List<String> warnings = new ArrayList<>(planned.warnings());
        warnings.addAll(extraWarnings);
        return new EditReceipt(applied, version, planned.aliases(), planned.connections(), planned.components(),
                changes, warnings, AiEditCommand.DESCRIPTION);
    }

    private static EditReceipt.ElementChanges changesOf(ReplaceContentCommand content) {
        return new EditReceipt.ElementChanges(values(content.addedIds()), values(content.changedIds()),
                values(content.removedIds()));
    }

    private static List<String> values(Set<ElementId> ids) {
        return ids.stream().map(ElementId::value).toList();
    }

    private void requireVersion(DocumentId document, EditableCircuit circuit, DocumentVersion expected) {
        DocumentVersion current = circuit.version();
        VersionStep transparent = transparentSteps.get(document);
        boolean onlyVsimsOwnStep = transparent != null && transparent.before().equals(expected)
                && transparent.after().equals(current);
        if (!current.equals(expected) && !onlyVsimsOwnStep) {
            throw new AutomationException(AutomationError.STALE_DOCUMENT, "The circuit changed since version "
                    + expected.value() + " (it is at " + current.value() + "): read it again and adapt the edit. "
                    + "Nothing was changed", Map.of("currentVersion", current.value()));
        }
    }

    private static void requireOperationId(String operationId) {
        if (operationId.isBlank() || operationId.length() > MAXIMUM_OPERATION_ID_LENGTH) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "An edit needs an operation id of 1 to "
                    + MAXIMUM_OPERATION_ID_LENGTH + " characters, new for each edit");
        }
    }

    // --- tasks ---

    /**
     * Begins a task on an open circuit (spec §3) and announces it. A task still running on that circuit ends first:
     * one AI works on a circuit at a time.
     */
    public TaskInfo beginTask(DocumentId document, String title, List<TaskStage> stages) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(stages, "stages");
        editorWait.await(host.onCircuit(document, circuit -> Boolean.TRUE));
        runningTaskOf(document).ifPresent(running -> endTask(running.info().taskId(), false));
        TaskInfo info = new TaskInfo("task-" + lastTaskNumber.incrementAndGet(), document, title);
        AiTask task = new AiTask(info, stages);
        synchronized (tasks) {
            tasks.put(info.taskId(), task);
            forgetOldTasks();
        }
        announce(listener -> listener.taskStarted(info));
        return info;
    }

    /** Replaces the plan of a task (a stage detailed when it is reached, spec §3). */
    public void updatePlan(String taskId, List<TaskStage> stages) {
        Objects.requireNonNull(stages, "stages");
        requireTask(taskId).replacePlan(stages);
    }

    /**
     * Ends a task and announces what it changed; a task that ended or was stopped already stays as it is. The guides
     * it added only to align ({@link EditOperation.AddGuide#layoutOnly}) and that are still there go away first, as
     * one more step of the task (so «Deshacer lo que hizo la IA» brings them back with the rest), unless
     * {@code keepLayoutGuides} (the AI or the user wants them). Not on the editor thread: it waits for it.
     */
    public TaskEnding endTask(String taskId, boolean keepLayoutGuides) {
        AiTask task = requireTask(taskId);
        if (!task.end()) {
            return new TaskEnding(task.info(), 0);
        }
        int removed = keepLayoutGuides || task.layoutGuides().isEmpty() ? 0 : removeLayoutGuides(task);
        announce(listener -> listener.taskEnded(task.info(), task.changedElementIds()));
        return new TaskEnding(task.info(), removed);
    }

    /** {@link #endTask(String, boolean)} taking the layout-only guides away. */
    public TaskInfo endTask(String taskId) {
        return endTask(taskId, false).task();
    }

    /** How many of the task's layout-only guides were still there and went away; 0 once the circuit is closed. */
    private int removeLayoutGuides(AiTask task) {
        try {
            return editorWait.await(host.onCircuit(task.info().document(),
                    circuit -> removeLayoutGuides(circuit, task)));
        } catch (AutomationException closed) {
            if (closed.error() == AutomationError.DOCUMENT_CLOSED) {
                return 0;
            }
            throw closed;
        }
    }

    /** Editor thread: one step of the task that takes away its layout-only guides still in the circuit. */
    private static int removeLayoutGuides(EditableCircuit circuit, AiTask task) {
        CircuitDocument candidate = circuit.document().copy();
        int removed = 0;
        for (Guide guide : task.layoutGuides()) {
            Optional<SetGuidesCommand> removal = SetGuidesCommand.removing(candidate, guide);
            if (removal.isPresent()) {
                removal.get().apply(candidate);
                removed++;
            }
        }
        if (removed == 0) {
            return 0;
        }
        ReplaceContentCommand content = ReplaceContentCommand.between(circuit.document(), candidate,
                AiEditCommand.DESCRIPTION);
        AiEditCommand command = new AiEditCommand(content, Optional.of(task.info().taskId()),
                AiEditCommand.CLEANUP_STAGE, AiEditCommand.DESCRIPTION);
        circuit.history().execute(command);
        task.recordEdit(command, EditReceipt.ElementChanges.NONE, List.of());
        return removed;
    }

    /** «Detener»: the task's applied edits stay (undoable), its next edits are refused, and listeners hear it. */
    public void stopTask(String taskId) {
        AiTask task = requireTask(taskId);
        if (task.stop()) {
            announce(listener -> listener.taskStopped(task.info()));
        }
    }

    /**
     * «Deshacer lo que hizo la IA» (spec §4.1): undoes the task's steps one after the other while one of them is on
     * top of the history; stops at anything else — the user's own edit — and says so when steps of the task remain
     * under it, so the user's work is never undone. Safe to call from the editor thread.
     */
    public UndoOutcome undoTask(String taskId) {
        AiTask task = requireTask(taskId);
        return editorWait.await(host.onCircuit(task.info().document(), circuit -> undo(circuit.history(), task)));
    }

    /**
     * «Deshacer etapa» (spec §4.1): undoes the steps of stage {@code stageIndex} of the task one after the other while
     * one of them is on top of the history (passing over the task's step that took its layout-only guides away);
     * stops at anything else — the user's own edit, another stage's step — and says which when steps of the stage
     * remain under it. Safe to call from the editor thread.
     */
    public StageUndoOutcome undoStage(String taskId, int stageIndex) {
        AiTask task = requireTask(taskId);
        return editorWait.await(host.onCircuit(task.info().document(),
                circuit -> undoStage(circuit.history(), task, stageIndex)));
    }

    /**
     * «Deshacer etapa» in the panel: undoes the stage of the task's step on top of the history, else the latest stage
     * that still has a step in the circuit (a stage that only checked has none, so the one before it is undone);
     * {@link StageUndoOutcome#NOTHING_TO_UNDO} when no stage has any. Pressed again, it goes back one more stage.
     * Safe to call from the editor thread.
     */
    public StageUndoOutcome undoLatestStage(String taskId) {
        AiTask task = requireTask(taskId);
        return editorWait.await(host.onCircuit(task.info().document(), circuit -> {
            OptionalInt stage = topStepOf(circuit.history(), task)
                    .filter(edit -> edit.stageIndex() >= 0)
                    .map(edit -> OptionalInt.of(edit.stageIndex()))
                    .orElseGet(task::latestStageWithAppliedEdits);
            return stage.isPresent() ? undoStage(circuit.history(), task, stage.getAsInt())
                    : StageUndoOutcome.NOTHING_TO_UNDO;
        }));
    }

    private static StageUndoOutcome undoStage(History history, AiTask task, int stageIndex) {
        int undone = 0;
        while (true) {
            Optional<AiEditCommand> top = topStepOf(history, task);
            boolean ofTheStage = top.filter(edit -> edit.stageIndex() == stageIndex).isPresent();
            boolean cleanupOverIt = top.filter(edit -> edit.stageIndex() == AiEditCommand.CLEANUP_STAGE).isPresent()
                    && task.hasAppliedEdits(stageIndex);
            if (!ofTheStage && !cleanupOverIt) {
                break;
            }
            history.undo();
            undone++;
        }
        StageUndoOutcome.StoppedAt stoppedAt = !task.hasAppliedEdits(stageIndex) ? StageUndoOutcome.StoppedAt.NOTHING
                : topStepOf(history, task).isPresent() ? StageUndoOutcome.StoppedAt.ANOTHER_STAGE
                : StageUndoOutcome.StoppedAt.USER_EDIT;
        return new StageUndoOutcome(stageIndex, undone, stoppedAt, task.latestStageWithAppliedEdits().isPresent());
    }

    /** The step on top of the history when it is one of {@code task}'s. */
    private static Optional<AiEditCommand> topStepOf(History history, AiTask task) {
        return history.undoCommand().filter(AiEditCommand.class::isInstance).map(AiEditCommand.class::cast)
                .filter(edit -> edit.belongsTo(task.info().taskId()));
    }

    /** The AI says it works on stage {@code stageIndex} of the task now («begin stage», IA.2). */
    public void enterStage(String taskId, int stageIndex) {
        AiTask task = requireTask(taskId);
        requireRunning(task);
        if (stageIndex < 0 || stageIndex >= Math.max(1, task.stages().size())) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The task " + taskId + " has "
                    + task.stages().size() + " stages: give a stage index from 0 to "
                    + (task.stages().size() - 1), Map.of("taskId", taskId, "stageIndex", stageIndex));
        }
        task.enterStage(stageIndex);
    }

    private static UndoOutcome undo(History history, AiTask task) {
        int undone = 0;
        while (isStepOf(history.undoCommand(), task)) {
            history.undo();
            undone++;
        }
        return new UndoOutcome(undone, task.hasAppliedEdits());
    }

    private static boolean isStepOf(Optional<Command> command, AiTask task) {
        return command.filter(AiEditCommand.class::isInstance)
                .map(AiEditCommand.class::cast)
                .filter(edit -> edit.belongsTo(task.info().taskId()))
                .isPresent();
    }

    /** The task, as the plan and the status line need it. */
    public Optional<AiTask> task(String taskId) {
        synchronized (tasks) {
            return Optional.ofNullable(tasks.get(taskId));
        }
    }

    /** The task {@code taskId}; {@code INVALID_REFERENCE} when there is none. */
    public AiTask requireTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return task(taskId).orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                "There is no task " + taskId + "; begin one first", Map.of("taskId", taskId)));
    }

    private static void requireSameDocument(AiTask task, DocumentId document) {
        if (!task.info().document().equals(document)) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "The task " + task.info().taskId()
                    + " works on " + task.info().document().value() + ", not on " + document.value());
        }
    }

    private static void requireRunning(AiTask task) {
        switch (task.state()) {
            case RUNNING -> {
            }
            case STOPPED -> throw new AutomationException(AutomationError.NOT_ALLOWED, "The user stopped the task "
                    + task.info().taskId() + " («Detener»): what it applied stays; ask the user before going on",
                    Map.of("taskId", task.info().taskId()));
            case ENDED -> throw new AutomationException(AutomationError.NOT_ALLOWED, "The task "
                    + task.info().taskId() + " has ended; begin a new one", Map.of("taskId", task.info().taskId()));
        }
    }

    /** True while a task runs on {@code document}. */
    public boolean hasRunningTask(DocumentId document) {
        return runningTaskOf(document).isPresent();
    }

    private Optional<AiTask> runningTaskOf(DocumentId document) {
        synchronized (tasks) {
            return tasks.values().stream()
                    .filter(task -> task.state() == AiTask.State.RUNNING && task.info().document().equals(document))
                    .findFirst();
        }
    }

    /** Keeps every running task and the most recent finished ones. Called holding the lock of {@code tasks}. */
    private void forgetOldTasks() {
        List<String> finished = tasks.values().stream()
                .filter(task -> task.state() != AiTask.State.RUNNING)
                .map(task -> task.info().taskId())
                .toList();
        for (int index = 0; index < finished.size() - MAXIMUM_FINISHED_TASKS; index++) {
            tasks.remove(finished.get(index));
        }
    }

    private void announce(Consumer<AutomationListener> event) {
        events.execute(() -> listeners.forEach(event));
    }
}
