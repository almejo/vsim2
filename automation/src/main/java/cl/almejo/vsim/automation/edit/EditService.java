package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditHost;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.document.ElementId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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

    private final EditHost host;
    private final List<AutomationListener> listeners;
    private final EditorWait editorWait;
    private final Executor events;
    private final Map<DocumentId, OperationLog> logs = new ConcurrentHashMap<>();
    private final Map<String, AiTask> tasks = new LinkedHashMap<>();
    private final AtomicLong lastTaskNumber = new AtomicLong();

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
        Objects.requireNonNull(request, "request");
        requireOperationId(request.operationId());
        Optional<AiTask> task = request.step().map(step -> requireTask(step.taskId()));
        task.ifPresent(found -> requireSameDocument(found, request.document()));
        return editorWait.await(host.onCircuit(request.document(), circuit -> edit(circuit, request, task)));
    }

    /** Editor thread: everything from the version check to the write, with nothing in between. */
    private EditReceipt edit(EditableCircuit circuit, EditRequest request, Optional<AiTask> task) {
        OperationLog log = logs.computeIfAbsent(request.document(), document -> new OperationLog());
        Optional<EditReceipt> earlier = request.validateOnly() ? Optional.empty() : log.replay(request);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        task.ifPresent(EditService::requireRunning);
        requireVersion(circuit, request.expected());
        EditPlanner.PlannedEdit planned = EditPlanner.plan(circuit.document(), circuit.registry(), circuit.library(),
                request.operations());
        ReplaceContentCommand content = ReplaceContentCommand.between(circuit.document(), planned.candidate(),
                AiEditCommand.DESCRIPTION);
        EditReceipt.ElementChanges changes = changesOf(content);
        if (request.validateOnly()) {
            return receipt(false, circuit.version(), planned, changes, List.of());
        }
        if (content.changesNothing()) {
            EditReceipt receipt = receipt(true, circuit.version(), planned, changes,
                    List.of("nothing changed: no undo step was added"));
            log.record(request, receipt);
            return receipt;
        }
        AiEditCommand command = new AiEditCommand(content, task.map(found -> found.info().taskId()));
        circuit.history().execute(command);
        EditReceipt receipt = receipt(true, circuit.version(), planned, changes, List.of());
        log.record(request, receipt);
        task.ifPresent(found -> found.recordEdit(command, changes));
        return receipt;
    }

    private static EditReceipt receipt(boolean applied, DocumentVersion version, EditPlanner.PlannedEdit planned,
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

    private static void requireVersion(EditableCircuit circuit, DocumentVersion expected) {
        DocumentVersion current = circuit.version();
        if (!current.equals(expected)) {
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
        runningTaskOf(document).ifPresent(running -> endTask(running.info().taskId()));
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

    /** Ends a task and announces what it changed; a task that ended or was stopped already stays as it is. */
    public TaskInfo endTask(String taskId) {
        AiTask task = requireTask(taskId);
        if (task.end()) {
            announce(listener -> listener.taskEnded(task.info(), task.changedElementIds()));
        }
        return task.info();
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

    private AiTask requireTask(String taskId) {
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
