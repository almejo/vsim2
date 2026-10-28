package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * «Restador» of N bits: pins A[N], B[N], Bin, D[N], Bout. {@code D = (A - B - Bin) mod 2^N}; Bout is 1 when the
 * unsigned result needed a borrow ({@code A < B + Bin}). An unconnected Bin counts as 0; undefined A or B, or a
 * conflict on Bin, drive D and Bout all Z ({@link BlockSignals}).
 */
final class SubtractorBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int BORROW_IN = 2;
    static final int DIFFERENCE = 3;
    static final int BORROW_OUT = 4;

    private final int width;
    private final int delay;

    SubtractorBehavior(int width, int delay) {
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
        Optional<Boolean> borrowIn = BlockSignals.control(context.input(BORROW_IN), false);
        if (first.isEmpty() || second.isEmpty() || borrowIn.isEmpty()) {
            context.drive(DIFFERENCE, BlockSignals.undefined(width), delay);
            context.drive(BORROW_OUT, BlockSignals.undefined(1), delay);
            return;
        }
        long total = first.getAsLong() - second.getAsLong() - (borrowIn.get() ? 1 : 0);
        context.drive(DIFFERENCE, BlockSignals.result(width, total), delay);
        context.drive(BORROW_OUT, Value.of(total < 0), delay);
    }
}
