package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.Action;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * The document's probes as a panel's rows (spec §3.2): one row per probe, in order, named by {@code names}, «sin
 * conexión» when it does not resolve; every row can be edited. The rows never include a machine copy's own channels,
 * which the recording also holds.
 */
final class ProbeChannels implements WaveformChannels {

    private final OpenDocument openDocument;
    private final Function<Probe, String> names;

    ProbeChannels(OpenDocument openDocument, Function<Probe, String> names) {
        this.openDocument = openDocument;
        this.names = names;
    }

    @Override
    public List<ChannelRows.ChannelRow> rows() {
        return ChannelRows.of(openDocument.document().probes(), openDocument.probeNets(), names);
    }

    @Override
    public Object rowsKey() {
        return new RowsKey(openDocument.document().probes(), openDocument.probeNets(),
                openDocument.document().revision(), openDocument.netlistToken());
    }

    /** What the rows depend on: a label edit renames a live default name, a new netlist (dis)connects a probe. */
    private record RowsKey(List<Probe> probes, Map<String, String> probeNets, long revision, long netlistToken) {
    }

    @Override
    public WaveformSnapshot latest() {
        return openDocument.latestWaveforms();
    }

    @Override
    public boolean hasChannels() {
        return !openDocument.document().probes().isEmpty();
    }

    @Override
    public Optional<LongFunction<String>> valueNames(String channelId) {
        return Optional.empty();
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public Optional<String> title() {
        return Optional.empty();
    }

    @Override
    public List<Action> headerActions() {
        return List.of();
    }
}
