package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Divisor» of N bits: pins A[N] (dividend), B[N] (divisor), Q[N], R[N], E. Signed division truncates toward zero and
 * the remainder takes the dividend's sign (Java and C); the minimum divided by -1 wraps to the minimum. {@code B = 0}
 * gives {@code Q = 0}, {@code R = A} and {@code E = 1}; otherwise E is 0. Undefined A or B drive every output Z
 * ({@link BlockSignals}).
 */
final class DividerBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int QUOTIENT = 2;
    static final int REMAINDER = 3;
    static final int ERROR = 4;

    private final int width;
    private final boolean signed;
    private final int delay;

    DividerBehavior(int width, boolean signed, int delay) {
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
        OptionalLong dividend = operand(context.input(A));
        OptionalLong divisor = operand(context.input(B));
        if (dividend.isEmpty() || divisor.isEmpty()) {
            context.drive(QUOTIENT, BlockSignals.undefined(width), delay);
            context.drive(REMAINDER, BlockSignals.undefined(width), delay);
            context.drive(ERROR, BlockSignals.undefined(1), delay);
            return;
        }
        boolean byZero = divisor.getAsLong() == 0;
        long quotient = byZero ? 0 : dividend.getAsLong() / divisor.getAsLong();
        long remainder = byZero ? dividend.getAsLong() : dividend.getAsLong() % divisor.getAsLong();
        context.drive(QUOTIENT, BlockSignals.result(width, quotient), delay);
        context.drive(REMAINDER, BlockSignals.result(width, remainder), delay);
        context.drive(ERROR, Value.of(byZero), delay);
    }
}
