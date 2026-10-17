package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * «Contador» of N bits: pins D[N], LD, EN, CLR, CLK, Q[N], C. On the clock's falling edge, in priority order: LD = 1
 * loads D (all Z when D is undefined); else EN = 1 counts up, {@code Q + 1 mod 2^N} (a Z count stays Z). CLR = 1 clears
 * Q to 0 at once and wins over the clock. C («fin de cuenta») is 1 while Q is all ones and EN = 1, for chaining
 * counters; Z when Q or EN is undefined. Unconnected, LD and CLR count as 0 and EN as 1; a conflict on LD, EN or CLR
 * keeps the count. Starts at 0, also after «Reiniciar». Q and C follow {@code delay} later.
 */
final class CounterBehavior implements Behavior {

    static final int DATA = 0;
    static final int LOAD = 1;
    static final int ENABLE = 2;
    static final int CLEAR = 3;
    static final int CLOCK = 4;
    static final int OUTPUT = 5;
    static final int CARRY = 6;

    private final int width;
    private final int delay;
    private Value count;
    private Value carry = Value.ZERO;
    private Value previousClock = Value.HIGH_IMPEDANCE;

    CounterBehavior(int width, int delay) {
        this.width = width;
        this.delay = delay;
        this.count = Value.of(width, 0L);
    }

    @Override
    public void initialize(BehaviorContext context) {
        previousClock = context.input(CLOCK);
        // Power-on reset, like the flip-flop: Q is 0 before the first edge.
        context.drive(OUTPUT, count, 0);
        carry = carryFor(context);
        context.drive(CARRY, carry, 0);
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
        if (clear.isPresent() && clear.get()) {
            setCount(context, Value.of(width, 0L));
        } else if (clear.isPresent() && fallingEdge) {
            clockEdge(context);
        }
        Value newCarry = carryFor(context);
        if (!newCarry.equals(carry)) {
            carry = newCarry;
            context.drive(CARRY, carry, delay);
        }
    }

    private void clockEdge(BehaviorContext context) {
        Optional<Boolean> load = BlockSignals.control(context.input(LOAD), false);
        if (load.isEmpty()) {
            return;
        }
        if (load.get()) {
            Value data = context.input(DATA);
            setCount(context, data.isFullyDefined() ? data : BlockSignals.undefined(width));
            return;
        }
        Optional<Boolean> enable = BlockSignals.control(context.input(ENABLE), true);
        if (enable.isEmpty() || !enable.get() || !count.isFullyDefined()) {
            return;
        }
        setCount(context, BlockSignals.result(width, count.bits() + 1));
    }

    private void setCount(BehaviorContext context, Value value) {
        if (!value.equals(count)) {
            count = value;
            context.drive(OUTPUT, count, delay);
        }
    }

    /** 1 while the count is all ones and EN = 1; Z when either is undefined. */
    private Value carryFor(BehaviorContext context) {
        Optional<Boolean> enable = BlockSignals.control(context.input(ENABLE), true);
        if (enable.isEmpty() || !count.isFullyDefined()) {
            return BlockSignals.undefined(1);
        }
        return Value.of(enable.get() && count.bits() == Value.maskFor(width));
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.StoredValueState(count);
    }
}
