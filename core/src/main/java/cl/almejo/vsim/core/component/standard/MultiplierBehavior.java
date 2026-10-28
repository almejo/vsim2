package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Multiplicador» of N bits: pins A[N], B[N], P[N], H[N]. The product has 2N bits, read with or without sign; P is its
 * low half and H its high half. Undefined A or B drive P and H all Z ({@link BlockSignals}).
 */
final class MultiplierBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int LOW = 2;
    static final int HIGH = 3;

    private final int width;
    private final boolean signed;
    private final int delay;

    MultiplierBehavior(int width, boolean signed, int delay) {
        this.width = width;
        this.signed = signed;
        this.delay = delay;
    }

    @Override
    public void initialize(BehaviorContext context) {
        evaluate(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        evaluate(context);
    }

    private OptionalLong operand(Value value) {
        return signed ? BlockSignals.signed(value) : BlockSignals.unsigned(value);
    }

    private void evaluate(BehaviorContext context) {
        OptionalLong first = operand(context.input(A));
        OptionalLong second = operand(context.input(B));
        if (first.isEmpty() || second.isEmpty()) {
            context.drive(LOW, BlockSignals.undefined(width), delay);
            context.drive(HIGH, BlockSignals.undefined(width), delay);
            return;
        }
        // 64 bits hold the 2N-bit product as a bit pattern, also the unsigned 32 x 32 one (it wraps in a long).
        long product = first.getAsLong() * second.getAsLong();
        context.drive(LOW, BlockSignals.result(width, product), delay);
        context.drive(HIGH, BlockSignals.result(width, product >>> width), delay);
    }
}
