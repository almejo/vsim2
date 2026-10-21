package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.TaskInfo;

import java.util.Objects;

/**
 * What ending a task did: the task, and how many guides it had added only to align went away with its end (spec §7).
 */
public record TaskEnding(TaskInfo task, int layoutGuidesRemoved) {

    public TaskEnding {
        Objects.requireNonNull(task, "task");
    }
}
