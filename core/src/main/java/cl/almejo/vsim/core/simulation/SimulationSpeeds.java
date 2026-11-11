package cl.almejo.vsim.core.simulation;

import java.util.List;

/** The simulation speeds a circuit can run at, in simulated units per real millisecond (the speed control's list). */
public final class SimulationSpeeds {

    public static final double DEFAULT = 1.0;
    public static final List<Double> AVAILABLE = List.of(0.1, 1.0, 10.0, 100.0, 1000.0, Double.POSITIVE_INFINITY);

    private SimulationSpeeds() {
    }
}
