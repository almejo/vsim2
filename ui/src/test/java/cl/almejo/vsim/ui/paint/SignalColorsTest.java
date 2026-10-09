package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignalColorsTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final Net NET = new Net(new NetId("n1_1"), Set.of(), Set.of(new ElementId("w1")),
            Set.of(new GridPoint(1, 1)), 1, false);

    private static SimulationSnapshot snapshot(long token, Value value) {
        return new SimulationSnapshot(1, token, 0, true, Map.of("n1_1", value), Map.of(), List.of(), Optional.empty());
    }

    @Test
    void valuesMapToTheirSchemeColors() {
        assertThat(SignalColors.valueColor(Value.ONE, SCHEME)).isEqualTo(SCHEME.wireOne());
        assertThat(SignalColors.valueColor(Value.ZERO, SCHEME)).isEqualTo(SCHEME.wireZero());
        assertThat(SignalColors.valueColor(Value.HIGH_IMPEDANCE, SCHEME)).isEqualTo(SCHEME.wireHighImpedance());
        assertThat(SignalColors.valueColor(Value.ONE.withConflict(), SCHEME)).isEqualTo(SCHEME.wireConflict());
    }

    @Test
    void wireTakesTheValueOfItsNetWhenTheSnapshotMatchesTheNetlist() {
        assertThat(SignalColors.wireColor(Optional.of(NET), snapshot(7, Value.ONE), 7, SCHEME)).isEqualTo(SCHEME.wireOne());
    }

    @Test
    void staleSnapshotPaintsWiresInTheUnknownColor() {
        assertThat(SignalColors.wireColor(Optional.of(NET), snapshot(6, Value.ONE), 7, SCHEME)).isEqualTo(SCHEME.wireUnknown());
    }

    @Test
    void wireWithoutNetOrValueIsUnknown() {
        assertThat(SignalColors.wireColor(Optional.empty(), snapshot(7, Value.ONE), 7, SCHEME)).isEqualTo(SCHEME.wireUnknown());
        SimulationSnapshot empty = new SimulationSnapshot(1, 7, 0, true, Map.of(), Map.of(), List.of(), Optional.empty());
        assertThat(SignalColors.wireColor(Optional.of(NET), empty, 7, SCHEME)).isEqualTo(SCHEME.wireUnknown());
    }
}
