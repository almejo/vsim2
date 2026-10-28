package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.OptionalInt;

/**
 * Where the live simulation shows a table's inputs (spec §5.4): one top-level net id per input column, valid for the
 * circuit of structure revision {@code token} ({@link cl.almejo.vsim.ui.app.OpenDocument#structureRevision()}: the
 * netlist they were resolved against, whatever values its switches were clicked to since).
 */
record LiveInputs(List<String> nets, long token) {

    LiveInputs {
        nets = List.copyOf(nets);
    }

    /**
     * The nets of {@code pins} in {@code netlist}, compiled from a circuit of structure revision {@code token}. A pin
     * outside every net reads the engine's own net of that pin ({@code p:<component>#<pin>}).
     */
    static LiveInputs resolve(List<PinRef> pins, Netlist netlist, long token) {
        return new LiveInputs(pins.stream()
                .map(pin -> netlist.netForPin(pin)
                        .map(net -> net.id().value())
                        .orElse("p:" + pin.componentId().value() + "#" + pin.pinIndex()))
                .toList(), token);
    }

    /**
     * The row of {@code table} whose inputs are the values {@code snapshot} shows on the nets; empty when the circuit
     * changed since the table was computed (its structure revision now, {@code structureRevision}, is another one, or
     * the snapshot is older than it) or an input is Z or in conflict. A click on a switch does not change it.
     */
    OptionalInt rowIn(TruthTable table, SimulationSnapshot snapshot, long structureRevision) {
        if (structureRevision != token || snapshot.token() < token) {
            return OptionalInt.empty();
        }
        List<Value> values = nets.stream().map(snapshot::netValue).toList();
        return table.rowOf(values);
    }
}
