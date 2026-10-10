package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The waveform channels of one engine (spec §3.3). Each channel id has a {@link ChannelTrace} that outlives the
 * {@link RuntimeNet}s it watches: nets are rebuilt on every update, so {@link #rebind} hangs the traces on the
 * current nets again and samples every channel, which also records changes that happened without netChanged.
 * Single-threaded, like the engine.
 */
final class WaveformRecorder {

    private Map<String, String> channels = Map.of();
    private final Map<String, ChannelTrace> traces = new HashMap<>();
    private long version;

    /** Channel id → net id. Channels left out lose their trace; the others keep it, even under another net id. */
    void setChannels(Map<String, String> newChannels) {
        if (channels.equals(newChannels)) {
            return;
        }
        channels = Map.copyOf(newChannels);
        traces.keySet().retainAll(channels.keySet());
        version++;
    }

    /** Drops every recorded event; the next {@link #rebind} starts each channel at its current value. */
    void clear() {
        traces.clear();
        version++;
    }

    /**
     * Attaches every channel to the net {@code netWithId} finds for its net id (aliases already resolved to the
     * root) and samples it at {@code now}: a channel without a net records a gap.
     */
    void rebind(Function<String, RuntimeNet> netWithId, Collection<RuntimeNet> nets, long now) {
        for (RuntimeNet net : nets) {
            if (!net.recorders.isEmpty()) {
                net.recorders = List.of();
            }
        }
        channels.forEach((channelId, netId) -> {
            RuntimeNet net = netId.isEmpty() ? null : netWithId.apply(netId);
            ChannelTrace trace = traces.computeIfAbsent(channelId, id -> new ChannelTrace(net == null ? 1 : net.width));
            boolean changed;
            if (net == null) {
                changed = trace.appendGap(now);
            } else {
                if (net.recorders.isEmpty()) {
                    net.recorders = new ArrayList<>(1);
                }
                net.recorders.add(trace);
                trace.setWidth(net.width);
                changed = trace.append(now, net.value);
            }
            if (changed) {
                version++;
            }
        });
    }

    /** {@code net} just changed value at {@code now} (called only when it has recorders). */
    void record(RuntimeNet net, long now) {
        for (ChannelTrace trace : net.recorders) {
            if (trace.append(now, net.value)) {
                version++;
            }
        }
    }

    long version() {
        return version;
    }

    boolean hasChannels() {
        return !channels.isEmpty();
    }

    WaveformSnapshot snapshot(long now) {
        Map<String, Trace> published = new HashMap<>();
        traces.forEach((channelId, trace) -> published.put(channelId, trace.snapshot()));
        return new WaveformSnapshot(version, now, published);
    }
}
