package cl.almejo.vsim.automation;

import java.util.Set;

/** Hears the AI's tasks begin and end; the editor shows «IA trabajando…» with them. Called off the editor thread. */
public interface AutomationListener {

    void taskStarted(TaskInfo task);

    /** The task ended normally; {@code changedElementIds} are the elements it added or changed. */
    void taskEnded(TaskInfo task, Set<String> changedElementIds);

    /** The user stopped the task («Detener»): what it applied stays, the rest is dropped. */
    void taskStopped(TaskInfo task);
}
