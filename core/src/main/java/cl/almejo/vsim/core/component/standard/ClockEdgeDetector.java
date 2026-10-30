package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;

import java.util.Map;

/**
 * Sees the clock edge a part acts on ({@link StandardParameters#EDGE}): only a direct step between 0 and 1 counts, as
 * in {@code RegisterBehavior}, so a clock that goes through Z or a conflict makes no edge. A behaviour keeps one,
 * {@linkplain #start starts} it with the clock's value in {@code initialize} and {@linkplain #step steps} it each time
 * the clock pin changes. Called on the simulation thread only.
 */
public final class ClockEdgeDetector {

    private final boolean rising;
    private Value previous = Value.HIGH_IMPEDANCE;

    private ClockEdgeDetector(boolean rising) {
        this.rising = rising;
    }

    /**
     * A detector of {@code edge}, {@link StandardParameters#RISING} or {@link StandardParameters#FALLING}.
     *
     * @throws IllegalArgumentException for any other value
     */
    public static ClockEdgeDetector forEdge(String edge) {
        return switch (edge) {
            case StandardParameters.RISING -> new ClockEdgeDetector(true);
            case StandardParameters.FALLING -> new ClockEdgeDetector(false);
            default -> throw new IllegalArgumentException("unknown clock edge: " + edge);
        };
    }

    /** A detector of the edge the resolved {@code parameters} choose with {@link StandardParameters#EDGE}. */
    public static ClockEdgeDetector of(Map<String, Object> parameters) {
        return forEdge(String.valueOf(parameters.getOrDefault(StandardParameters.EDGE.name(),
                StandardParameters.EDGE.defaultValue())));
    }

    /** The clock's value when the part starts: what the first step is compared with. */
    public void start(Value clock) {
        previous = clock;
    }

    /** Whether the clock going from its last value to {@code clock} is the chosen edge; remembers {@code clock}. */
    public boolean step(Value clock) {
        Value before = previous;
        previous = clock;
        return rising ? before.equals(Value.ZERO) && clock.equals(Value.ONE)
                : before.equals(Value.ONE) && clock.equals(Value.ZERO);
    }
}
