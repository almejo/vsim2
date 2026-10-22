package cl.almejo.vsim.automation.edit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stage of a task's plan (spec §3): its title and its steps; a future stage may list no steps yet. */
public record TaskStage(String title, List<String> steps) {

    public TaskStage {
        Objects.requireNonNull(title, "title");
        steps = List.copyOf(steps);
    }

    /**
     * The index of the stage a step names: by its title (any case), else by its number from 1, else {@code fallback}.
     */
    public static int indexIn(List<TaskStage> stages, String stage, int fallback) {
        String wanted = stage.strip();
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).title().strip().equalsIgnoreCase(wanted)) {
                return index;
            }
        }
        Optional<Integer> number = numberOf(wanted);
        if (number.isPresent() && number.get() >= 1 && number.get() <= stages.size()) {
            return number.get() - 1;
        }
        return fallback;
    }

    /** {@code text} as a whole number; empty when it is not one. */
    public static Optional<Integer> numberOf(String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
