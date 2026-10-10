package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationThreadWaveformsTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final long PUBLISH_INTERVAL = 16 * MILLISECOND;

    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    private static FlatCircuit singleSource() {
        ComponentType source = TestComponentTypes.registry().require(TestComponentTypes.SOURCE);
        return new FlatCircuit(List.of(new FlatComponent("s", source, Map.of("value", "0"))),
                List.of(new FlatNet("out", List.of(new FlatPinRef("s", 0)), 1, false)));
    }

    @Test
    void probesArePublishedAtTheNextTickAndOnlyRepublishedWhenSomethingChanged() {
        thread.update(singleSource());
        thread.tick();
        assertThat(thread.latestWaveforms()).as("no channels yet").isSameAs(WaveformSnapshot.EMPTY);

        thread.setProbes(Map.of("a", "out"));
        assertThat(thread.latestWaveforms()).isSameAs(WaveformSnapshot.EMPTY);
        thread.tick();
        WaveformSnapshot first = thread.latestWaveforms();
        assertThat(first.trace("a").orElseThrow().valueAt(0)).contains(Value.ZERO);

        nanos.addAndGet(PUBLISH_INTERVAL);
        thread.tick();
        assertThat(thread.latestWaveforms()).as("paused and unchanged").isSameAs(first);

        thread.interact("s");
        nanos.addAndGet(PUBLISH_INTERVAL);
        thread.tick();
        assertThat(thread.latestWaveforms().trace("a").orElseThrow().valueAt(0)).contains(Value.ONE);
        assertThat(thread.latestWaveforms().version()).isGreaterThan(first.version());
    }

    @Test
    void snapshotsArePublishedAtMostEvery16MillisecondsAndAPendingChangeIsPublishedLater() {
        thread.update(singleSource());
        thread.setProbes(Map.of("a", "out"));
        thread.tick();
        WaveformSnapshot first = thread.latestWaveforms();
        assertThat(first.trace("a")).isPresent();

        thread.interact("s");
        nanos.addAndGet(15 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestWaveforms()).as("too soon").isSameAs(first);

        nanos.addAndGet(MILLISECOND);
        thread.tick();
        assertThat(thread.latestWaveforms().trace("a").orElseThrow().valueAt(0)).as("published without new requests")
                .contains(Value.ONE);
    }

    @Test
    void whileRunningTheSnapshotFollowsTheTimeEvenWithoutEvents() {
        thread.update(singleSource());
        thread.setProbes(Map.of("a", "out"));
        thread.resume();
        thread.tick();
        nanos.addAndGet(PUBLISH_INTERVAL);
        thread.tick();
        assertThat(thread.latestWaveforms().time()).isEqualTo(16);
        assertThat(thread.latestWaveforms().trace("a").orElseThrow().eventCount()).isEqualTo(1);
    }

    @Test
    void clearingRestartsTheTracesAtTheCurrentTime() {
        thread.update(singleSource());
        thread.setProbes(Map.of("a", "out"));
        thread.resume();
        thread.tick();
        nanos.addAndGet(PUBLISH_INTERVAL);
        thread.tick();

        thread.clearWaveforms();
        nanos.addAndGet(PUBLISH_INTERVAL);
        thread.tick();

        assertThat(thread.latestWaveforms().trace("a").orElseThrow().firstTime()).hasValue(16);
    }

    @Test
    void theCircuitAndItsChannelsAreOneRequestSoNoTickRunsBetweenThem() {
        thread.update(singleSource(), Map.of("a", "out"));
        assertThat(thread.pendingRequests()).isEqualTo(1);

        thread.tick();

        assertThat(thread.latestSnapshot().netValue("out")).isEqualTo(Value.ZERO);
        assertThat(thread.latestWaveforms().trace("a").orElseThrow().valueAt(0)).contains(Value.ZERO);
    }

    @Test
    void theChannelsAreBoundEvenWhenTheCircuitIsRejected() {
        FlatCircuit broken = new FlatCircuit(List.of(),
                List.of(new FlatNet("bad", List.of(new FlatPinRef("missing", 0)), 1, false)));
        thread.update(broken, Map.of("a", ""));
        thread.tick();

        assertThat(thread.latestSnapshot().failure()).isPresent();
        assertThat(thread.latestWaveforms().trace("a")).isPresent();
    }
}
