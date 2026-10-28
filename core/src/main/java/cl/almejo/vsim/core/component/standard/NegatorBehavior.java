package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.OptionalLong;

/**
 * «Negador» of N bits: pins A[N], Y[N]. {@code Y = -A} in two's complement, cut to N bits (0 gives 0 and the minimum
 * gives itself). Undefined A drives Y all Z ({@link BlockSignals}).
 */
final class NegatorBehavior implements Behavior {

    static final int A = 0;
    static final int Y = 1;

    private final int width;
    private final int delay;

    NegatorBehavior(int width, int delay) {
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
        OptionalLong input = BlockSignals.unsigned(context.input(A));
        context.drive(Y, input.isEmpty() ? BlockSignals.undefined(width)
                : BlockSignals.result(width, -input.getAsLong()), delay);
    }
}
