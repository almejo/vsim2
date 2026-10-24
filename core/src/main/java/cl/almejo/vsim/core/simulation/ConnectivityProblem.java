package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/** A structurally disconnected pin or loose wire end, with its instance path and local position. */
public record ConnectivityProblem(String netId, String elementId, int pinIndex, GridPoint position)
        implements Problem {

    public ConnectivityProblem {
        Objects.requireNonNull(netId, "netId");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(position, "position");
        if (pinIndex < -1) {
            throw new IllegalArgumentException("pin index must be nonnegative or -1 for a loose wire end");
        }
    }

    /** Whether this warns about a pin; a loose wire end has the documented sentinel index -1. */
    public boolean isPin() {
        return pinIndex >= 0;
    }
}
