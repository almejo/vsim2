package cl.almejo.vsim.core.machine;

import java.util.List;
import java.util.Objects;

/**
 * A state of a {@link StateMachine}: a stable {@code id} (never shown), the visible {@code name}, where it is drawn
 * and its Moore outputs.
 */
public record MachineState(String id, String name, DiagramPoint position, List<OutputAssignment> outputs) {

    public MachineState {
        StateMachine.requireId(id, "state");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("state " + id + " needs a name");
        }
        name = name.strip();
        Objects.requireNonNull(position, "position");
        outputs = List.copyOf(outputs);
    }

    public MachineState withName(String newName) {
        return new MachineState(id, newName, position, outputs);
    }

    public MachineState withPosition(DiagramPoint newPosition) {
        return new MachineState(id, name, newPosition, outputs);
    }

    public MachineState withOutputs(List<OutputAssignment> newOutputs) {
        return new MachineState(id, name, position, newOutputs);
    }
}
