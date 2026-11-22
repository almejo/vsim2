package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The waveform channels of one engine (spec §3.3). Each channel id has a {@link ChannelTrace} that outlives the
 * {@link RuntimeNet}s it watches: nets are rebuilt on every update, so {@link #rebind} hangs the traces on the
 * current nets again and samples every channel, which also records changes that happened without netChanged.
 * A channel left out is parked with its trace (a gap from that moment) and gets it back when a channel with its id
 * comes again, whatever left it out (a deleted part, a removed signal, then Undo); only the most recently parked are
 * kept, at most {@link #MAXIMUM_PARKED_CHANNELS} of them holding at most {@link #MAXIMUM_PARKED_EVENTS} events in all.
 * Single-threaded, like the engine.
 */
final class WaveformRecorder {

    /** How many channels left out keep their trace, waiting to come back; past it the oldest parked is dropped. */
    static final int MAXIMUM_PARKED_CHANNELS = 64;
    /**
     * How many events the parked traces may hold in all (about four full traces' worth, a few MB of bus values at
     * most); past it the oldest parked are dropped.
     */
    static final int MAXIMUM_PARKED_EVENTS = 4 * ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS;

    private final int maximumParkedEvents;

    WaveformRecorder() {
        this(MAXIMUM_PARKED_EVENTS);
    }

    /** {@code maximumParkedEvents}: the events bound, smaller in tests. */
    WaveformRecorder(int maximumParkedEvents) {
        this.maximumParkedEvents = maximumParkedEvents;
    }

    private Map<String, String> channels = Map.of();
    private final Map<String, ChannelTrace> traces = new HashMap<>();
    /** Traces of channels left out, oldest parked first. */
    private final Map<String, ChannelTrace> parked = new LinkedHashMap<>();
    private long version;

    /**
     * Channel id → net id. The others keep their trace, even under another net id; a channel left out is parked at
     * {@code now} (its trace gets a gap there and leaves the {@code nets} it hung on, so nothing records into it
     * until {@link #rebind}) and a parked channel that comes back takes its trace again.
     */
    void setChannels(Map<String, String> newChannels, Collection<RuntimeNet> nets, long now) {
        if (channels.equals(newChannels)) {
            return;
        }
        channels = Map.copyOf(newChannels);
        List<ChannelTrace> leaving = new ArrayList<>();
        Iterator<Map.Entry<String, ChannelTrace>> current = traces.entrySet().iterator();
        while (current.hasNext()) {
            Map.Entry<String, ChannelTrace> entry = current.next();
            if (!channels.containsKey(entry.getKey())) {
                current.remove();
                leaving.add(entry.getValue());
                park(entry.getKey(), entry.getValue(), now);
            }
        }
        if (!leaving.isEmpty()) {
            for (RuntimeNet net : nets) {
                if (!net.recorders.isEmpty()) {
                    List<ChannelTrace> staying = net.recorders.stream()
                            .filter(trace -> leaving.stream().noneMatch(parkedTrace -> parkedTrace == trace))
                            .toList();
                    net.recorders = staying.isEmpty() ? List.of() : new ArrayList<>(staying);
                }
            }
        }
        channels.keySet().forEach(channelId -> {
            ChannelTrace back = parked.remove(channelId);
            if (back != null && !traces.containsKey(channelId)) {
                traces.put(channelId, back);
            }
        });
        dropOldestParked();
        version++;
    }

    private void park(String channelId, ChannelTrace trace, long now) {
        trace.appendGap(now);
        parked.remove(channelId);
        parked.put(channelId, trace);
    }

    /** Drops the oldest parked traces until both bounds hold (after the returning channels took theirs back). */
    private void dropOldestParked() {
        long parkedEvents = parked.values().stream().mapToLong(ChannelTrace::eventCount).sum();
        Iterator<ChannelTrace> oldest = parked.values().iterator();
        while (parked.size() > MAXIMUM_PARKED_CHANNELS || parkedEvents > maximumParkedEvents) {
            parkedEvents -= oldest.next().eventCount();
            oldest.remove();
        }
    }

    /**
     * Drops every recorded event, the parked channels' too; the next {@link #rebind} starts each channel at its
     * current value.
     */
    void clear() {
        traces.clear();
        parked.clear();
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
