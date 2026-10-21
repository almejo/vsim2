package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.UndoOutcome;

import java.util.Objects;

/**
 * The AI's tasks as the editor sees them: their events, «Detener» and «Deshacer lo que hizo la IA». The automation
 * layer implements them ({@link #of}); tests give a fake. {@link #stopTask} and {@link #undoTask} may wait for the
 * editor thread, so the editor calls them off it.
 */
public interface AiTasks {

    /** {@code listener} hears every task begin and end (off the EDT) until the returned runnable takes it back. */
    Runnable addListener(AutomationListener listener);

    void stopTask(String taskId);

    UndoOutcome undoTask(String taskId);

    static AiTasks of(AutomationApi api) {
        Objects.requireNonNull(api, "api");
        return new AiTasks() {
            @Override
            public Runnable addListener(AutomationListener listener) {
                return api.addListener(listener);
            }

            @Override
            public void stopTask(String taskId) {
                api.stopTask(taskId);
            }

            @Override
            public UndoOutcome undoTask(String taskId) {
                return api.undoTask(taskId);
            }
        };
    }
}
