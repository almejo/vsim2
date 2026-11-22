package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The parking of channels left out, bounded by the events their traces hold (not only by how many there are). */
class WaveformRecorderTest {

    private final RuntimeNet net = new RuntimeNet("n1", 1, false);
    private long now;

    /** Watches {@code channelId} alone (leaving out the one before) and records {@code toggles} changes on it. */
    private void recordAlone(WaveformRecorder recorder, String channelId, int toggles) {
        recorder.setChannels(Map.of(channelId, net.id), List.of(net), now);
        recorder.rebind(netId -> net, List.of(net), now);
        for (int toggle = 0; toggle < toggles; toggle++) {
            now++;
            net.value = toggle % 2 == 0 ? Value.ONE : Value.ZERO;
            recorder.record(net, now);
        }
        now++;
    }

    private long firstTime(WaveformRecorder recorder, String channelId) {
        return recorder.snapshot(now).trace(channelId).orElseThrow().firstTime().orElseThrow();
    }

    @Test
    void theDefaultBoundIsAFewFullTracesWorthOfEvents() {
        assertThat(WaveformRecorder.MAXIMUM_PARKED_EVENTS)
                .isEqualTo(4 * ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS);
    }

    @Test
    void pastTheEventBoundTheOldestParkedTracesAreDroppedFirst() {
        WaveformRecorder recorder = new WaveformRecorder(20);
        recordAlone(recorder, "first", 8);    // 1 initial + 8 changes, + 1 gap when parked = 10 events
        long secondStart = now;
        recordAlone(recorder, "second", 8);   // parks «first»: 10 parked
        long thirdStart = now;
        recordAlone(recorder, "third", 8);    // parks «second»: 20 parked, still within the bound
        recordAlone(recorder, "fourth", 0);   // parks «third»: 30 parked, so «first» goes

        recorder.setChannels(Map.of("first", net.id, "second", net.id, "third", net.id), List.of(net), now);
        recorder.rebind(netId -> net, List.of(net), now);

        assertThat(firstTime(recorder, "first")).as("the oldest parked was forgotten").isEqualTo(now);
        assertThat(firstTime(recorder, "second")).isEqualTo(secondStart);
        assertThat(firstTime(recorder, "third")).isEqualTo(thirdStart);
    }
}
