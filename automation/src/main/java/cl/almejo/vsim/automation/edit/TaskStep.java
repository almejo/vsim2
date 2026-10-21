package cl.almejo.vsim.automation.edit;

import java.util.Objects;

/**
 * Where an edit belongs in its task (spec §3): the task, the stage and step it carries out, and the one-line text of
 * the step («Conecto la realimentación»). IA.1 only keeps it; IA.2 shows it in the panel and the callout.
 */
public record TaskStep(String taskId, String stage, String step, String text) {

    public TaskStep {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(text, "text");
    }
}
