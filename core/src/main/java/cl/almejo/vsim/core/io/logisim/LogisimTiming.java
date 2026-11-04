package cl.almejo.vsim.core.io.logisim;

/**
 * Logisim 2.7's delays, which the import writes into each part's «Retardo» (spec §2, decision 2: VSim's own defaults
 * stay as they are). Logisim's units are «ticks of propagation» and VSim's are simulation units: one each, so they are
 * copied as they are. They are the delays Logisim 2.7 gives its gates, plexers, registers, memories, flip-flops and
 * counters, and the width-dependent ones of its arithmetic parts.
 */
public final class LogisimTiming {

    /**
     * Each half of an imported clock's period, in simulation units, for every Logisim tick the clock stays high or
     * low. Logisim lets a circuit settle completely between two ticks; VSim's time runs on, so the half period must be
     * longer than the slowest path: the LRV32IM's is a few thousand units (a 32-bit divider alone is 1088), so 10 000
     * leaves room (gap analysis §3.2; the E3 validation measures it). Empty simulated time costs nothing.
     */
    public static final int HALF_PERIOD = 10_000;
    /** Every gate, the buffers and the controlled buffer. */
    public static final int GATE = 1;
    /** Multiplexer, demultiplexer, decoder, priority encoder and bit selector. */
    public static final int PLEXER = 3;
    /** The D, T, JK and SR flip-flops. */
    public static final int FLIP_FLOP = 5;
    /** Register and counter. */
    public static final int REGISTER = 8;
    /** RAM and ROM. */
    public static final int MEMORY = 10;
    /** What the bit extender takes in Logisim (none in VSim, noted). */
    public static final int BIT_EXTENDER = 1;

    private LogisimTiming() {
    }

    /** Adder: {@code (w + 2)}. */
    public static int adder(int width) {
        return width + 2;
    }

    /** Subtractor: {@code (w + 4)}. */
    public static int subtractor(int width) {
        return width + 4;
    }

    /** Comparator and negator: {@code (w + 2)}. */
    public static int comparator(int width) {
        return width + 2;
    }

    /** Shifter: {@code 3w}. */
    public static int shifter(int width) {
        return 3 * width;
    }

    /** Multiplier and divider: {@code w (w + 2)}, 1088 at 32 bits. */
    public static int multiplier(int width) {
        return width * (width + 2);
    }

    /** Bit adder (VSim's «Contador de unos»): as many units as bits in its output. */
    public static int bitAdder(int outputWidth) {
        return outputWidth;
    }
}
