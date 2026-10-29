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
    /** The 1-bit values, indexed by bit | highImpedance << 1 | conflict << 2: every 1-bit result is one of these. */
    private static final Value[] ONE_BIT = {ZERO, ONE, HIGH_IMPEDANCE, HIGH_IMPEDANCE,
            new Value(1, 0L, 0L, true), new Value(1, 1L, 0L, true), new Value(1, 0L, 1L, true),
            new Value(1, 0L, 1L, true)};

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
        return canonical(width, bits, 0L, false);
    }

    /** Equal to {@code new Value(width, bits, highImpedanceMask, conflict)}; for 1 bit, the shared instance. */
    private static Value canonical(int width, long bits, long highImpedanceMask, boolean conflict) {
        if (width == 1) {
            int highImpedance = (int) (highImpedanceMask & 1L);
            int bit = (int) (bits & 1L) & ~highImpedance;
            return ONE_BIT[bit | highImpedance << 1 | (conflict ? 4 : 0)];
        }
        return new Value(width, bits, highImpedanceMask, conflict);
    }

    public static Value of(boolean bit) {
        return bit ? ONE : ZERO;
    }

    public static Value highImpedance(int width) {
        return canonical(width, 0L, -1L, false);
    }

    /**
     * Resolves the value of a net from the values its drivers impose.
     * High impedance bits yield; different defined bits produce a conflict.
     */
    public static Value resolve(int width, Collection<Value> drivers) {
        Resolution resolution = new Resolution(width);
        for (Value driver : drivers) {
            resolution.add(driver);
        }
        return resolution.value();
    }

    /**
     * The rule of {@link #resolve(int, Collection)} as an accumulator, so a net resolves its pins one by one without
     * building a list: {@link #reset()}, {@link #add(Value)} each driver, {@link #value()}.
     */
    public static final class Resolution {
        private final int width;
        private final long mask;
        private int drivers;
        private long defined;
        private long definedBits;
        private long conflictMask;

        public Resolution(int width) {
            this.width = width;
            this.mask = maskFor(width);
        }

        public void reset() {
            drivers = 0;
            defined = 0L;
            definedBits = 0L;
            conflictMask = 0L;
        }

        public void add(Value driver) {
            if (driver.width != width) {
                throw new IllegalArgumentException("driver width " + driver.width + " differs from net width " + width);
            }
            long driverDefined = ~driver.highImpedanceMask & mask;
            long overlap = defined & driverDefined;
            conflictMask |= overlap & (definedBits ^ driver.bits);
            definedBits |= driver.bits & driverDefined & ~defined;
            defined |= driverDefined;
            drivers++;
        }

        /** True when no driver was added since the last reset. */
        public boolean isEmpty() {
            return drivers == 0;
        }

        public Value value() {
            return canonical(width, definedBits, ~defined, conflictMask != 0);
        }
    }

    /** This value with, on each bit in Z, the bit {@code filler} defines (if any); keeps this conflict flag. */
    public Value filledWith(Value filler) {
        if (filler.width != width) {
            throw new IllegalArgumentException("width mismatch: " + width + " vs " + filler.width);
        }
        long fill = highImpedanceMask & ~filler.highImpedanceMask;
        return canonical(width, bits | (filler.bits & fill), highImpedanceMask & ~fill, conflict);
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

    /**
     * The value in hexadecimal, most significant digit first, one digit per 4 bits (the top digit may cover fewer):
     * a digit whose bits are all Z is {@code Z}, one only partly in Z is {@code ?}. The conflict flag is not shown.
     */
    public String hex() {
        int digits = (width + 3) / 4;
        StringBuilder builder = new StringBuilder(digits);
        for (int digit = digits - 1; digit >= 0; digit--) {
            int shift = digit * 4;
            long nibbleMask = (maskFor(width) >>> shift) & 0xFL;
            long highImpedance = (highImpedanceMask >>> shift) & nibbleMask;
            if (highImpedance == 0) {
                builder.append(Character.toUpperCase(Character.forDigit((int) ((bits >>> shift) & nibbleMask), 16)));
            } else if (highImpedance == nibbleMask) {
                builder.append('Z');
            } else {
                builder.append('?');
            }
        }
        return builder.toString();
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
        return canonical(width, ~bits, highImpedanceMask, conflict);
    }

    public Value withConflict() {
        return canonical(width, bits, highImpedanceMask, true);
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
        return canonical(width, result, bothHighImpedance, conflict || other.conflict);
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
