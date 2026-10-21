package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** The AI's tasks without an automation layer: a test fires the events and reads what the editor asked for. */
final class FakeAiTasks implements AiTasks {

    final List<AutomationListener> listeners = new CopyOnWriteArrayList<>();
    final List<String> stopped = new ArrayList<>();
    final List<String> undone = new ArrayList<>();
    UndoOutcome nextOutcome = new UndoOutcome(0, false);

    @Override
    public Runnable addListener(AutomationListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void stopTask(String taskId) {
        stopped.add(taskId);
    }

    @Override
    public UndoOutcome undoTask(String taskId) {
        undone.add(taskId);
        return nextOutcome;
    }

    static TaskInfo task(String taskId, DocumentId document, String title) {
        return new TaskInfo(taskId, document, title);
    }

    void start(TaskInfo task) {
        listeners.forEach(listener -> listener.taskStarted(task));
    }

    void end(TaskInfo task, Set<String> changedElementIds) {
        listeners.forEach(listener -> listener.taskEnded(task, changedElementIds));
    }

    void stop(TaskInfo task) {
        listeners.forEach(listener -> listener.taskStopped(task));
    }
}
