package cl.almejo.vsim.core.component.standard;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of consecutive bus bits of one pin of a grouped splitter or joiner: bus bits {@code busStart} up are pin bits
 * {@code pinStart} up, {@code mask} wide (its low bits set). The behaviours copy a whole run with a shift and a mask
 * instead of bit by bit, so a 32-bit splitter of a CPU's instruction fields costs a few operations per event; the runs
 * are worked out once, when the behaviour is built.
 */
record BitRun(int busStart, int pinStart, long mask) {

    /** The runs of {@code ascendingBusBits}, the bus bits of one pin: {@code 7-11,25-31} is two runs. */
    static BitRun[] of(List<Integer> ascendingBusBits) {
        List<BitRun> runs = new ArrayList<>();
        int pinBit = 0;
        while (pinBit < ascendingBusBits.size()) {
            int runStart = pinBit;
            while (pinBit + 1 < ascendingBusBits.size()
                    && ascendingBusBits.get(pinBit + 1) == ascendingBusBits.get(pinBit) + 1) {
                pinBit++;
            }
            pinBit++;
            runs.add(new BitRun(ascendingBusBits.get(runStart), runStart, (1L << (pinBit - runStart)) - 1));
        }
        return runs.toArray(BitRun[]::new);
    }

    /** The run's bits of {@code busWord} (a value's bits or its Z mask), moved to their place in the pin. */
    long toPin(long busWord) {
        return (busWord >>> busStart & mask) << pinStart;
    }

    /** The run's bits of {@code pinWord}, moved to their place on the bus. */
    long toBus(long pinWord) {
        return (pinWord >>> pinStart & mask) << busStart;
    }
}
