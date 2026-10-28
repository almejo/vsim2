package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * «Codificador con prioridad» of 2, 4, 8 or 16 one-bit inputs: pins I0..I(k-1), A[log2 k], V. A is the index of the
 * highest input at 1 and V is 1; with none at 1, A = 0 and V = 0. An unconnected input counts as 0. Inputs are read
 * from the highest down, so an input in conflict only matters when no higher one is 1: then A and V are Z
 * ({@link BlockSignals}). The outputs change {@code delay} after any input.
 */
final class PriorityEncoderBehavior implements Behavior {

    private final int inputs;
    private final int addressBits;
    private final int delay;

    PriorityEncoderBehavior(int inputs, int delay) {
        this.inputs = inputs;
        this.addressBits = Integer.numberOfTrailingZeros(inputs);
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
        for (int index = inputs - 1; index >= 0; index--) {
            Optional<Boolean> input = BlockSignals.control(context.input(index), false);
            if (input.isEmpty()) {
                context.drive(inputs, BlockSignals.undefined(addressBits), delay);
                context.drive(inputs + 1, BlockSignals.undefined(1), delay);
                return;
            }
            if (input.get()) {
                context.drive(inputs, Value.of(addressBits, index), delay);
                context.drive(inputs + 1, Value.ONE, delay);
                return;
            }
        }
        context.drive(inputs, Value.of(addressBits, 0), delay);
        context.drive(inputs + 1, Value.ZERO, delay);
    }
}
