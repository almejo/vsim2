package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Set;

public record Net(NetId id, Set<PinRef> pins, Set<ElementId> wires, Set<GridPoint> points,
                  int width, boolean widthMismatch) {
    public Net {
        pins = Set.copyOf(pins);
        wires = Set.copyOf(wires);
        points = Set.copyOf(points);
    }
}
