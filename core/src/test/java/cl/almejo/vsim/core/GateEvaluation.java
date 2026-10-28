package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a combinational component's behaviour once, without the engine: fixed values on its inputs, the value it drives
 * on its last pin (a gate's output) back. Fast enough to check every row of an 8-input table.
 */
public final class GateEvaluation {

    private GateEvaluation() {
    }

    /** What {@code type} (specialized for {@code parameters}) drives on its last pin with {@code inputs} on the others. */
    public static Value output(ComponentType type, Map<String, Object> parameters, List<Value> inputs) {
        ComponentType specialized = type.specializedFor(parameters);
        Behavior behavior = specialized.createBehavior(parameters);
        RecordingContext context = new RecordingContext(inputs);
        behavior.initialize(context);
        return context.driven.get(specialized.pinCount() - 1);
    }

    /** A context whose inputs are fixed and which remembers what was driven on each pin. */
    private static final class RecordingContext implements BehaviorContext {

        private final List<Value> inputs;
        private final Map<Integer, Value> driven = new HashMap<>();

        RecordingContext(List<Value> inputs) {
            this.inputs = inputs;
        }

        @Override
        public Value input(int pinIndex) {
            return inputs.get(pinIndex);
        }

        @Override
        public void drive(int pinIndex, Value value, long delay) {
            driven.put(pinIndex, value);
        }

        @Override
        public void schedule(long delay, Runnable action) {
            throw new UnsupportedOperationException("a combinational component schedules nothing");
        }

        @Override
        public long now() {
            return 0;
        }
    }
}
