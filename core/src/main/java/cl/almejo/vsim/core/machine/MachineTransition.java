package cl.almejo.vsim.core.machine;

import java.util.List;
import java.util.Objects;

/**
 * An arrow of a {@link StateMachine} from {@code fromId} to {@code toId} (the same id for a self loop). The
 * {@code condition} is kept as typed, in the notation of «Circuito desde expresiones»; empty means «otherwise».
 * {@code outputs} are its Mealy outputs.
 */
public record MachineTransition(String id, String fromId, String toId, String condition,
                                List<OutputAssignment> outputs) {

    public MachineTransition {
        StateMachine.requireId(id, "transition");
        StateMachine.requireId(fromId, "transition source");
        StateMachine.requireId(toId, "transition target");
        condition = condition == null ? "" : condition.strip();
        outputs = List.copyOf(outputs);
    }

    public boolean isOtherwise() {
        return condition.isEmpty();
    }

    public boolean isSelfLoop() {
        return fromId.equals(toId);
    }

    public MachineTransition withCondition(String newCondition) {
        return new MachineTransition(id, fromId, toId, newCondition, outputs);
    }

    public MachineTransition withOutputs(List<OutputAssignment> newOutputs) {
        return new MachineTransition(id, fromId, toId, condition, newOutputs);
    }

    public MachineTransition withEnds(String newFromId, String newToId) {
        return new MachineTransition(id, Objects.requireNonNull(newFromId), Objects.requireNonNull(newToId),
                condition, outputs);
    }
}
