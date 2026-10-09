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
        if (value.conflict()) {
            return scheme.wireConflict();
        }
        if (value.isFullyHighImpedance()) {
            return scheme.wireHighImpedance();
        }
        if (value.highImpedanceMask() != 0) {
            return scheme.wireUnknown();
        }
        return value.bits() != 0 ? scheme.wireOne() : scheme.wireZero();
    }

    /**
     * Color of a wire on {@code net}. Net ids belong to one netlist: when the snapshot was produced for another
     * netlist (its token differs) the value cannot be trusted and the wire is drawn in the unknown color.
     */
    public static Color wireColor(Optional<Net> net, SimulationSnapshot snapshot, long netlistToken, ColorScheme scheme) {
        if (net.isEmpty() || snapshot.token() != netlistToken) {
            return scheme.wireUnknown();
        }
        Value value = snapshot.netValues().get(net.get().id().value());
        return value == null ? scheme.wireUnknown() : valueColor(value, scheme);
    }
}
