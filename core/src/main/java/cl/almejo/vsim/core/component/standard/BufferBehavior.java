package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/** The Buffer: copies its input to its output after the delay; negated, it is the NOT. Any width. */
record BufferBehavior(boolean negated, int delay) implements Behavior {

    @Override
    public void initialize(BehaviorContext context) {
        inputChanged(context, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        Value input = context.input(0);
        context.drive(1, negated ? input.not() : input, delay);
    }
}
