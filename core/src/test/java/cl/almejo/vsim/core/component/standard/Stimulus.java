package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.value.Value;

import java.util.Random;

/**
 * What a test drives on one input pin of a {@link BlockHarness}: a defined number, all Z (nothing drives it), a value
 * only partly Z, or a conflict (two drivers disagree). {@link #netValue} is what the block then reads, as far as the
 * rules of M7 spec §2.1 care (a conflict is only flagged, its bits do not matter).
 */
record Stimulus(Kind kind, int width, long bits, long highImpedanceMask) {

    enum Kind { DEFINED, HIGH_IMPEDANCE, PARTLY_HIGH_IMPEDANCE, CONFLICT }

    static Stimulus defined(int width, long bits) {
        return new Stimulus(Kind.DEFINED, width, bits & Value.maskFor(width), 0L);
    }

    static Stimulus highImpedance(int width) {
        return new Stimulus(Kind.HIGH_IMPEDANCE, width, 0L, Value.maskFor(width));
    }

    static Stimulus conflict(int width) {
        return new Stimulus(Kind.CONFLICT, width, 0L, 0L);
    }

    /** Some bits Z and some defined; a 1-bit pin cannot be partly Z, so it gets all Z. */
    static Stimulus partlyHighImpedance(int width, Random random) {
        if (width == 1) {
            return highImpedance(1);
        }
        long mask = Value.maskFor(width);
        long highImpedance;
        do {
            highImpedance = random.nextLong() & mask;
        } while (highImpedance == 0L || highImpedance == mask);
        return new Stimulus(Kind.PARTLY_HIGH_IMPEDANCE, width, random.nextLong() & mask & ~highImpedance,
                highImpedance);
    }

    /** One of the three undefined kinds, at random. */
    static Stimulus undefined(int width, Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> highImpedance(width);
            case 1 -> partlyHighImpedance(width, random);
            default -> conflict(width);
        };
    }

    /** A defined edge value (0, 1, all ones, the sign bit, …) or a random one. */
    static Stimulus definedSample(int width, Random random) {
        long mask = Value.maskFor(width);
        long top = 1L << (width - 1);
        long[] edges = {0L, 1L, 2L, mask, mask - 1, top, top - 1, top + 1, 0x5555_5555L, 0xAAAA_AAAAL};
        long bits = random.nextInt(3) == 0 ? edges[random.nextInt(edges.length)] : random.nextLong();
        return defined(width, bits);
    }

    /** Mostly defined values, sometimes undefined of any kind ({@code undefinedPercent} of the time). */
    static Stimulus sample(int width, Random random, int undefinedPercent) {
        return random.nextInt(100) < undefinedPercent ? undefined(width, random) : definedSample(width, random);
    }

    void applyTo(BlockHarness harness, int pinIndex) {
        switch (kind) {
            case DEFINED -> harness.set(pinIndex, Value.of(width, bits));
            case HIGH_IMPEDANCE -> harness.release(pinIndex);
            case PARTLY_HIGH_IMPEDANCE -> harness.set(pinIndex, new Value(width, bits, highImpedanceMask, false));
            case CONFLICT -> harness.conflict(pinIndex);
        }
    }

    Value netValue() {
        return kind == Kind.CONFLICT ? Value.of(width, 0L).withConflict()
                : new Value(width, bits, highImpedanceMask, false);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case DEFINED -> Long.toUnsignedString(bits);
            case HIGH_IMPEDANCE -> "Z";
            case PARTLY_HIGH_IMPEDANCE -> new Value(width, bits, highImpedanceMask, false).hex() + "(partly Z)";
            case CONFLICT -> "conflict";
        };
    }
}
