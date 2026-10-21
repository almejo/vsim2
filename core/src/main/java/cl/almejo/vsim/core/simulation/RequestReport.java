package cl.almejo.vsim.core.simulation;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * The receipt of one request with an event budget (IA.1, A§7): built on the simulation thread right after the request
 * ran, so its parts belong together — the {@code snapshot} (time, running, failure, token of the circuit it ran on),
 * the {@code nextEventTime} then, the time it started from, how many events it ran and, for
 * {@link SimulationThread#setInputsWithin}, what happened to each input in the order asked.
 */
public record RequestReport(Status status, SimulationSnapshot snapshot, OptionalLong nextEventTime, long fromTime,
                            long eventsProcessed, List<InputOutcome> inputs) {

    public enum Status {
        /** The request ran to its end. */
        COMPLETED,
        /** The next event would have gone over the budget: stopped at the time reached and paused. */
        BUDGET_EXCEEDED,
        /** A step while the simulation runs: nothing ran (pause first). */
        REFUSED_WHILE_RUNNING,
        /** The time asked for is past the largest simulation time: nothing ran. */
        REFUSED_TIME_OVERFLOW,
        /** The engine failed: paused, the failure in the snapshot, as for the user's own steps. */
        FAILED
    }

    public RequestReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(nextEventTime, "nextEventTime");
        inputs = List.copyOf(inputs);
    }
}
