package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

record NotBehavior(int delay) implements Behavior {

    @Override
    public void initialize(BehaviorContext context) {
        inputChanged(context, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        context.drive(1, context.input(0).not(), delay);
    }
}
