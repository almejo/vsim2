package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.OptionalLong;

/**
 * «Contador de unos» of N bits: pins A[N], Y[k] with k the bits needed to count 0..N. Y is how many bits of A are 1.
 * Undefined A drives Y all Z ({@link BlockSignals}).
 */
final class BitCounterBehavior implements Behavior {

    static final int A = 0;
    static final int Y = 1;

    private final int outputWidth;
    private final int delay;

    BitCounterBehavior(int width, int delay) {
        this.outputWidth = ArithmeticTypes.bitsToCount(width);
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
        context.drive(Y, input.isEmpty() ? BlockSignals.undefined(outputWidth)
                : BlockSignals.result(outputWidth, Long.bitCount(input.getAsLong())), delay);
    }
}
