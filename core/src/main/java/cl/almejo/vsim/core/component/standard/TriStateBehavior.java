package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/** Pin 0 input, pin 1 output, pin 2 enable. Output is the input when enabled, Z otherwise. */
record TriStateBehavior(int delay) implements Behavior {

    @Override
    public void initialize(BehaviorContext context) {
        inputChanged(context, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        Value output = context.input(2).equals(Value.ONE) ? context.input(0) : Value.HIGH_IMPEDANCE;
        context.drive(1, output, delay);
    }
}
