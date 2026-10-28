package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.OptionalLong;

/**
 * «Desplazador» of N bits: pins A[N], S[k], Y[N]. S counts the bits to move, as an unsigned number; the «Tipo» says
 * how: logical left or right (zeros come in), arithmetic right (copies the sign bit), rotate left or right. An amount
 * of N or more (possible when N is not a power of two) shifts everything out (logical: 0; arithmetic: all sign bits)
 * and rotates modulo N. Undefined A or S drive Y all Z ({@link BlockSignals}).
 */
final class ShifterBehavior implements Behavior {

    static final int A = 0;
    static final int AMOUNT = 1;
    static final int Y = 2;

    private final int width;
    private final String shiftType;
    private final int delay;

    ShifterBehavior(int width, String shiftType, int delay) {
        this.width = width;
        this.shiftType = shiftType;
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
        OptionalLong data = BlockSignals.unsigned(context.input(A));
        OptionalLong amount = BlockSignals.unsigned(context.input(AMOUNT));
        if (data.isEmpty() || amount.isEmpty()) {
            context.drive(Y, BlockSignals.undefined(width), delay);
            return;
        }
        context.drive(Y, BlockSignals.result(width, shift(data.getAsLong(), (int) amount.getAsLong())), delay);
    }

    private long shift(long bits, int amount) {
        return switch (shiftType) {
            case ArithmeticTypes.LOGICAL_RIGHT -> amount >= width ? 0 : bits >>> amount;
            case ArithmeticTypes.ARITHMETIC_RIGHT -> signExtended(bits) >> Math.min(amount, width - 1);
            case ArithmeticTypes.ROTATE_LEFT -> rotatedLeft(bits, amount % width);
            case ArithmeticTypes.ROTATE_RIGHT -> rotatedLeft(bits, (width - amount % width) % width);
            default -> amount >= width ? 0 : bits << amount;
        };
    }

    private long signExtended(long bits) {
        int unusedBits = Long.SIZE - width;
        return bits << unusedBits >> unusedBits;
    }

    private long rotatedLeft(long bits, int amount) {
        return amount == 0 ? bits : bits << amount | bits >>> (width - amount);
    }
}
