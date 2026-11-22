package cl.almejo.vsim.core.simulation;

import java.util.List;
import java.util.Objects;

/**
 * The engine reached one of its limits at simulation time {@code time}: what happened is the bundle key
 * {@code messageKey} with {@code arguments}, so the simulation thread pauses with a {@link PauseNotice} in the user's
 * words instead of an English failure. An {@link IllegalStateException}, as these limits were before.
 */
public final class SimulationLimitException extends IllegalStateException {

    /** A part scheduled an event past the largest time the simulation can reach ({@code Long.MAX_VALUE}). */
    public static final String TIME_OVERFLOW = "simulation.limit.timeOverflow";
    /** One time ran more events than a step allows: a zero-delay loop never lets the time move. */
    public static final String TOO_MANY_EVENTS = "simulation.limit.tooManyEvents";

    private final String messageKey;
    private final List<String> arguments;
    private final long time;

    SimulationLimitException(String messageKey, List<String> arguments, long time, String englishMessage) {
        super(englishMessage);
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        this.arguments = List.copyOf(arguments);
        this.time = time;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<String> arguments() {
        return arguments;
    }

    /** The simulation time at which the limit was reached. */
    public long time() {
        return time;
    }
}
