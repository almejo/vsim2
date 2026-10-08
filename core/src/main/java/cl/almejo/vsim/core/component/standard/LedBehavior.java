package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

final class LedBehavior implements Behavior {

    private Value value = Value.HIGH_IMPEDANCE;

    @Override
    public void initialize(BehaviorContext context) {
        value = context.input(0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        value = context.input(0);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.LedState(value);
    }
}
