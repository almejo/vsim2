package cl.almejo.vsim.core.waveform;

import java.util.Map;
import java.util.Optional;

/**
 * The recorded waveforms at simulation time {@code time}: one {@link Trace} per channel id. {@code version} grows
 * whenever a trace or the set of channels changed. Immutable; traces share their full chunks with older snapshots.
 */
public record WaveformSnapshot(long version, long time, Map<String, Trace> traces) {

    public static final WaveformSnapshot EMPTY = new WaveformSnapshot(0, 0, Map.of());

    public WaveformSnapshot {
        traces = Map.copyOf(traces);
    }

    public Optional<Trace> trace(String channelId) {
        return Optional.ofNullable(traces.get(channelId));
    }
}
