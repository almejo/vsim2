package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.VisibleState;
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

    /** The running simulation's: the copy's published {@code MachineVisibleState}. */
    static MachineStateSource standard() {
        return (snapshot, instancePath) -> snapshot.componentStates().get(instancePath) instanceof
                VisibleState.MachineVisibleState state
                ? Optional.of(new ActiveMachineState(state.stateId(), state.nextStateId(), state.nextTransitionId()))
                : Optional.empty();
    }
}
