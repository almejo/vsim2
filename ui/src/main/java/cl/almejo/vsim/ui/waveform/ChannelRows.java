package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The rows of a waveform panel: one per probe, in order, with the name to show and whether the probe resolves. */
final class ChannelRows {

    record ChannelRow(String probeId, String name, boolean connected) {
    }

    private ChannelRows() {
    }

    /** {@code names} gives each probe the name its row shows ({@link ProbeNames#displayName} in the panel). */
    static List<ChannelRow> of(List<Probe> probes, Map<String, String> probeNets, Function<Probe, String> names) {
        return probes.stream()
                .map(probe -> new ChannelRow(probe.id(), names.apply(probe), probeNets.containsKey(probe.id())))
                .toList();
    }

    /** {@code 1}, {@code 0}, {@code Z} or {@code X} for one bit; {@code 0x…} (or {@code X}) for a bus; nothing outside the recording. */
    static String valueText(Optional<Value> value) {
        if (value.isEmpty()) {
            return "";
        }
        Value present = value.get();
        if (present.width() > 1) {
            return BusValueText.of(present);
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
