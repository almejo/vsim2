package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Map;

/** Absolute destinations of pins, planned together against the original circuit. Unlisted pins stay put. */
public record PinMoves(Map<PinRef, GridPoint> after) {
    public PinMoves {
        after = Map.copyOf(after);
    }
}
