package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The recording side of one channel, written by the simulation thread only. An event is kept only when it differs
 * from the one before; several changes at the same time keep only the last value (zero-width glitches are invisible
 * at any zoom). Events live in chunks of {@link #CHUNK_SIZE}; past {@link #MAXIMUM_CHUNKS} chunks the oldest is
 * dropped (a channel may keep fewer, {@link #ChannelTrace(int, int)}). {@link #snapshot()} copies only the open tail
 * chunk.
 */
public final class ChannelTrace {

    public static final int CHUNK_SIZE = 1024;
    public static final int MAXIMUM_CHUNKS = 64;

    /** The widest defined value {@link #canonical} shares. */
    private static final int SHARED_WIDTH = 8;

    /** Every defined value of 2 to {@link #SHARED_WIDTH} bits, by width and bits. */
    private static final Value[][] SHARED_VALUES = sharedValues();

    private final List<Chunk> fullChunks = new ArrayList<>();
    private long[] tailTimes = new long[CHUNK_SIZE];
    private Value[] tailValues = new Value[CHUNK_SIZE];
    private int tailSize;
    private boolean truncated;
    private int width;
    private final int maximumChunks;
    private Trace published;

    public ChannelTrace(int width) {
        this(width, MAXIMUM_CHUNKS);
    }

    /** A channel that keeps at most {@code maximumChunks} chunks (counting the open one) before dropping the oldest. */
    public ChannelTrace(int width, int maximumChunks) {
        if (maximumChunks < 2) {
            throw new IllegalArgumentException("maximumChunks must be at least 2: " + maximumChunks);
        }
        this.width = width;
        this.maximumChunks = maximumChunks;
    }

    public int width() {
        return width;
    }

    /** The channel now watches a net of {@code newWidth} bits. */
    public void setWidth(int newWidth) {
        if (newWidth != width) {
            width = newWidth;
            published = null;
        }
    }

    /** Records {@code value} at {@code time}; false when nothing changed. A time before the last one throws. */
    public boolean append(long time, Value value) {
        return add(time, canonical(Objects.requireNonNull(value, "value")));
    }

    /**
     * 1-bit values share the {@link Value} constants and defined values up to {@link #SHARED_WIDTH} bits (a machine's
     * state code, a small counter) share one instance per code, so such a long trace holds no Value per event.
     */
    private static Value canonical(Value value) {
        if (value.conflict() || value.width() > SHARED_WIDTH) {
            return value;
        }
        if (value.width() == 1) {
            if (value.highImpedanceMask() != 0) {
                return Value.HIGH_IMPEDANCE;
            }
            return value.bits() != 0 ? Value.ONE : Value.ZERO;
        }
        if (value.highImpedanceMask() != 0 || (value.bits() & ~Value.maskFor(value.width())) != 0) {
            return value;
        }
        return SHARED_VALUES[value.width()][(int) value.bits()];
    }

    private static Value[][] sharedValues() {
        Value[][] values = new Value[SHARED_WIDTH + 1][];
        for (int sharedWidth = 2; sharedWidth <= SHARED_WIDTH; sharedWidth++) {
            values[sharedWidth] = new Value[1 << sharedWidth];
            for (int bits = 0; bits < values[sharedWidth].length; bits++) {
                values[sharedWidth][bits] = Value.of(sharedWidth, bits);
            }
        }
        return values;
    }

    /** Records that the watched net disappeared at {@code time}; ignored before the first event. */
    public boolean appendGap(long time) {
        if (!hasEvents()) {
            return false;
        }
        return add(time, null);
    }

    /** The recording so far; the same instance until the next change. */
    public Trace snapshot() {
        if (published == null) {
            List<Chunk> chunks = new ArrayList<>(fullChunks);
            if (tailSize > 0) {
                chunks.add(new Chunk(Arrays.copyOf(tailTimes, tailSize), Arrays.copyOf(tailValues, tailSize), tailSize));
            }
            published = new Trace(width, chunks, truncated);
        }
        return published;
    }

    private boolean add(long time, Value value) {
        if (hasEvents() && time < lastTime()) {
            throw new IllegalArgumentException("time " + time + " is before the last event at " + lastTime());
        }
        if (tailSize > 0 && tailTimes[tailSize - 1] == time) {
            return replaceLast(value);
        }
        if (hasEvents() && Objects.equals(lastValue(), value)) {
            return false;
        }
        if (tailSize == CHUNK_SIZE) {
            freezeTail();
        }
        tailTimes[tailSize] = time;
        tailValues[tailSize] = value;
        tailSize++;
        published = null;
        return true;
    }

    /** Same-time collapse: the last event takes {@code value}, or goes away when that brings back the value before it. */
    private boolean replaceLast(Value value) {
        int last = tailSize - 1;
        if (Objects.equals(tailValues[last], value)) {
            return false;
        }
        boolean hasPrevious = last > 0 || !fullChunks.isEmpty();
        Value previous = last > 0 ? tailValues[last - 1] : hasPrevious ? lastValueOf(fullChunks.getLast()) : null;
        if ((hasPrevious && Objects.equals(previous, value)) || (!hasPrevious && value == null)) {
            tailValues[last] = null;
            tailSize--;
        } else {
            tailValues[last] = value;
        }
        published = null;
        return true;
    }

    private void freezeTail() {
        fullChunks.add(new Chunk(tailTimes, tailValues, CHUNK_SIZE));
        tailTimes = new long[CHUNK_SIZE];
        tailValues = new Value[CHUNK_SIZE];
        tailSize = 0;
        if (fullChunks.size() + 1 > maximumChunks) {
            fullChunks.removeFirst();
            truncated = true;
        }
    }

    /** How many events the trace holds now (what it costs to keep). */
    public int eventCount() {
        return fullChunks.size() * CHUNK_SIZE + tailSize;
    }

    private boolean hasEvents() {
        return tailSize > 0 || !fullChunks.isEmpty();
    }

    private long lastTime() {
        if (tailSize > 0) {
            return tailTimes[tailSize - 1];
        }
        Chunk chunk = fullChunks.getLast();
        return chunk.times()[chunk.size() - 1];
    }

    private Value lastValue() {
        return tailSize > 0 ? tailValues[tailSize - 1] : lastValueOf(fullChunks.getLast());
    }

    private static Value lastValueOf(Chunk chunk) {
        return chunk.values()[chunk.size() - 1];
    }
}
