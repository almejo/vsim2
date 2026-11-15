package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineChannels;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.machine.StateMachineTypes;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.ChannelRows;
import cl.almejo.vsim.ui.waveform.WaveformChannels;
import cl.almejo.vsim.ui.window.InstanceNames;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * The wave strip of a machine tab (hito 2, decision 2): the channels the root circuit records for the copy entered
 * through ({@link MachineChannels}), as the mockup draws them under the diagram: its inputs, «estado» written with the
 * state names, then its outputs. They are recorded from «Reiniciar» whether the tab is open or not, so entering after
 * simulating already shows what happened. The rows cannot be edited. With «Sin valores» there is no copy and no row,
 * and the title says how to get one. Everything is read from the place when asked, so another copy («Valores de»),
 * an edit of the machine or a new label shows at once.
 */
final class MachineStripChannels implements WaveformChannels {

    private final OpenDocument place;
    private final String definitionId;
    private final List<Action> headerActions;

    MachineStripChannels(OpenDocument place, String definitionId, List<Action> headerActions) {
        this.place = Objects.requireNonNull(place, "place");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.headerActions = List.copyOf(headerActions);
    }

    @Override
    public List<ChannelRows.ChannelRow> rows() {
        Optional<String> copy = copy();
        Optional<StateMachine> machine = machine();
        if (copy.isEmpty() || machine.isEmpty()) {
            return List.of();
        }
        String path = copy.get();
        List<ChannelRows.ChannelRow> rows = new ArrayList<>();
        for (String input : machine.get().inputNames()) {
            rows.add(row(MachineChannels.inputChannelId(path, input), input));
        }
        rows.add(row(MachineChannels.stateChannelId(path), Messages.text("machine.strip.state")));
        for (String output : machine.get().outputNames()) {
            rows.add(row(MachineChannels.outputChannelId(path, output), output));
        }
        return List.copyOf(rows);
    }

    private static ChannelRows.ChannelRow row(String channelId, String name) {
        return new ChannelRows.ChannelRow(channelId, name, true, Probe.FORMAT_HEX);
    }

    @Override
    public Object rowsKey() {
        return new RowsKey(copy(), machine(), circuit().document().revision());
    }

    /** What the rows and the title are made of: the copy, the machine's pins, the names along the copy's route. */
    private record RowsKey(Optional<String> copy, Optional<StateMachine> machine, long revision) {
    }

    @Override
    public WaveformSnapshot latest() {
        return place.latestWaveforms();
    }

    @Override
    public boolean hasChannels() {
        return copy().isPresent() && machine().isPresent();
    }

    /** The state row writes each code as the name of the state that has it now (an edit renames the history too). */
    @Override
    public Optional<LongFunction<String>> valueNames(String channelId) {
        Optional<String> copy = copy();
        if (copy.isEmpty() || !channelId.equals(MachineChannels.stateChannelId(copy.get()))) {
            return Optional.empty();
        }
        return Optional.of(this::stateName);
    }

    private String stateName(long code) {
        return machine().flatMap(machine -> StateMachineTypes.stateIdOfCode(machine, code).flatMap(machine::state))
                .map(MachineState::name)
                .orElseGet(() -> Long.toString(code));
    }

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    public Optional<String> title() {
        return Optional.of(copy()
                .map(path -> Messages.format("machine.strip.title",
                        InstanceNames.routeText(path, circuit().document())))
                .orElseGet(() -> Messages.text("machine.strip.noCopy")));
    }

    @Override
    public List<Action> headerActions() {
        return headerActions;
    }

    /** The copy entered through: the place's context path; empty with «Sin valores». */
    private Optional<String> copy() {
        return place.contextPath();
    }

    private Optional<StateMachine> machine() {
        return circuit().document().definition(definitionId).flatMap(ComponentDefinition::machine);
    }

    private OpenDocument circuit() {
        return place.parent().orElse(place);
    }
}
