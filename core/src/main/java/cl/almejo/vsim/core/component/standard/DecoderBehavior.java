package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * «Decodificador» of k input bits: pins A[k], EN, Y0..Y(2^k-1). With EN = 1, {@code Y[A]} is 1 and the others 0; with
 * EN = 0 every output is 0 (whatever A is). An unconnected EN counts as 1; a conflict on EN, or an undefined A while
 * enabled, drives every output Z ({@link BlockSignals}). The outputs change {@code delay} after any input.
 */
final class DecoderBehavior implements Behavior {

    static final int ADDRESS = 0;
    static final int ENABLE = 1;
    static final int FIRST_OUTPUT = 2;

    private final int outputs;
    private final int delay;

    DecoderBehavior(int inputBits, int delay) {
        this.outputs = 1 << inputBits;
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
        Optional<Boolean> enabled = BlockSignals.control(context.input(ENABLE), true);
        if (enabled.isPresent() && !enabled.get()) {
            driveAll(context, Value.ZERO);
            return;
        }
        OptionalLong address = BlockSignals.unsigned(context.input(ADDRESS));
        if (enabled.isEmpty() || address.isEmpty()) {
            driveAll(context, BlockSignals.undefined(1));
            return;
        }
        for (int output = 0; output < outputs; output++) {
            context.drive(FIRST_OUTPUT + output, Value.of(output == address.getAsLong()), delay);
        }
    }

    private void driveAll(BehaviorContext context, Value value) {
        for (int output = 0; output < outputs; output++) {
            context.drive(FIRST_OUTPUT + output, value, delay);
        }
    }
}
