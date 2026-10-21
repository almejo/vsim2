package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.TaskInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One AI task (spec §3): its title, its plan of stages and steps, whether it still runs, and the edits it applied,
 * so «Deshacer lo que hizo la IA» can take them back and its end can say what it changed. Thread-safe: the server's
 * threads begin, edit and end it while «Detener» stops it from the editor.
 */
public final class AiTask {

    /** Where a task is: running until it ends or the user stops it. */
    public enum State { RUNNING, ENDED, STOPPED }

    private final TaskInfo info;
    private List<TaskStage> stages;
    private State state = State.RUNNING;
    private final List<AiEditCommand> edits = new ArrayList<>();
    private final Set<String> changedElementIds = new LinkedHashSet<>();

    AiTask(TaskInfo info, List<TaskStage> stages) {
        this.info = Objects.requireNonNull(info, "info");
        this.stages = List.copyOf(stages);
    }

    public TaskInfo info() {
        return info;
    }

    public synchronized List<TaskStage> stages() {
        return stages;
    }

    public synchronized State state() {
        return state;
    }

    synchronized void replacePlan(List<TaskStage> newStages) {
        stages = List.copyOf(newStages);
    }

    /** Ends a running task; false, changing nothing, when it had ended or was stopped already. */
    synchronized boolean end() {
        return leave(State.ENDED);
    }

    /** Stops a running task («Detener»); false when it had ended or was stopped already. */
    synchronized boolean stop() {
        return leave(State.STOPPED);
    }

    private boolean leave(State next) {
        if (state != State.RUNNING) {
            return false;
        }
        state = next;
        return true;
    }

    /** Records an applied edit and the elements it added, changed and removed. */
    synchronized void recordEdit(AiEditCommand edit, EditReceipt.ElementChanges changes) {
        edits.add(edit);
        changedElementIds.addAll(changes.added());
        changedElementIds.addAll(changes.changed());
        changes.removed().forEach(changedElementIds::remove);
    }

    /** True while some edit of this task is still in the document (not undone). Editor thread only. */
    synchronized boolean hasAppliedEdits() {
        return edits.stream().anyMatch(AiEditCommand::isApplied);
    }

    /** The elements the task added or changed and that it did not remove afterwards. */
    synchronized Set<String> changedElementIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(changedElementIds));
    }
}
