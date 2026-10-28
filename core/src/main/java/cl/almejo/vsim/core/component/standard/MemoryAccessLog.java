package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reads and writes of one memory since it started simulating, for the memory map (spec §6): how many times each
 * word was read and written, and the accesses not yet taken by {@link #drainRecent()}. Sparse: counters exist only
 * for pages of words that were touched, and a memory bigger than {@link #MAXIMUM_COUNTERS} words counts per block of
 * words (a power of two), so even a 16M-word memory read end to end keeps at most about a megabyte of counters.
 * Belongs to the simulation thread, like its behaviour; a restart makes a new behaviour and so a new, empty log.
 */
public final class MemoryAccessLog {

    /** One access: a word read or written at a simulation time. */
    public record MemoryEvent(long address, MemoryAccess access, long time) {
        public MemoryEvent {
            Objects.requireNonNull(access, "access");
        }
    }

    /** At most this many counters (per kind); bigger memories count per block of words. */
    static final int MAXIMUM_COUNTERS = 1 << 16;
    /** Recent accesses kept between two drains; older ones are dropped (the map only fades the latest). */
    static final int MAXIMUM_RECENT = 4096;
    private static final int PAGE_BITS = 8;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;

    private final int blockBits;
    private final Map<Long, long[]> readPages = new HashMap<>();
    private final Map<Long, long[]> writePages = new HashMap<>();
    private final Deque<MemoryEvent> recent = new ArrayDeque<>();

    /** A log for a memory of {@code words} words. */
    MemoryAccessLog(long words) {
        int addressBits = 64 - Long.numberOfLeadingZeros(Math.max(1, words - 1));
        this.blockBits = Math.max(0, addressBits - Integer.numberOfTrailingZeros(MAXIMUM_COUNTERS));
    }

    /** How many words share one counter: 1 up to {@link #MAXIMUM_COUNTERS} words, more above. */
    public long wordsPerCounter() {
        return 1L << blockBits;
    }

    void read(long address, long time) {
        count(readPages, address);
        remember(new MemoryEvent(address, MemoryAccess.READ, time));
    }

    void write(long address, long time) {
        count(writePages, address);
        remember(new MemoryEvent(address, MemoryAccess.WRITE, time));
    }

    /** Times the word at {@code address} was read (its block's count in a memory that counts per block). */
    public long reads(long address) {
        return counter(readPages, address >>> blockBits);
    }

    /** Times the word at {@code address} was written (its block's count in a memory that counts per block). */
    public long writes(long address) {
        return counter(writePages, address >>> blockBits);
    }

    /**
     * The reads of {@code cells} consecutive cells of {@code wordsPerCell} words each, from {@code firstAddress}: one
     * sum per cell, ready for the map to paint. A cell smaller than a counting block gets its whole block's count.
     */
    public long[] readsPerCell(long firstAddress, long wordsPerCell, int cells) {
        return perCell(readPages, firstAddress, wordsPerCell, cells);
    }

    /** {@link #readsPerCell} for the writes. */
    public long[] writesPerCell(long firstAddress, long wordsPerCell, int cells) {
        return perCell(writePages, firstAddress, wordsPerCell, cells);
    }

    /** The accesses since the last drain, oldest first (at most {@link #MAXIMUM_RECENT}); empties the list. */
    public List<MemoryEvent> drainRecent() {
        List<MemoryEvent> drained = new ArrayList<>(recent);
        recent.clear();
        return drained;
    }

    private void remember(MemoryEvent event) {
        if (recent.size() == MAXIMUM_RECENT) {
            recent.removeFirst();
        }
        recent.addLast(event);
    }

    private void count(Map<Long, long[]> pages, long address) {
        long counter = address >>> blockBits;
        long[] page = pages.computeIfAbsent(counter >>> PAGE_BITS, pageIndex -> new long[PAGE_SIZE]);
        page[(int) (counter & (PAGE_SIZE - 1))]++;
    }

    private static long counter(Map<Long, long[]> pages, long counter) {
        long[] page = pages.get(counter >>> PAGE_BITS);
        return page == null ? 0 : page[(int) (counter & (PAGE_SIZE - 1))];
    }

    private long[] perCell(Map<Long, long[]> pages, long firstAddress, long wordsPerCell, int cells) {
        if (wordsPerCell < 1 || cells < 0 || firstAddress < 0) {
            throw new IllegalArgumentException("cells of " + wordsPerCell + " words, " + cells + " cells from "
                    + firstAddress);
        }
        long[] sums = new long[cells];
        if (pages.isEmpty()) {
            return sums;
        }
        for (int cell = 0; cell < cells; cell++) {
            long first = (firstAddress + cell * wordsPerCell) >>> blockBits;
            long last = (firstAddress + (cell + 1) * wordsPerCell - 1) >>> blockBits;
            long sum = 0;
            for (long counter = first; counter <= last; counter++) {
                long[] page = pages.get(counter >>> PAGE_BITS);
                if (page == null) {
                    counter = ((counter >>> PAGE_BITS) + 1 << PAGE_BITS) - 1; // skip the untouched page
                    continue;
                }
                sum += page[(int) (counter & (PAGE_SIZE - 1))];
            }
            sums[cell] = sum;
        }
        return sums;
    }
}
