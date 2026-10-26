package cl.almejo.vsim.core.waveform;

import java.util.ArrayList;
import java.util.List;

/** Test helper: the events of a trace inside a time window, read through its public index API. */
public final class TraceEdges {

    private TraceEdges() {
    }

    /** The events with {@code from <= time <= to}, oldest first. */
    public static List<TraceEvent> between(Trace trace, long from, long to) {
        List<TraceEvent> events = new ArrayList<>();
        if (from > to) {
            return events;
        }
        for (int index = trace.firstIndexAtOrAfter(from); index < trace.eventCount()
                && trace.eventTime(index) <= to; index++) {
            events.add(new TraceEvent(trace.eventTime(index), trace.eventValue(index)));
        }
        return events;
    }
}
