package cl.almejo.vsim.automation.edit;

import java.util.Objects;
import java.util.Optional;

/**
 * Where an edit belongs in its task (spec §3): the task, the stage and step it carries out, the one-line text of
 * the step («Conecto la realimentación») and, optionally, why («para que el sumador recuerde el acarreo»). IA.2
 * shows them in the panel and the callout.
 */
public record TaskStep(String taskId, String stage, String step, String text, Optional<String> reason) {

    public TaskStep {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reason, "reason");
    }

    /** A step without a reason. */
    public TaskStep(String taskId, String stage, String step, String text) {
        this(taskId, stage, step, text, Optional.empty());
    }
}
