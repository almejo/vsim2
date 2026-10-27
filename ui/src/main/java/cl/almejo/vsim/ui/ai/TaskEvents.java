package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/** The automation layer's events, off the EDT: moved to the EDT. */
final class TaskEvents implements AutomationListener {

    private final Executor editorThread;
    private final Map<String, TaskInfo> runningTasks;
    private final List<AiServerService.Listener> listeners;
    private final Runnable notifyStateChanged;

    TaskEvents(Executor editorThread, Map<String, TaskInfo> runningTasks, List<AiServerService.Listener> listeners,
               Runnable notifyStateChanged) {
        this.editorThread = editorThread;
        this.runningTasks = runningTasks;
        this.listeners = listeners;
        this.notifyStateChanged = notifyStateChanged;
    }

    @Override
    public void taskStarted(TaskInfo task) {
        editorThread.execute(() -> {
            runningTasks.put(task.taskId(), task);
            listeners.forEach(listener -> listener.taskStarted(task));
            notifyStateChanged.run();
        });
    }

    @Override
    public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
        Set<String> changed = Set.copyOf(changedElementIds);
        editorThread.execute(() -> {
            runningTasks.remove(task.taskId());
            listeners.forEach(listener -> listener.taskEnded(task, changed));
            notifyStateChanged.run();
        });
    }

    @Override
    public void taskStopped(TaskInfo task) {
        taskStopped(task, TaskStopReason.USER);
    }

    @Override
    public void taskStopped(TaskInfo task, TaskStopReason reason) {
        editorThread.execute(() -> {
            runningTasks.remove(task.taskId());
            listeners.forEach(listener -> listener.taskStopped(task, reason));
            notifyStateChanged.run();
        });
    }
}
