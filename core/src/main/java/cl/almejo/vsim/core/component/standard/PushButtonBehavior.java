package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

/**
 * «Pulsador»: drives 0 from the start and 1 only while it is held. The editor presses and releases it through
 * {@link #setValue} (never a click: a click toggles, a press does not), and so do a truth-table row tried in the
 * circuit and the AI's inputs, which leave it at their value until the next press and release or a restart. Its state
 * is a {@link VisibleState.SwitchState}, so the tree, the restart and the readouts treat it as they treat a switch.
 */
final class PushButtonBehavior implements Behavior {

    private Value value = Value.ZERO;

    @Override
    public void initialize(BehaviorContext context) {
        context.drive(0, value, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    /** Drives {@code newValue} with delay 0; it must be one bit wide (IllegalArgumentException). */
    @Override
    public boolean setValue(BehaviorContext context, Value newValue) {
        if (newValue.width() != 1) {
            throw new IllegalArgumentException("the push button is 1 bit wide, not " + newValue.width());
        }
        value = newValue;
        context.drive(0, value, 0);
        return true;
    }

    @Override
    public boolean keepsValueOnRestart() {
        return false;
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.SwitchState(value);
    }
}
