package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * «Sumador» of N bits: pins A[N], B[N], Cin, S[N], Cout. {@code S = (A + B + Cin) mod 2^N} and Cout is the carry out of
 * bit N-1, both {@code delay} after any input changes. An unconnected Cin counts as 0; A or B undefined, or a conflict
 * on Cin, drive S and Cout all Z ({@link BlockSignals}).
 */
final class AdderBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int CARRY_IN = 2;
    static final int SUM = 3;
    static final int CARRY_OUT = 4;

    private final int width;
    private final int delay;

    AdderBehavior(int width, int delay) {
        this.width = width;
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

    private void evaluate(BehaviorContext context) {
        OptionalLong first = BlockSignals.unsigned(context.input(A));
        OptionalLong second = BlockSignals.unsigned(context.input(B));
        Optional<Boolean> carryIn = BlockSignals.control(context.input(CARRY_IN), false);
        if (first.isEmpty() || second.isEmpty() || carryIn.isEmpty()) {
            context.drive(SUM, BlockSignals.undefined(width), delay);
            context.drive(CARRY_OUT, BlockSignals.undefined(1), delay);
            return;
        }
        long total = first.getAsLong() + second.getAsLong() + (carryIn.get() ? 1 : 0);
        context.drive(SUM, BlockSignals.result(width, total), delay);
        context.drive(CARRY_OUT, Value.of((total >>> width & 1L) == 1L), delay);
    }
}
