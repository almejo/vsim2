package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «ALU» of N bits: pins A[N], B[N], OP[3], Y[N], Z, N, C, V. OP picks the operation (M7 spec §2.1):
 * <pre>
 *   000 A + B    C = carry out, V = signed overflow
 *   001 A - B    C = borrow (1 when A &lt; B unsigned), V = signed overflow
 *   010 A AND B  011 A OR B  100 A XOR B  101 NOT A   (C = V = 0)
 *   110 A &lt;&lt; 1   111 A &gt;&gt; 1 (logical)  C = the bit shifted out, V = 0
 * </pre>
 * Z = 1 when Y is 0, N = the top bit of Y. Every output changes {@code delay} after any input. OP undefined drives
 * everything Z and shows {@code AluState(-1)}; an undefined operand the operation reads (B is not read by NOT A and the
 * shifts) drives everything Z ({@link BlockSignals}).
 */
final class AluBehavior implements Behavior {

    static final int A = 0;
    static final int B = 1;
    static final int OPERATION = 2;
    static final int RESULT = 3;
    static final int ZERO = 4;
    static final int NEGATIVE = 5;
    static final int CARRY = 6;
    static final int OVERFLOW = 7;

    /** The operation shown while OP is undefined. */
    static final int UNDEFINED_OPERATION = -1;

    private static final int ADD = 0b000;
    private static final int SUBTRACT = 0b001;
    private static final int AND = 0b010;
    private static final int OR = 0b011;
    private static final int XOR = 0b100;
    private static final int NOT = 0b101;
    private static final int SHIFT_LEFT = 0b110;

    private final int width;
    private final int delay;
    private int operation = UNDEFINED_OPERATION;

    AluBehavior(int width, int delay) {
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

    @Override
    public VisibleState visibleState() {
        return new VisibleState.AluState(operation);
    }

    private void evaluate(BehaviorContext context) {
        OptionalLong code = BlockSignals.unsigned(context.input(OPERATION));
        operation = code.isPresent() ? (int) code.getAsLong() : UNDEFINED_OPERATION;
        Value first = context.input(A);
        Value second = context.input(B);
        boolean readsSecond = operation <= XOR;
        if (operation == UNDEFINED_OPERATION || !BlockSignals.allDefined(first)
                || readsSecond && !BlockSignals.allDefined(second)) {
            driveUndefined(context);
            return;
        }
        long firstBits = first.bits();
        long secondBits = readsSecond ? second.bits() : 0L;
        long topBit = 1L << (width - 1);
        long result;
        boolean carry = false;
        boolean overflow = false;
        switch (operation) {
            case ADD -> {
                long total = firstBits + secondBits;
                result = total;
                carry = (total >>> width & 1L) == 1L;
                overflow = outsideSignedRange(BlockSignals.signed(first).getAsLong()
                        + BlockSignals.signed(second).getAsLong());
            }
            case SUBTRACT -> {
                result = firstBits - secondBits;
                carry = firstBits < secondBits;
                overflow = outsideSignedRange(BlockSignals.signed(first).getAsLong()
                        - BlockSignals.signed(second).getAsLong());
            }
            case AND -> result = firstBits & secondBits;
            case OR -> result = firstBits | secondBits;
            case XOR -> result = firstBits ^ secondBits;
            case NOT -> result = ~firstBits;
            case SHIFT_LEFT -> {
                result = firstBits << 1;
                carry = (firstBits & topBit) != 0L;
            }
            default -> {
                result = firstBits >>> 1;
                carry = (firstBits & 1L) != 0L;
            }
        }
        Value output = BlockSignals.result(width, result);
        context.drive(RESULT, output, delay);
        context.drive(ZERO, Value.of(output.bits() == 0L), delay);
        context.drive(NEGATIVE, Value.of((output.bits() & topBit) != 0L), delay);
        context.drive(CARRY, Value.of(carry), delay);
        context.drive(OVERFLOW, Value.of(overflow), delay);
    }

    /** True when {@code number} does not fit a two's complement number of the block's width. */
    private boolean outsideSignedRange(long number) {
        long smallest = -(1L << (width - 1));
        long largest = (1L << (width - 1)) - 1;
        return number < smallest || number > largest;
    }

    private void driveUndefined(BehaviorContext context) {
        context.drive(RESULT, BlockSignals.undefined(width), delay);
        for (int flag = ZERO; flag <= OVERFLOW; flag++) {
            context.drive(flag, BlockSignals.undefined(1), delay);
        }
    }
}
