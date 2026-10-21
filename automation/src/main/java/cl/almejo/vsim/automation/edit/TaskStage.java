package cl.almejo.vsim.automation.edit;

import java.util.List;
import java.util.Objects;

/** One stage of a task's plan (spec §3): its title and its steps; a future stage may list no steps yet. */
public record TaskStage(String title, List<String> steps) {

    public TaskStage {
        Objects.requireNonNull(title, "title");
        steps = List.copyOf(steps);
    }
}
