package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only geometry of a list of wires over the pins of a document, with the same connection semantics as
 * {@code NetlistCompiler}: a pin or a wire end connects every wire that contains that point (end or interior); the
 * interiors of two wires that cross do not connect.
 */
public final class WireGraph {

    private final List<Wire> wires;
    private final Map<GridPoint, List<PinRef>> pinsAtPoint;

    private WireGraph(List<Wire> wires, Map<GridPoint, List<PinRef>> pinsAtPoint) {
        this.wires = List.copyOf(wires);
        this.pinsAtPoint = pinsAtPoint;
    }

    public static WireGraph of(List<Wire> wires, CircuitDocument document, ComponentTypeRegistry registry) {
        return new WireGraph(wires, pinLocations(document, registry));
    }

    /** The grid point of every top-level pin; computed once per document when many graphs are built. */
    static Map<GridPoint, List<PinRef>> pinLocations(CircuitDocument document, ComponentTypeRegistry registry) {
        Map<GridPoint, List<PinRef>> pinsAtPoint = new HashMap<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                pinsAtPoint.computeIfAbsent(type.pinLocation(component, pinIndex), point -> new ArrayList<>())
                        .add(new PinRef(component.id(), pinIndex));
            }
        }
        return pinsAtPoint;
    }

    static WireGraph of(List<Wire> wires, Map<GridPoint, List<PinRef>> pinsAtPoint) {
        return new WireGraph(wires, pinsAtPoint);
    }

    public List<Wire> wires() {
        return wires;
    }

    public List<PinRef> pinsAt(GridPoint point) {
        return pinsAtPoint.getOrDefault(point, List.of());
    }

    /** Wires that have {@code point} at an end or in their interior, in list order. */
    public List<Wire> wiresThrough(GridPoint point) {
        return wires.stream().filter(wire -> wire.contains(point)).toList();
    }

    /** True when {@code point} is neither end of {@code wire} but lies on it. */
    public static boolean isInterior(Wire wire, GridPoint point) {
        return wire.contains(point) && !point.equals(wire.start()) && !point.equals(wire.end());
    }

    /**
     * The points of {@code wire} (ends included) where it really joins something else: a pin, or an end of another
     * wire. An end counts when a pin or another wire (end or interior) touches it. Free ends and crossings do not.
     */
    public Set<GridPoint> connectionPointsOn(Wire wire) {
        Set<GridPoint> points = new TreeSet<>();
        pinsAtPoint.keySet().stream().filter(wire::contains).forEach(points::add);
        for (Wire other : wires) {
            if (other.id().equals(wire.id())) {
                continue;
            }
            for (GridPoint end : List.of(other.start(), other.end())) {
                if (wire.contains(end)) {
                    points.add(end);
                }
            }
            for (GridPoint end : List.of(wire.start(), wire.end())) {
                if (other.contains(end)) {
                    points.add(end);
                }
            }
        }
        return points;
    }
}
