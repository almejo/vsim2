package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.standard.MemoryAccessLog.MemoryEvent;
import cl.almejo.vsim.core.component.standard.MemoryBehavior;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One picture of a memory for the «Mapa de memoria» (spec §6), answering a {@link MemoryMapRequest}: per cell, how
 * many times its words were read and written since the memory started simulating and the current value of its first
 * word ({@code wordsPerCounter} words share one count above 64K words: a cell narrower than that shows its block's
 * counts); plus the accesses since the previous frame ({@code recent}, oldest first) and the simulation time. Immutable:
 * the arrays are copies, and the accessors of the arrays return copies too (read single cells with
 * {@link #readsAt}, {@link #writesAt}, {@link #valueAt}, which allocate nothing).
 */
public record MemoryMapFrame(MemoryMapRequest request, long[] reads, long[] writes, long[] values,
                             List<MemoryEvent> recent, long time, long wordsPerCounter) {

    /** A frame whose counters are per word (a memory of at most 64K words). */
    public MemoryMapFrame(MemoryMapRequest request, long[] reads, long[] writes, long[] values,
                          List<MemoryEvent> recent, long time) {
        this(request, reads, writes, values, recent, time, 1);
    }

    public MemoryMapFrame {
        Objects.requireNonNull(request, "request");
        int cells = request.cells();
        if (reads.length != cells || writes.length != cells || values.length != cells) {
            throw new IllegalArgumentException("a frame of " + cells + " cells needs " + cells + " counts and values");
        }
        reads = reads.clone();
        writes = writes.clone();
        values = values.clone();
        recent = List.copyOf(recent);
        if (wordsPerCounter < 1) {
            throw new IllegalArgumentException("words per counter " + wordsPerCounter);
        }
    }

    /**
     * The frame of {@code memory} for {@code request}, on the simulation thread. Cost: the access log's sums (at most
     * one counter per block of words) plus one word per cell, never a pass over the whole memory.
     */
    static MemoryMapFrame capture(MemoryBehavior memory, MemoryMapRequest request, List<MemoryEvent> recent,
                                  long time) {
        long first = request.firstAddress();
        long wordsPerCell = request.wordsPerCell();
        int cells = request.cells();
        long[] values = new long[cells];
        for (int cell = 0; cell < cells; cell++) {
            values[cell] = memory.liveWord(first + cell * wordsPerCell);
        }
        return new MemoryMapFrame(request, memory.accessLog().readsPerCell(first, wordsPerCell, cells),
                memory.accessLog().writesPerCell(first, wordsPerCell, cells), values, recent, time,
                memory.accessLog().wordsPerCounter());
    }

    public int cells() {
        return request.cells();
    }

    public long readsAt(int cell) {
        return reads[cell];
    }

    public long writesAt(int cell) {
        return writes[cell];
    }

    public long valueAt(int cell) {
        return values[cell];
    }

    @Override
    public long[] reads() {
        return reads.clone();
    }

    @Override
    public long[] writes() {
        return writes.clone();
    }

    @Override
    public long[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MemoryMapFrame frame && request.equals(frame.request) && time == frame.time
                && wordsPerCounter == frame.wordsPerCounter
                && Arrays.equals(reads, frame.reads) && Arrays.equals(writes, frame.writes)
                && Arrays.equals(values, frame.values) && recent.equals(frame.recent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, time, wordsPerCounter, Arrays.hashCode(reads), Arrays.hashCode(writes), Arrays.hashCode(values),
                recent);
    }

    @Override
    public String toString() {
        return "MemoryMapFrame[" + request + ", time=" + time + ", recent=" + recent.size() + "]";
    }
}
