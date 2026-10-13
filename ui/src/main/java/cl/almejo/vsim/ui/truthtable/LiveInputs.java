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
 * circuit of revision {@code token} (the netlist they were resolved against).
 */
record LiveInputs(List<String> nets, long token) {

    LiveInputs {
        nets = List.copyOf(nets);
    }

    /**
     * The nets of {@code pins} in {@code netlist}, compiled from document revision {@code token}. A pin outside every
     * net reads the engine's own net of that pin ({@code p:<component>#<pin>}).
     */
    static LiveInputs resolve(List<PinRef> pins, Netlist netlist, long token) {
        return new LiveInputs(pins.stream()
                .map(pin -> netlist.netForPin(pin)
                        .map(net -> net.id().value())
                        .orElse("p:" + pin.componentId().value() + "#" + pin.pinIndex()))
                .toList(), token);
    }

    /**
     * The row of {@code table} whose inputs are the values {@code snapshot} shows on the nets; empty when the snapshot
     * comes from another circuit (an edit since the table was computed) or an input is Z or in conflict.
     */
    OptionalInt rowIn(TruthTable table, SimulationSnapshot snapshot) {
        if (snapshot.token() != token) {
            return OptionalInt.empty();
        }
        List<Value> values = nets.stream().map(snapshot::netValue).toList();
        return table.rowOf(values);
    }
}
