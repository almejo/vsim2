package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * «Registro» of N bits: pins D[N], EN, CLR, CLK, Q[N]. On the clock's falling edge (ONE to ZERO, like the D
 * flip-flop) with EN = 1, Q takes D (all Z when D is undefined). CLR = 1 clears Q to 0 at once and wins over the clock.
 * Unconnected, EN counts as 1 and CLR as 0; a conflict on either keeps the stored value. Starts at 0, so «Reiniciar»
 * (which recreates the behaviour) brings it back to 0. Q follows the stored value {@code delay} later.
 */
final class RegisterBehavior implements Behavior {

    static final int DATA = 0;
    static final int ENABLE = 1;
    static final int CLEAR = 2;
    static final int CLOCK = 3;
    static final int OUTPUT = 4;

    private final int width;
    private final int delay;
    private Value stored;
    private Value previousClock = Value.HIGH_IMPEDANCE;

    RegisterBehavior(int width, int delay) {
        this.width = width;
        this.delay = delay;
        this.stored = Value.of(width, 0L);
    }

    @Override
    public void initialize(BehaviorContext context) {
        previousClock = context.input(CLOCK);
        // Power-on reset, like the flip-flop: Q is 0 before the first edge.
        context.drive(OUTPUT, stored, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean fallingEdge = false;
        if (pinIndex == CLOCK) {
            Value clock = context.input(CLOCK);
            fallingEdge = previousClock.equals(Value.ONE) && clock.equals(Value.ZERO);
            previousClock = clock;
        }
        Optional<Boolean> clear = BlockSignals.control(context.input(CLEAR), false);
        if (clear.isEmpty()) {
            return;
        }
        if (clear.get()) {
            store(context, Value.of(width, 0L));
            return;
        }
        if (!fallingEdge) {
            return;
        }
        Optional<Boolean> enable = BlockSignals.control(context.input(ENABLE), true);
        if (enable.isEmpty() || !enable.get()) {
            return;
        }
        Value data = context.input(DATA);
        store(context, data.isFullyDefined() ? data : BlockSignals.undefined(width));
    }

    private void store(BehaviorContext context, Value value) {
        if (value.equals(stored)) {
            return;
        }
        stored = value;
        context.drive(OUTPUT, stored, delay);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.StoredValueState(stored);
    }
}
