package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongFunction;

/** The rows of a waveform panel: one per probe, in order, with the name to show and whether the probe resolves. */
public final class ChannelRows {

    public record ChannelRow(String probeId, String name, boolean connected, String format) {

        public ChannelRow(String probeId, String name, boolean connected) {
            this(probeId, name, connected, Probe.FORMAT_HEX);
        }
    }

    /** What a named row writes while its value is Z: a machine copy with no state. */
    static final String NO_NAME = "—";

    private ChannelRows() {
    }

    /** {@code names} gives each probe the name its row shows ({@link ProbeNames#displayName} in the panel). */
    static List<ChannelRow> of(List<Probe> probes, Map<String, String> probeNets, Function<Probe, String> names) {
        return probes.stream()
                .map(probe -> new ChannelRow(probe.id(), names.apply(probe), probeNets.containsKey(probe.id()),
                        probe.format()))
                .toList();
    }

    /** {@code 1}, {@code 0}, {@code Z} or {@code X} for one bit; {@code 0x…} (or {@code X}) for a bus; nothing outside the recording. */
    static String valueText(Optional<Value> value) {
        return valueText(value, Probe.FORMAT_HEX);
    }

    /**
     * As {@link #valueText(Optional, String)}, but a row with {@code names} (a machine's state row) writes the name
     * of each value, 1-bit too: «S2», {@link #NO_NAME} while any bit is Z (no state), {@code X} in conflict. A null
     * {@code names} is a plain row.
     */
    static String valueText(Optional<Value> value, String format, LongFunction<String> names) {
        if (names == null || value.isEmpty()) {
            return valueText(value, format);
        }
        Value present = value.get();
        if (present.conflict()) {
            return BusValueText.CONFLICT;
        }
        return present.isFullyDefined() ? names.apply(present.bits()) : NO_NAME;
    }

    /** As {@link #valueText(Optional)}, a bus in the probe's {@code format} ({@link BusValueText#of(Value, String)}). */
    static String valueText(Optional<Value> value, String format) {
        if (value.isEmpty()) {
            return "";
        }
        Value present = value.get();
        if (present.width() > 1) {
            return BusValueText.of(present, format);
        }
        if (present.conflict()) {
            return BusValueText.CONFLICT;
        }
        return switch (present.bit(0)) {
            case ZERO -> "0";
            case ONE -> "1";
            case HIGH_IMPEDANCE -> "Z";
        };
    }
}
