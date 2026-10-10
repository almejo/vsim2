package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceTest {

    /** Events at 5 (0), 9 (1), 12 (gap) and 20 (0). */
    private static Trace sample() {
        ChannelTrace channel = new ChannelTrace(1);
        channel.append(5, Value.ZERO);
        channel.append(9, Value.ONE);
        channel.appendGap(12);
        channel.append(20, Value.ZERO);
        return channel.snapshot();
    }

    @Test
    void firstIndexAtOrAfterSkipsToTheEarliestEventAtOrAfterATime() {
        Trace trace = sample();

        assertThat(trace.firstIndexAtOrAfter(Long.MIN_VALUE)).isZero();
        assertThat(trace.firstIndexAtOrAfter(5)).isZero();
        assertThat(trace.firstIndexAtOrAfter(6)).isEqualTo(1);
        assertThat(trace.firstIndexAtOrAfter(9)).isEqualTo(1);
        assertThat(trace.firstIndexAtOrAfter(13)).isEqualTo(3);
        assertThat(trace.firstIndexAtOrAfter(21)).isEqualTo(4);
    }

    @Test
    void eventsReadBackByIndex() {
        Trace trace = sample();

        assertThat(trace.eventTime(1)).isEqualTo(9);
        assertThat(trace.eventValue(1)).contains(Value.ONE);
        assertThat(trace.eventTime(2)).isEqualTo(12);
        assertThat(trace.eventValue(2)).isEmpty();
        assertThatThrownBy(() -> trace.eventTime(4)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> trace.eventValue(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void indicesSpanChunks() {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < ChannelTrace.CHUNK_SIZE * 3; index++) {
            channel.append(2L * index, Value.of(index % 2 == 1));
        }
        Trace trace = channel.snapshot();

        int index = trace.firstIndexAtOrAfter(2L * ChannelTrace.CHUNK_SIZE + 1);
        assertThat(index).isEqualTo(ChannelTrace.CHUNK_SIZE + 1);
        assertThat(trace.eventTime(index)).isEqualTo(2L * (ChannelTrace.CHUNK_SIZE + 1));
        assertThat(trace.eventValue(index)).contains(Value.ONE);
    }
}
