package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.machine.MachineConditions.ParsedCondition;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a machine does on a clock edge: from a state and the inputs, the first conditional arrow that holds (in model
 * order), else the «otherwise» arrow, else it stays. Outputs are the state's Moore outputs plus the Mealy outputs of
 * the arrow the inputs select; an output nobody assigns is 0. Inputs without a value count as 0.
 * <p>
 * The static methods read the conditions each time; {@link #of} reads them once for a machine that evaluates many
 * edges (the simulation).
 */
public final class MachineEvaluator {

    /**
     * One edge: the state after it, the arrow taken (empty when the machine stays for lack of one) and the outputs
     * before the edge (Moore of the current state, Mealy of the arrow taken).
     */
    public record MachineStep(String nextStateId, Optional<String> transitionId, Map<String, Boolean> outputs) {

        public MachineStep {
            Objects.requireNonNull(nextStateId, "nextStateId");
            Objects.requireNonNull(transitionId, "transitionId");
            outputs = Map.copyOf(outputs);
        }
    }

    private final StateMachine machine;
    private final Map<String, ParsedCondition> conditions = new HashMap<>();

    private MachineEvaluator(StateMachine machine) {
        this.machine = machine;
        for (MachineTransition transition : machine.transitions()) {
            conditions.put(transition.id(), MachineConditions.parse(transition.condition()));
        }
    }

    /** An evaluator of {@code machine} with its conditions read once. */
    public static MachineEvaluator of(StateMachine machine) {
        return new MachineEvaluator(Objects.requireNonNull(machine, "machine"));
    }

    /** The step from {@code stateId} with {@code inputs}; empty when the machine has no such state. */
    public static Optional<MachineStep> next(StateMachine machine, String stateId, Map<String, Boolean> inputs) {
        return of(machine).next(stateId, inputs);
    }

    /** The outputs while in {@code stateId} with {@code inputs} (every output name of the machine, 0 by default). */
    public static Map<String, Boolean> outputs(StateMachine machine, String stateId, Map<String, Boolean> inputs) {
        return of(machine).outputs(stateId, inputs);
    }

    public StateMachine machine() {
        return machine;
    }

    /** See {@link #next(StateMachine, String, Map)}. */
    public Optional<MachineStep> next(String stateId, Map<String, Boolean> inputs) {
        if (machine.state(stateId).isEmpty()) {
            return Optional.empty();
        }
        Optional<MachineTransition> taken = arrowTaken(stateId, inputs);
        String nextStateId = taken.map(MachineTransition::toId).orElse(stateId);
        return Optional.of(new MachineStep(nextStateId, taken.map(MachineTransition::id),
                outputs(stateId, taken)));
    }

    /** See {@link #outputs(StateMachine, String, Map)}; empty values (all 0) for an unknown state. */
    public Map<String, Boolean> outputs(String stateId, Map<String, Boolean> inputs) {
        if (machine.state(stateId).isEmpty()) {
            return outputs(stateId, Optional.empty());
        }
        return outputs(stateId, arrowTaken(stateId, inputs));
    }

    private Optional<MachineTransition> arrowTaken(String stateId, Map<String, Boolean> inputs) {
        List<MachineTransition> leaving = machine.transitionsFrom(stateId);
        for (MachineTransition transition : leaving) {
            if (conditions.get(transition.id()).holds(inputs)) {
                return Optional.of(transition);
            }
        }
        return leaving.stream().filter(transition -> conditions.get(transition.id()).isOtherwise()).findFirst();
    }

    private Map<String, Boolean> outputs(String stateId, Optional<MachineTransition> taken) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String output : machine.outputNames()) {
            values.put(output, false);
        }
        machine.state(stateId).ifPresent(state -> assign(values, state.outputs()));
        taken.ifPresent(transition -> assign(values, transition.outputs()));
        return Collections.unmodifiableMap(values);
    }

    private static void assign(Map<String, Boolean> values, List<OutputAssignment> assignments) {
        for (OutputAssignment assignment : assignments) {
            values.put(assignment.output(), assignment.value());
        }
    }
}
