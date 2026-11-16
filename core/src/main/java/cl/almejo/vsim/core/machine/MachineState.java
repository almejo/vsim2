package cl.almejo.vsim.core.machine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A state of a {@link StateMachine}: a stable {@code id} (never shown), the visible {@code name}, where it is drawn,
 * its Moore outputs and the fill the user chose for it (none by default).
 */
public record MachineState(String id, String name, DiagramPoint position, List<OutputAssignment> outputs,
                           Optional<StateColor> color) {

    public MachineState {
        StateMachine.requireId(id, "state");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("state " + id + " needs a name");
        }
        name = name.strip();
        Objects.requireNonNull(position, "position");
        outputs = List.copyOf(outputs);
        Objects.requireNonNull(color, "color");
    }

    /** A state without a colour of its own. */
    public MachineState(String id, String name, DiagramPoint position, List<OutputAssignment> outputs) {
        this(id, name, position, outputs, Optional.empty());
    }

    public MachineState withName(String newName) {
        return new MachineState(id, newName, position, outputs, color);
    }

    public MachineState withPosition(DiagramPoint newPosition) {
        return new MachineState(id, name, newPosition, outputs, color);
    }

    public MachineState withOutputs(List<OutputAssignment> newOutputs) {
        return new MachineState(id, name, position, newOutputs, color);
    }

    public MachineState withColor(Optional<StateColor> newColor) {
        return new MachineState(id, name, position, outputs, newColor);
    }
}
