package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record Net(NetId id, Set<PinRef> pins, Set<ElementId> wires, Set<GridPoint> points,
                  int width, boolean widthMismatch) {
    public Net {
        pins = Set.copyOf(pins);
        wires = Set.copyOf(wires);
        points = Set.copyOf(points);
    }

    /** The pins whose component is still in {@code document}, in the document's order of the parts, then pin order. */
    public List<PinRef> pinsInOrderOf(CircuitDocument document) {
        Comparator<PinRef> order = Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                .thenComparingInt(PinRef::pinIndex);
        return pins.stream().filter(pin -> document.component(pin.componentId()).isPresent()).sorted(order).toList();
    }
}
