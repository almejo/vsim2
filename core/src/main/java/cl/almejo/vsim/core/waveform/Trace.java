package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An immutable picture of one channel's recording, safe to read from any thread. Every event differs from the one
 * before it, so each event is an edge (the first one is where the recording starts). Times only grow.
 */
public final class Trace {

    private final int width;
    private final List<Chunk> chunks;
    private final int eventCount;
    private final boolean truncated;

    /** Every chunk but the last holds exactly {@link ChannelTrace#CHUNK_SIZE} events. */
    Trace(int width, List<Chunk> chunks, boolean truncated) {
        this.width = width;
        this.chunks = List.copyOf(chunks);
        this.truncated = truncated;
        int count = 0;
        for (Chunk chunk : this.chunks) {
            count += chunk.size();
        }
        this.eventCount = count;
    }

    /** The width of the net the channel watches now (older events may have another width). */
    public int width() {
        return width;
    }

    public int eventCount() {
        return eventCount;
    }

    /** True when the oldest events were dropped to stay within {@link ChannelTrace#MAXIMUM_CHUNKS} chunks. */
    public boolean truncated() {
        return truncated;
    }

    public OptionalLong firstTime() {
        return eventCount == 0 ? OptionalLong.empty() : OptionalLong.of(timeAt(0));
    }

    public OptionalLong lastTime() {
        return eventCount == 0 ? OptionalLong.empty() : OptionalLong.of(timeAt(eventCount - 1));
    }

    /** The value at {@code time}: empty before the first event and inside a gap. */
    public Optional<Value> valueAt(long time) {
        int index = lastIndexAtOrBefore(time);
        return index < 0 ? Optional.empty() : Optional.ofNullable(valueAtIndex(index));
    }

    /** The events with {@code from <= time <= to}, oldest first. */
    public List<TraceEvent> edgesBetween(long from, long to) {
        List<TraceEvent> events = new ArrayList<>();
        if (from > to) {
            return events;
        }
        int last = lastIndexAtOrBefore(to);
        for (int index = lastIndexAtOrBefore(from - 1) + 1; index <= last; index++) {
            events.add(new TraceEvent(timeAt(index), Optional.ofNullable(valueAtIndex(index))));
        }
        return events;
    }

    /** The time of the latest event strictly before {@code time}. */
    public OptionalLong previousEdge(long time) {
        int index = lastIndexAtOrBefore(time - 1);
        return index < 0 ? OptionalLong.empty() : OptionalLong.of(timeAt(index));
    }

    /** The time of the earliest event strictly after {@code time}. */
    public OptionalLong nextEdge(long time) {
        int index = lastIndexAtOrBefore(time) + 1;
        return index >= eventCount ? OptionalLong.empty() : OptionalLong.of(timeAt(index));
    }

    /** For tests: the chunks, to check that snapshots share them. */
    List<Chunk> chunks() {
        return chunks;
    }

    private long timeAt(int index) {
        return chunks.get(index / ChannelTrace.CHUNK_SIZE).times()[index % ChannelTrace.CHUNK_SIZE];
    }

    private Value valueAtIndex(int index) {
        return chunks.get(index / ChannelTrace.CHUNK_SIZE).values()[index % ChannelTrace.CHUNK_SIZE];
    }

    /** The index of the latest event at or before {@code time}, or -1. */
    private int lastIndexAtOrBefore(long time) {
        int low = 0;
        int high = eventCount - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (timeAt(middle) <= time) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }
}
