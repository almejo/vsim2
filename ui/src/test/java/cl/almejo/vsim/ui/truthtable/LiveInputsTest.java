package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link LiveInputs}: the row of the live input values (spec §5.4), read from fake snapshots. */
class LiveInputsTest {

    /** Inputs A and B, four rows counting in binary. */
    private final TruthTable table = new TruthTable(List.of(new Column("A", "a", 1), new Column("B", "b", 1)),
            List.of(new Column("Y", "y", 1)),
            List.of(row(Value.ZERO, Value.ZERO), row(Value.ZERO, Value.ONE), row(Value.ONE, Value.ZERO),
                    row(Value.ONE, Value.ONE)),
            false);

    private static Row row(Value first, Value second) {
        return new Row(List.of(first, second), List.of(Value.ZERO), true);
    }

    private static SimulationSnapshot snapshot(long token, Map<String, Value> netValues) {
        return new SimulationSnapshot(1, token, 0, false, netValues, Map.of(), List.of(), Optional.empty());
    }

    @Test
    void theRowFollowsTheValuesOfTheInputNetsOfTheSameCircuit() {
        LiveInputs live = new LiveInputs(List.of("n1", "n2"), 7);

        assertThat(live.rowIn(table, snapshot(7, Map.of("n1", Value.ONE, "n2", Value.ZERO)), 7)).hasValue(2);
        assertThat(live.rowIn(table, snapshot(7, Map.of("n1", Value.ZERO, "n2", Value.ONE)), 7)).hasValue(1);
        assertThat(live.rowIn(table, snapshot(7, Map.of("n1", Value.ONE)), 7)).as("n2 has no value: Z").isEmpty();
        assertThat(live.rowIn(table, snapshot(8, Map.of("n1", Value.ONE, "n2", Value.ONE)), 8))
                .as("the circuit changed since the table was computed").isEmpty();
        assertThat(live.rowIn(table, snapshot(6, Map.of("n1", Value.ONE, "n2", Value.ONE)), 7))
                .as("a snapshot of the circuit before").isEmpty();
    }

    @Test
    void clickedSwitchesKeepTheCircuitOfTheTable() {
        LiveInputs live = new LiveInputs(List.of("n1", "n2"), 7);

        assertThat(live.rowIn(table, snapshot(9, Map.of("n1", Value.ONE, "n2", Value.ONE)), 7)).hasValue(3);
    }

    @Test
    void aPinOutsideEveryNetReadsTheEnginesOwnNetOfThatPin() {
        LiveInputs live = LiveInputs.resolve(List.of(new PinRef(new ElementId("c4"), 2)), Netlist.EMPTY, 3);

        assertThat(live).isEqualTo(new LiveInputs(List.of("p:c4#2"), 3));
    }
}
