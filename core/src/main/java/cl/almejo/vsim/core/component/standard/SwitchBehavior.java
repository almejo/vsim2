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

    /** {@code "1"} is all ones, {@code "0"} all zeros, anything else all Z, {@code width} bits wide. */
    static Value parse(String text, int width) {
        return switch (text) {
            case "1" -> Value.of(width, Value.maskFor(width));
            case "0" -> Value.of(width, 0L);
            default -> Value.highImpedance(width);
        };
    }

    static Value parse(String text) {
        return parse(text, 1);
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

    /** Toggles between all ones and all zeros (Z or a mixed value becomes all ones). */
    @Override
    public void interact(BehaviorContext context) {
        Value allOnes = Value.of(value.width(), Value.maskFor(value.width()));
        value = value.equals(allOnes) ? Value.of(value.width(), 0L) : allOnes;
        context.drive(0, value, 0);
    }

    /** Drives {@code newValue} with delay 0; it must be as wide as the switch or pin (IllegalArgumentException). */
    @Override
    public boolean setValue(BehaviorContext context, Value newValue) {
        if (newValue.width() != value.width()) {
            throw new IllegalArgumentException("the input is " + value.width() + " bits wide, not "
                    + newValue.width());
        }
        value = newValue;
        context.drive(0, value, 0);
        return true;
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SwitchState(value);
    }
}
