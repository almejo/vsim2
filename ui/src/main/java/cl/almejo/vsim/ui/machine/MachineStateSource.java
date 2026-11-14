package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.Optional;

/**
 * Where a machine tab reads the active state of the copy entered through: from the circuit's (unremapped) snapshot,
 * where a machine copy is one flat component named by its instance path ({@code c3}, {@code c3/c7}). A seam, so the
 * tab does not depend on how the simulation publishes it (spec 2026-11-14 §2.3: a {@code MachineVisibleState}).
 */
@FunctionalInterface
public interface MachineStateSource {

    /** The state of the machine copy at {@code instancePath} in {@code snapshot}; empty when it shows none. */
    Optional<ActiveMachineState> activeState(SimulationSnapshot snapshot, String instancePath);

    /**
     * The running simulation's: the copy's published visible state. Wired to {@code MachineVisibleState} when the
     * simulation of machines joins this branch (hito 1, task 3); until then no copy shows a state.
     */
    static MachineStateSource standard() {
        return (snapshot, instancePath) -> Optional.empty();
    }
}
