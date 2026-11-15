package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachineTypes;
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
 * conexión» when it does not resolve; every row can be edited. A probe on a machine's state is written with the
 * state names. The rows never include a machine copy's own channels, which the recording also holds.
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

    /** A probe on a machine's state writes each code as the name of the state that has it now. */
    @Override
    public Optional<LongFunction<String>> valueNames(String channelId) {
        return openDocument.document().probes().stream()
                .filter(probe -> probe.id().equals(channelId))
                .findFirst()
                .filter(probe -> probe.target() instanceof ProbeTarget.MachineStateTarget)
                .map(probe -> code -> stateName(((ProbeTarget.MachineStateTarget) probe.target()).componentId(),
                        code));
    }

    /** The name of the state whose code is {@code code} in the machine of piece {@code componentId}, read now. */
    private String stateName(ElementId componentId, long code) {
        CircuitDocument document = openDocument.document();
        return document.component(componentId)
                .flatMap(component -> SubcircuitTypes.definitionIdOf(component.typeId()))
                .flatMap(document::definition)
                .flatMap(ComponentDefinition::machine)
                .flatMap(machine -> StateMachineTypes.stateIdOfCode(machine, code).flatMap(machine::state))
                .map(MachineState::name)
                .orElseGet(() -> Long.toString(code));
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
