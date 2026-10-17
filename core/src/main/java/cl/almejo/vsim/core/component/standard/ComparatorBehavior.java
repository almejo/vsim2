package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Comparador» of N bits: pins A[N], B[N], A&lt;B, A=B, A&gt;B. Exactly one output is 1, {@code delay} after any input;
 * «Con signo» compares in two's complement. A or B undefined drives the three outputs Z ({@link BlockSignals}).
 */
final class ComparatorBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int LESS = 2;
    static final int EQUAL = 3;
    static final int GREATER = 4;

    private final boolean signed;
    private final int delay;

    ComparatorBehavior(boolean signed, int delay) {
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

    private void evaluate(BehaviorContext context) {
        OptionalLong first = read(context.input(A));
        OptionalLong second = read(context.input(B));
        if (first.isEmpty() || second.isEmpty()) {
            for (int output = LESS; output <= GREATER; output++) {
                context.drive(output, BlockSignals.undefined(1), delay);
            }
            return;
        }
        int comparison = Long.compare(first.getAsLong(), second.getAsLong());
        context.drive(LESS, Value.of(comparison < 0), delay);
        context.drive(EQUAL, Value.of(comparison == 0), delay);
        context.drive(GREATER, Value.of(comparison > 0), delay);
    }

    private OptionalLong read(Value value) {
        return signed ? BlockSignals.signed(value) : BlockSignals.unsigned(value);
    }
}
