package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.util.Optional;

public final class SignalColors {

    private SignalColors() {
    }

    public static Color valueColor(Value value, ColorScheme scheme) {
        return WireState.of(value).color(scheme);
    }

    /**
     * Color of a wire on {@code net}. Net ids belong to one netlist: when the snapshot was produced for another
     * netlist (its token differs) the value cannot be trusted and the wire is drawn in the unknown color.
     */
    public static Color wireColor(Optional<Net> net, SimulationSnapshot snapshot, long netlistToken, ColorScheme scheme) {
        return WireState.of(net, snapshot, netlistToken).color(scheme);
    }
}
