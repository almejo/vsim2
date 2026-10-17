package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Multiplexor» of 2 or 4 inputs of N bits: pins D0..D(k-1)[N], S[log2 k], Y[N]. {@code Y = D[S]}, {@code delay} after
 * any input changes. Only the selected input matters: an undefined S, or an undefined selected D, drives Y all Z
 * ({@link BlockSignals}).
 */
final class MuxBehavior implements Behavior {

    private final int inputs;
    private final int width;
    private final int delay;

    MuxBehavior(int inputs, int width, int delay) {
        this.inputs = inputs;
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
        int output = inputs + 1;
        OptionalLong select = BlockSignals.unsigned(context.input(inputs));
        if (select.isEmpty()) {
            context.drive(output, BlockSignals.undefined(width), delay);
            return;
        }
        Value selected = context.input((int) select.getAsLong());
        OptionalLong data = BlockSignals.unsigned(selected);
        context.drive(output, data.isEmpty() ? BlockSignals.undefined(width)
                : BlockSignals.result(width, data.getAsLong()), delay);
    }
}
