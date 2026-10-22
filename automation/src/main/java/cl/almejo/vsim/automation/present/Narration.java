package cl.almejo.vsim.automation.present;

import java.util.Objects;

/**
 * One line the AI says about what comes and why (IA.3, {@code vsim_say}): «Presentar» shows it as a subtitle and in
 * the current stage's card; in working mode only the status line keeps it. {@code stageIndex} is the stage the task
 * is on (from 0), {@code presenting} whether the window presents the task.
 */
public record Narration(String taskId, int stageIndex, String text, boolean presenting) {

    public Narration {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(text, "text");
    }
}
