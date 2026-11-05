package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Desplazador» of N bits: pins A[N], S[k], Y[N]. S counts the bits to move, as an unsigned number; the «Tipo» says
 * how: logical left or right (zeros come in), arithmetic right (copies the sign bit), rotate left or right. An amount
 * of N or more (possible when N is not a power of two) shifts everything out (logical: 0; arithmetic: all sign bits)
 * and rotates modulo N. An undefined S, or a conflict on A, drives Y all Z ({@link BlockSignals}); with S defined,
 * the Z bits of A move like the others (Logisim's shifter does the same: a shifter is wiring, a floating bit lands
 * where it is moved, and the bits that come in are defined), so a byte read with the rest of the bus floating can be
 * moved into place.
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
        Value data = context.input(A);
        OptionalLong amount = BlockSignals.unsigned(context.input(AMOUNT));
        if (data.conflict() || amount.isEmpty()) {
            context.drive(Y, BlockSignals.undefined(width), delay);
            return;
        }
        int distance = (int) amount.getAsLong();
        long mask = Value.maskFor(width);
        long highImpedance = shift(data.highImpedanceMask(), distance) & mask;
        long bits = shift(data.bits(), distance) & mask & ~highImpedance;
        context.drive(Y, highImpedance == 0L ? BlockSignals.result(width, bits)
                : new Value(width, bits, highImpedance, false), delay);
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
