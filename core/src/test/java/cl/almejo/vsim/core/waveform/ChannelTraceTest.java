package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelTraceTest {

    /** {@code count} events at times 0, 10, 20, …, alternating 0 and 1. */
    private static ChannelTrace alternating(int count) {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < count; index++) {
            channel.append(10L * index, Value.of(index % 2 == 1));
        }
        return channel;
    }

    @Test
    void valuesReadBackAtAndBetweenTheirTimes() {
        ChannelTrace channel = new ChannelTrace(1);
        assertThat(channel.append(5, Value.ZERO)).isTrue();
        assertThat(channel.append(9, Value.ONE)).isTrue();

        Trace trace = channel.snapshot();

        assertThat(trace.width()).isEqualTo(1);
        assertThat(trace.valueAt(4)).isEmpty();
        assertThat(trace.valueAt(5)).contains(Value.ZERO);
        assertThat(trace.valueAt(8)).contains(Value.ZERO);
        assertThat(trace.valueAt(9)).contains(Value.ONE);
        assertThat(trace.valueAt(1_000)).contains(Value.ONE);
        assertThat(trace.firstTime()).hasValue(5);
        assertThat(trace.lastTime()).hasValue(9);
        assertThat(trace.eventCount()).isEqualTo(2);
        assertThat(trace.truncated()).isFalse();
    }

    @Test
    void anUnchangedValueIsNotRecorded() {
        ChannelTrace channel = new ChannelTrace(1);
        channel.append(0, Value.ONE);
        assertThat(channel.append(3, Value.ONE)).isFalse();
        assertThat(channel.snapshot().eventCount()).isEqualTo(1);
    }

    @Test
    void changesAtTheSameTimeKeepOnlyTheLastValue() {
        ChannelTrace channel = new ChannelTrace(1);
        channel.append(0, Value.ZERO);
        channel.append(4, Value.ONE);
        channel.append(4, Value.HIGH_IMPEDANCE);
        assertThat(TraceEdges.between(channel.snapshot(), 0, 10))
                .containsExactly(TraceEvent.of(0, Value.ZERO), TraceEvent.of(4, Value.HIGH_IMPEDANCE));

        assertThat(channel.append(4, Value.ZERO)).as("back to the value before: the glitch disappears").isTrue();
        assertThat(TraceEdges.between(channel.snapshot(), 0, 10)).containsExactly(TraceEvent.of(0, Value.ZERO));

        channel.append(4, Value.ONE);
        assertThat(TraceEdges.between(channel.snapshot(), 0, 10))
                .containsExactly(TraceEvent.of(0, Value.ZERO), TraceEvent.of(4, Value.ONE));
    }

    @Test
    void aGapHidesTheValueUntilTheNextEventAndIsIgnoredBeforeTheFirstOne() {
        ChannelTrace channel = new ChannelTrace(1);
        assertThat(channel.appendGap(1)).isFalse();
        channel.append(2, Value.ONE);
        assertThat(channel.appendGap(6)).isTrue();
        assertThat(channel.appendGap(7)).isFalse();
        channel.append(9, Value.ONE);

        Trace trace = channel.snapshot();

        assertThat(trace.valueAt(5)).contains(Value.ONE);
        assertThat(trace.valueAt(6)).isEmpty();
        assertThat(trace.valueAt(8)).isEmpty();
        assertThat(trace.valueAt(9)).contains(Value.ONE);
        assertThat(TraceEdges.between(trace, 0, 9))
                .containsExactly(TraceEvent.of(2, Value.ONE), TraceEvent.gap(6), TraceEvent.of(9, Value.ONE));
        assertThat(TraceEdges.between(trace, 0, 9).get(1).value()).isEmpty();
    }

    @Test
    void edgesAreFoundAcrossChunkBoundaries() {
        Trace trace = alternating(3 * ChannelTrace.CHUNK_SIZE).snapshot();
        long boundary = 10L * ChannelTrace.CHUNK_SIZE; // the first event of the second chunk

        assertThat(trace.previousEdge(boundary)).hasValue(boundary - 10);
        assertThat(trace.previousEdge(boundary + 1)).hasValue(boundary);
        assertThat(trace.nextEdge(boundary - 1)).hasValue(boundary);
        assertThat(trace.nextEdge(boundary)).hasValue(boundary + 10);
        assertThat(trace.previousEdge(0)).isEmpty();
        assertThat(trace.nextEdge(trace.lastTime().getAsLong())).isEmpty();
        assertThat(TraceEdges.between(trace, boundary - 15, boundary + 15)).extracting(TraceEvent::time)
                .containsExactly(boundary - 10, boundary, boundary + 10);
        assertThat(TraceEdges.between(trace, 5, 4)).isEmpty();
    }

    @Test
    void theOldestChunkIsDroppedPastTheCapAndTheTraceSaysSo() {
        int cap = ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS;
        assertThat(cap).isEqualTo(65_536);
        ChannelTrace channel = alternating(cap);
        Trace full = channel.snapshot();
        assertThat(full.eventCount()).isEqualTo(cap);
        assertThat(full.truncated()).isFalse();
        assertThat(full.firstTime()).hasValue(0);

        channel.append(10L * cap, Value.ZERO); // the last event (index cap - 1, odd) was 1

        Trace dropped = channel.snapshot();
        assertThat(dropped.truncated()).isTrue();
        assertThat(dropped.eventCount()).isEqualTo(cap - ChannelTrace.CHUNK_SIZE + 1);
        assertThat(dropped.firstTime()).hasValue(10L * ChannelTrace.CHUNK_SIZE);
        assertThat(dropped.valueAt(5)).isEmpty();
        assertThat(full.eventCount()).as("an older snapshot does not change").isEqualTo(cap);
    }

    @Test
    void snapshotsShareFullChunksAndStayUnchanged() {
        ChannelTrace channel = alternating(ChannelTrace.CHUNK_SIZE + 3);
        Trace earlier = channel.snapshot();
        assertThat(channel.snapshot()).isSameAs(earlier);

        channel.append(1_000_000, Value.HIGH_IMPEDANCE);
        Trace later = channel.snapshot();

        assertThat(later).isNotSameAs(earlier);
        assertThat(later.chunks().get(0)).isSameAs(earlier.chunks().get(0));
        assertThat(earlier.eventCount()).isEqualTo(ChannelTrace.CHUNK_SIZE + 3);
        assertThat(earlier.valueAt(1_000_000)).contains(Value.ZERO);
        assertThat(later.valueAt(1_000_000)).contains(Value.HIGH_IMPEDANCE);
    }

    @Test
    void theWidthFollowsTheBoundNetAndTimeNeverGoesBack() {
        ChannelTrace channel = new ChannelTrace(1);
        channel.setWidth(8);
        channel.append(3, Value.of(8, 0x3F));
        assertThat(channel.snapshot().width()).isEqualTo(8);
        assertThatThrownBy(() -> channel.append(2, Value.of(8, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptySnapshotHasNoTraces() {
        assertThat(WaveformSnapshot.EMPTY.traces()).isEmpty();
        assertThat(WaveformSnapshot.EMPTY.trace("p1")).isEmpty();
        assertThat(WaveformSnapshot.EMPTY.version()).isZero();
    }

    @Test
    void aOneBitConflictIsStoredAsGiven() {
        ChannelTrace channel = new ChannelTrace(1);
        Value conflict = new Value(1, 1L, 0L, true);
        channel.append(1, conflict);

        Value stored = channel.snapshot().valueAt(1).orElseThrow();

        assertThat(stored).isSameAs(conflict).isNotEqualTo(Value.ONE).isNotEqualTo(Value.ZERO);
    }

    @Test
    void oneBitEventsShareTheValueConstants() {
        ChannelTrace channel = new ChannelTrace(1);
        channel.append(1, new Value(1, 1L, 0L, false));
        channel.append(2, new Value(1, 0L, 1L, false));
        channel.append(3, new Value(1, 0L, 0L, false));
        channel.append(4, new Value(1, 1L, 0L, false));

        Trace trace = channel.snapshot();

        assertThat(trace.valueAt(1).orElseThrow()).isSameAs(Value.ONE);
        assertThat(trace.valueAt(4).orElseThrow()).isSameAs(Value.ONE).isSameAs(trace.valueAt(1).orElseThrow());
        assertThat(trace.valueAt(2).orElseThrow()).isSameAs(Value.HIGH_IMPEDANCE);
        assertThat(trace.valueAt(3).orElseThrow()).isSameAs(Value.ZERO);
    }
}
