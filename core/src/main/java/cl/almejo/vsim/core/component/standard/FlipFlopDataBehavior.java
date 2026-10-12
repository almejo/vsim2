package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/**
 * D flip-flop: pin 0 D, pin 1 Q, pin 2 clock. Latches D on a falling edge (ONE to ZERO). A new flip-flop drives
 * Q = 0 at once (power-on reset, as in teaching simulators), so a toggle (D = NOT Q) or a ripple counter starts.
 */
final class FlipFlopDataBehavior implements Behavior {

    private static final int DATA = 0;
    private static final int OUTPUT = 1;
    private static final int CLOCK = 2;

    private final int delay;
    private Value previousClock = Value.HIGH_IMPEDANCE;

    FlipFlopDataBehavior(int delay) {
        this.delay = delay;
    }

    @Override
    public void initialize(BehaviorContext context) {
        previousClock = context.input(CLOCK);
        // Power-on reset: Q is 0 before the first falling edge (an unconnected input still reads Z).
        context.drive(OUTPUT, Value.ZERO, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        if (pinIndex != CLOCK) {
            return;
        }
        Value clock = context.input(CLOCK);
        if (previousClock.equals(Value.ONE) && clock.equals(Value.ZERO)) {
            context.drive(OUTPUT, context.input(DATA), delay);
        }
        previousClock = clock;
    }
}
