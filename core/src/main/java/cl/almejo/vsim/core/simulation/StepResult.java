package cl.almejo.vsim.core.simulation;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What one «Avanzar» or one click on a switch while paused did (M6.1-B): the simulation went from {@code fromTime} to
 * {@code time}, the nets in {@code changedNets} changed value (each net id with its aliases, so the top-level ids of a
 * net joined through a template are there too), and {@code nextEventTime} is the earliest event still pending (empty
 * when the circuit is stable).
 */
public record StepResult(long fromTime, long time, Set<String> changedNets, OptionalLong nextEventTime) {

    public StepResult {
        changedNets = Set.copyOf(changedNets);
        Objects.requireNonNull(nextEventTime, "nextEventTime");
    }

    /** Nothing was pending: no event ran and the time stayed at {@code now}. */
    public static StepResult idle(long now) {
        return new StepResult(now, now, Set.of(), OptionalLong.empty());
    }

    /** True when nothing ran, nothing changed and nothing is pending. */
    public boolean isIdle() {
        return changedNets.isEmpty() && time == fromTime && nextEventTime.isEmpty();
    }
}
