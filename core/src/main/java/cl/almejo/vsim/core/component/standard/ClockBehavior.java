package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

/** Square wave: low for time-down units, high for time-up units, starting low. */
final class ClockBehavior implements Behavior {

    private final int timeUp;
    private final int timeDown;
    private Value state = Value.ZERO;

    ClockBehavior(int timeUp, int timeDown) {
        this.timeUp = timeUp;
        this.timeDown = timeDown;
    }

    @Override
    public void initialize(BehaviorContext context) {
        context.drive(0, state, 0);
        context.schedule(timeDown, () -> toggle(context));
    }

    private void toggle(BehaviorContext context) {
        state = state.not();
        context.drive(0, state, 0);
        context.schedule(state.equals(Value.ONE) ? timeUp : timeDown, () -> toggle(context));
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.ClockState(state);
    }
}
