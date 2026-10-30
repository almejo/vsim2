package cl.almejo.vsim.core.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * The last bytes a Terminal received, oldest dropped first, for the window's byte monitor, the sounds and «Guardar
 * salida». Readers remember the last sequence number they saw and ask for what came {@link #since(long)}. Simulation
 * thread only.
 */
public final class ByteLog {

    /** How many bytes the ring keeps (spec §16, decision 8). */
    public static final int CAPACITY = 10_000;
    /** The value logged for an undefined or high-impedance data byte (the screen shows {@code ?}). */
    public static final int UNDEFINED = -1;

    /** One received byte: its number (from 1), the simulation time it arrived and its value or {@link #UNDEFINED}. */
    public record Entry(long sequence, long time, int value) {

        /** Whether the data pins were not fully defined on that edge. */
        public boolean undefined() {
            return value == UNDEFINED;
        }
    }

    private final long[] times = new long[CAPACITY];
    private final int[] values = new int[CAPACITY];
    private long lastSequence;
    private int count;

    /** Records a byte; the oldest one leaves when the ring is full. */
    void append(long time, int value) {
        lastSequence++;
        int slot = slotOf(lastSequence);
        times[slot] = time;
        values[slot] = value;
        count = Math.min(count + 1, CAPACITY);
    }

    /** The entries still kept whose sequence number is greater than {@code sequence}, oldest first. */
    public List<Entry> since(long sequence) {
        long first = Math.max(sequence + 1, lastSequence - count + 1);
        List<Entry> entries = new ArrayList<>((int) Math.max(0, lastSequence - first + 1));
        for (long current = first; current <= lastSequence; current++) {
            int slot = slotOf(current);
            entries.add(new Entry(current, times[slot], values[slot]));
        }
        return entries;
    }

    /** The number of the last byte received; 0 before the first one. */
    public long lastSequence() {
        return lastSequence;
    }

    /** Forgets every entry; numbering goes on, so a reader's last sequence stays meaningful. */
    public void clear() {
        count = 0;
    }

    /** A separate log with the same entries and numbering. */
    ByteLog copy() {
        ByteLog copy = new ByteLog();
        System.arraycopy(times, 0, copy.times, 0, CAPACITY);
        System.arraycopy(values, 0, copy.values, 0, CAPACITY);
        copy.lastSequence = lastSequence;
        copy.count = count;
        return copy;
    }

    private static int slotOf(long sequence) {
        return (int) (sequence % CAPACITY);
    }
}
