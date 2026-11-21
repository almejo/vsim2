package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.value.Value;

/**
 * The ranges the import reads Logisim's counts in before any loop or array uses them, so an odd file (a multiplexer
 * with -5 select bits, a splitter with two billion ends) leaves its part out with a note instead of running out of
 * memory or time. Each range holds every value Logisim writes, and Logisim-evolution's too.
 */
final class LogisimLimits {

    /** A bus, a splitter's ends and its bits: VSim's widest value (Logisim's own stop at 32). */
    static final int MAXIMUM_WIDTH = Value.MAXIMUM_WIDTH;
    /** A gate's or bit adder's inputs: Logisim-evolution's most (Logisim 2.7's go up to 32). */
    static final int MAXIMUM_INPUTS = 64;
    /** A plexer's select bits, as Logisim allows them. */
    static final int MINIMUM_SELECT_BITS = 1;
    static final int MAXIMUM_SELECT_BITS = 5;
    /** The largest of these counts: no table of a part's defaults ({@code negateN}, {@code bitN}) grows past it. */
    static final int LARGEST_COUNT = Math.max(MAXIMUM_WIDTH, MAXIMUM_INPUTS);

    private LogisimLimits() {
    }
}
