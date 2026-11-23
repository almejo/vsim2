package cl.almejo.vsim.core.machine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An arrow of a {@link StateMachine} from {@code fromId} to {@code toId} (the same id for a self loop). The
 * {@code condition} is kept as typed, in the notation of «Circuito desde expresiones»; empty means «otherwise».
 * {@code outputs} are its Mealy outputs. {@code loopSide} is where a self loop is drawn when the user chose it (empty:
 * the placement every loop had before).
 */
public record MachineTransition(String id, String fromId, String toId, String condition,
                                List<OutputAssignment> outputs, Optional<LoopSide> loopSide) {

    public MachineTransition {
        StateMachine.requireId(id, "transition");
        StateMachine.requireId(fromId, "transition source");
        StateMachine.requireId(toId, "transition target");
        condition = condition == null ? "" : condition.strip();
        outputs = List.copyOf(outputs);
        Objects.requireNonNull(loopSide, "loopSide");
    }

    /** An arrow with no chosen loop side. */
    public MachineTransition(String id, String fromId, String toId, String condition,
                             List<OutputAssignment> outputs) {
        this(id, fromId, toId, condition, outputs, Optional.empty());
    }

    public boolean isOtherwise() {
        return condition.isEmpty();
    }

    public boolean isSelfLoop() {
        return fromId.equals(toId);
    }

    public MachineTransition withCondition(String newCondition) {
        return new MachineTransition(id, fromId, toId, newCondition, outputs, loopSide);
    }

    public MachineTransition withOutputs(List<OutputAssignment> newOutputs) {
        return new MachineTransition(id, fromId, toId, condition, newOutputs, loopSide);
    }

    /** The same arrow with its loop drawn on {@code side} (empty: the placement of old files); no meaning off a loop. */
    public MachineTransition withLoopSide(Optional<LoopSide> side) {
        return new MachineTransition(id, fromId, toId, condition, outputs, side);
    }

    public MachineTransition withEnds(String newFromId, String newToId) {
        return new MachineTransition(id, Objects.requireNonNull(newFromId), Objects.requireNonNull(newToId),
                condition, outputs);
    }
}
