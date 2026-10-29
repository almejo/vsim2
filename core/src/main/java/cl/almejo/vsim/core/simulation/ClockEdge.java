package cl.almejo.vsim.core.simulation;

import java.util.Optional;

/**
 * The clock edge an edge step stopped at: a clock's output went from 0 to 1 ({@link #RISING}), from 1 to 0
 * ({@link #FALLING}), or two clocks changed opposite ways at the same time ({@link #BOTH}). Only a direct step between
 * 0 and 1 counts, as for the flip-flops.
 */
public enum ClockEdge {
    RISING,
    FALLING,
    BOTH;

    /** The edge made of the rises and falls seen at one time; empty when neither happened. */
    static Optional<ClockEdge> of(boolean rose, boolean fell) {
        if (rose && fell) {
            return Optional.of(BOTH);
        }
        if (rose) {
            return Optional.of(RISING);
        }
        return fell ? Optional.of(FALLING) : Optional.empty();
    }
}
