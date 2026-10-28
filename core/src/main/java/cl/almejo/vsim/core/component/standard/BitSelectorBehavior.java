package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.OptionalLong;

/**
 * «Selector de bits»: pins in[N], S[k], out[M]. {@code out} is group number S of M bits of the input, group 0 being the
 * low bits; the bits of the last group that lie beyond the input, and every bit of a group past the end, are zeros.
 * An undefined input or S drives out all Z. {@code delay} after any input changes.
 */
final class BitSelectorBehavior implements Behavior {

    private static final int INPUT = 0;
    private static final int SELECT = 1;
    private static final int OUTPUT = 2;

    private final int outputWidth;
    private final int delay;

    BitSelectorBehavior(int outputWidth, int delay) {
        this.outputWidth = outputWidth;
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
        OptionalLong data = BlockSignals.unsigned(context.input(INPUT));
        OptionalLong group = BlockSignals.unsigned(context.input(SELECT));
        if (data.isEmpty() || group.isEmpty()) {
            context.drive(OUTPUT, BlockSignals.undefined(outputWidth), delay);
            return;
        }
        long firstBit = group.getAsLong() * outputWidth;
        long bits = firstBit >= Long.SIZE ? 0L : data.getAsLong() >>> firstBit;
        context.drive(OUTPUT, BlockSignals.result(outputWidth, bits), delay);
    }
}
