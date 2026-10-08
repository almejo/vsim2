package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

final class SwitchBehavior implements Behavior {

    private Value value;

    SwitchBehavior(Value initialValue) {
        this.value = initialValue;
    }

    static Value parse(String text) {
        return switch (text) {
            case "1" -> Value.ONE;
            case "0" -> Value.ZERO;
            default -> Value.HIGH_IMPEDANCE;
        };
    }

    @Override
    public void initialize(BehaviorContext context) {
        context.drive(0, value, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public void interact(BehaviorContext context) {
        value = value.equals(Value.ONE) ? Value.ZERO : Value.ONE;
        context.drive(0, value, 0);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SwitchState(value);
    }
}
