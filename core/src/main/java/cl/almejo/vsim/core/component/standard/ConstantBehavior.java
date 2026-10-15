package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

/** Drives a fixed value on its only pin at initialization (delay 0) and never again. */
final class ConstantBehavior implements Behavior {

    private final Value value;

    ConstantBehavior(Value value) {
        this.value = value;
    }

    @Override
    public void initialize(BehaviorContext context) {
        context.drive(0, value, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.ConstantState(value);
    }
}
