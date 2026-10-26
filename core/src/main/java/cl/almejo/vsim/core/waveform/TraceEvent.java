package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;

import java.util.Objects;
import java.util.Optional;

/** A change of a channel at {@code time}: its new value, or empty for a gap (the net it watched disappeared). */
public record TraceEvent(long time, Optional<Value> value) {

    public TraceEvent {
        Objects.requireNonNull(value, "value");
    }

    public static TraceEvent of(long time, Value value) {
        return new TraceEvent(time, Optional.of(value));
    }

    public static TraceEvent gap(long time) {
        return new TraceEvent(time, Optional.empty());
    }
}
