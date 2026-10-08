package cl.almejo.vsim.core.simulation;

import java.util.List;

/** Two or more drivers impose different values on the same net since {@code sinceTime}. */
public record ShortCircuit(String netId, List<DriverRef> drivers, long sinceTime) implements Problem {
    public ShortCircuit {
        drivers = List.copyOf(drivers);
    }
}
