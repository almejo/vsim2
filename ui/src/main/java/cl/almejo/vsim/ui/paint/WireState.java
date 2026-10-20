package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.util.Optional;

/**
 * What a wire shows: its value's state ({@link #ONE}, {@link #ZERO}, {@link #HIGH_IMPEDANCE}, {@link #UNKNOWN} for a
 * partly floating bus or a net the snapshot has no value for, {@link #CONFLICT}) or {@link #NO_VALUE} when there are
 * no values to trust at all (no net, or a snapshot of another netlist). On screen the last two share the unknown
 * colour ({@link SignalColors}); «Blanco y negro» draws them differently.
 */
public enum WireState {
    ONE, ZERO, HIGH_IMPEDANCE, UNKNOWN, CONFLICT, NO_VALUE;

    public static WireState of(Value value) {
        if (value.conflict()) {
            return CONFLICT;
        }
        if (value.isFullyHighImpedance()) {
            return HIGH_IMPEDANCE;
        }
        if (value.highImpedanceMask() != 0) {
            return UNKNOWN;
        }
        return value.bits() != 0 ? ONE : ZERO;
    }

    /** The state of a wire on {@code net}; net ids belong to one netlist, see {@link SignalColors#wireColor}. */
    public static WireState of(Optional<Net> net, SimulationSnapshot snapshot, long netlistToken) {
        if (net.isEmpty() || snapshot.token() != netlistToken) {
            return NO_VALUE;
        }
        Value value = snapshot.netValues().get(net.get().id().value());
        return value == null ? UNKNOWN : of(value);
    }

    /** The scheme's colour for this state. */
    public Color color(ColorScheme scheme) {
        return switch (this) {
            case ONE -> scheme.wireOne();
            case ZERO -> scheme.wireZero();
            case HIGH_IMPEDANCE -> scheme.wireHighImpedance();
            case CONFLICT -> scheme.wireConflict();
            case UNKNOWN, NO_VALUE -> scheme.wireUnknown();
        };
    }
}
