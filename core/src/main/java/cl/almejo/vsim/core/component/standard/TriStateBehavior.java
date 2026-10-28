package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/**
 * Pin 0 input, pin 1 output, pin 2 enable (1 bit). Output is the input (inverted when {@code negated}) when enabled,
 * all Z otherwise; an enable that is Z or in conflict counts as off. The input and output are {@code width} bits.
 */
record TriStateBehavior(int width, boolean negated, int delay) implements Behavior {

    @Override
    public void initialize(BehaviorContext context) {
        inputChanged(context, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        context.drive(1, output(context.input(0), context.input(2)), delay);
    }

    private Value output(Value input, Value enable) {
        if (!enable.equals(Value.ONE)) {
            return Value.highImpedance(width);
        }
        return negated ? input.not() : input;
    }
}
