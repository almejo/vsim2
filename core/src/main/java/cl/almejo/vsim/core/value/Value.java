package cl.almejo.vsim.core.value;

import java.util.Collection;
import java.util.function.LongBinaryOperator;

/**
 * Immutable signal value of 1..64 bits. Every bit is 0, 1 or Z (high impedance).
 * {@code conflict} marks that two drivers disagreed (a short circuit).
 */
public record Value(int width, long bits, long highImpedanceMask, boolean conflict) {

    public static final int MAXIMUM_WIDTH = 64;
    public static final Value ZERO = new Value(1, 0L, 0L, false);
    public static final Value ONE = new Value(1, 1L, 0L, false);
    public static final Value HIGH_IMPEDANCE = new Value(1, 0L, 1L, false);

    public Value {
        if (width < 1 || width > MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("width must be between 1 and 64: " + width);
        }
        long mask = maskFor(width);
        highImpedanceMask &= mask;
        bits &= mask & ~highImpedanceMask;
    }

    public static long maskFor(int width) {
        return width == MAXIMUM_WIDTH ? -1L : (1L << width) - 1;
    }

    public static Value of(int width, long bits) {
        return new Value(width, bits, 0L, false);
    }

    public static Value of(boolean bit) {
        return bit ? ONE : ZERO;
    }

    public static Value highImpedance(int width) {
        return new Value(width, 0L, -1L, false);
    }

    /**
     * Resolves the value of a net from the values its drivers impose.
     * High impedance bits yield; different defined bits produce a conflict.
     */
    public static Value resolve(int width, Collection<Value> drivers) {
        long mask = maskFor(width);
        long defined = 0L;
        long definedBits = 0L;
        long conflictMask = 0L;
        for (Value driver : drivers) {
            if (driver.width != width) {
                throw new IllegalArgumentException("driver width " + driver.width + " differs from net width " + width);
            }
            long driverDefined = ~driver.highImpedanceMask & mask;
            long overlap = defined & driverDefined;
            conflictMask |= overlap & (definedBits ^ driver.bits);
            definedBits |= driver.bits & driverDefined & ~defined;
            defined |= driverDefined;
        }
        Value result = new Value(width, definedBits, ~defined, false);
        return conflictMask != 0 ? result.withConflict() : result;
    }

    public BitState bit(int index) {
        if (index < 0 || index >= width) {
            throw new IndexOutOfBoundsException("bit " + index + " of width " + width);
        }
        if ((highImpedanceMask >>> index & 1L) == 1L) {
            return BitState.HIGH_IMPEDANCE;
        }
        return (bits >>> index & 1L) == 1L ? BitState.ONE : BitState.ZERO;
    }

    public boolean isFullyHighImpedance() {
        return highImpedanceMask == maskFor(width);
    }

    public boolean isFullyDefined() {
        return highImpedanceMask == 0L && !conflict;
    }

    public Value and(Value other) {
        return combine(other, (left, right) -> left & right);
    }

    public Value or(Value other) {
        return combine(other, (left, right) -> left | right);
    }

    public Value xor(Value other) {
        return combine(other, (left, right) -> left ^ right);
    }

    public Value not() {
        return new Value(width, ~bits, highImpedanceMask, conflict);
    }

    public Value withConflict() {
        return new Value(width, bits, highImpedanceMask, true);
    }

    private Value combine(Value other, LongBinaryOperator operation) {
        if (other.width != width) {
            throw new IllegalArgumentException("width mismatch: " + width + " vs " + other.width);
        }
        long anyHighImpedance = highImpedanceMask | other.highImpedanceMask;
        long bothHighImpedance = highImpedanceMask & other.highImpedanceMask;
        long onlyThisHighImpedance = highImpedanceMask & ~other.highImpedanceMask;
        long onlyOtherHighImpedance = other.highImpedanceMask & ~highImpedanceMask;
        long computed = operation.applyAsLong(bits, other.bits);
        long result = (computed & ~anyHighImpedance)
                | (other.bits & onlyThisHighImpedance)
                | (bits & onlyOtherHighImpedance);
        return new Value(width, result, bothHighImpedance, conflict || other.conflict);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(conflict ? "X!" : "");
        for (int index = width - 1; index >= 0; index--) {
            builder.append(switch (bit(index)) {
                case ZERO -> '0';
                case ONE -> '1';
                case HIGH_IMPEDANCE -> 'Z';
            });
        }
        return builder.toString();
    }
}
