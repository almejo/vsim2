package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Demultiplexor» of 2, 4 or 8 outputs of N bits: pins D[N], S[log2 k], Y0..Y(k-1)[N]. The output {@code Y[S]} follows
 * D and every other one is 0; all change {@code delay} after any input. An undefined S drives every output Z; an
 * undefined D drives only the selected output Z ({@link BlockSignals}).
 */
final class DemuxBehavior implements Behavior {

    private static final int DATA = 0;
    private static final int SELECT = 1;
    private static final int FIRST_OUTPUT = 2;

    private final int outputs;
    private final int width;
    private final int delay;

    DemuxBehavior(int outputs, int width, int delay) {
        this.outputs = outputs;
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
        OptionalLong select = BlockSignals.unsigned(context.input(SELECT));
        if (select.isEmpty()) {
            for (int output = 0; output < outputs; output++) {
                context.drive(FIRST_OUTPUT + output, BlockSignals.undefined(width), delay);
            }
            return;
        }
        OptionalLong data = BlockSignals.unsigned(context.input(DATA));
        Value selected = data.isEmpty() ? BlockSignals.undefined(width) : BlockSignals.result(width, data.getAsLong());
        for (int output = 0; output < outputs; output++) {
            context.drive(FIRST_OUTPUT + output, output == select.getAsLong() ? selected : Value.of(width, 0), delay);
        }
    }
}
