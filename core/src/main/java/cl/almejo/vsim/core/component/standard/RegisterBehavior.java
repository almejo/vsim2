package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * «Registro» of N bits: pins D[N], EN, CLR, CLK, Q[N]. «Disparo» says when it loads D: on the clock's falling edge
 * (ONE to ZERO, like the D flip-flop; the default), on the rising edge, or, as a transparent latch, all the while the
 * clock is high or low: then every change of D (or of CLK or EN into the active state) loads D. Loading needs EN = 1
 * and stores D (all Z when D is undefined). CLR = 1 clears Q to 0 at once and wins over everything. Unconnected, EN
 * counts as 1 and CLR as 0; a conflict on either keeps the stored value. Starts at 0, so «Reiniciar» (which recreates
 * the behaviour) brings it back to 0. Q follows the stored value {@code delay} later.
 */
final class RegisterBehavior implements Behavior {

    static final int DATA = 0;
    static final int ENABLE = 1;
    static final int CLEAR = 2;
    static final int CLOCK = 3;
    static final int OUTPUT = 4;

    private final int width;
    private final int delay;
    /** The edge it loads on, or null for a latch. */
    private final ClockEdgeDetector edge;
    /** The clock value that opens a latch; unused for an edge. */
    private final Value openLevel;
    private Value stored;

    RegisterBehavior(int width, int delay) {
        this(width, delay, StandardParameters.FALLING);
    }

    /** {@code trigger} is a «Disparo» value: {@code falling}, {@code rising}, {@code high} or {@code low}. */
    RegisterBehavior(int width, int delay, String trigger) {
        this.width = width;
        this.delay = delay;
        boolean latch = StandardComponentTypes.TRIGGER_HIGH.equals(trigger)
                || StandardComponentTypes.TRIGGER_LOW.equals(trigger);
        this.edge = latch ? null : ClockEdgeDetector.forEdge(trigger);
        this.openLevel = StandardComponentTypes.TRIGGER_HIGH.equals(trigger) ? Value.ONE : Value.ZERO;
        this.stored = Value.of(width, 0L);
    }

    @Override
    public void initialize(BehaviorContext context) {
        if (edge != null) {
            edge.start(context.input(CLOCK));
        }
        // Power-on reset, like the flip-flop: Q is 0 before the first edge.
        context.drive(OUTPUT, stored, 0);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean loads;
        if (edge == null) {
            loads = context.input(CLOCK).equals(openLevel);
        } else {
            loads = pinIndex == CLOCK && edge.step(context.input(CLOCK));
        }
        Optional<Boolean> clear = BlockSignals.control(context.input(CLEAR), false);
        if (clear.isEmpty()) {
            return;
        }
        if (clear.get()) {
            store(context, Value.of(width, 0L));
            return;
        }
        if (!loads) {
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
