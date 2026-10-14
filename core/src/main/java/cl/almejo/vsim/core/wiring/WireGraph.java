package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only geometry of a list of wires over the pins of a document, with the same connection semantics as
 * {@code NetlistCompiler}: a pin or a wire end connects every wire that contains that point (end or interior); the
 * interiors of two wires that cross do not connect. Pins, wires and wire ends are indexed by row and column, so a
 * query only looks at what lies on the lines involved. The tidy run keeps one graph and {@link #update updates} it
 * after every change instead of building a new one.
 */
public final class WireGraph {

    private final Map<GridPoint, List<PinRef>> pinsAtPoint;
    private final Map<Integer, TreeSet<Integer>> pinColumnsByRow = new HashMap<>();
    private final Map<Integer, TreeSet<Integer>> pinRowsByColumn = new HashMap<>();
    private final Map<Integer, List<Wire>> horizontalsByRow = new HashMap<>();
    private final Map<Integer, List<Wire>> verticalsByColumn = new HashMap<>();
    private final Map<Integer, TreeMap<Integer, List<Wire>>> endsByRow = new HashMap<>();
    private final Map<Integer, TreeMap<Integer, List<Wire>>> endsByColumn = new HashMap<>();
    /** Position of each wire in the list order; a replacement takes the position of the first wire it replaces. */
    private final Map<ElementId, Integer> ranks = new HashMap<>();
    private List<Wire> wires;

    private WireGraph(List<Wire> wires, Map<GridPoint, List<PinRef>> pinsAtPoint) {
        this.wires = List.copyOf(wires);
        this.pinsAtPoint = pinsAtPoint;
        for (GridPoint point : pinsAtPoint.keySet()) {
            pinColumnsByRow.computeIfAbsent(point.y(), row -> new TreeSet<>()).add(point.x());
            pinRowsByColumn.computeIfAbsent(point.x(), column -> new TreeSet<>()).add(point.y());
        }
        for (int index = 0; index < this.wires.size(); index++) {
            Wire wire = this.wires.get(index);
            ranks.put(wire.id(), index);
            index(wire);
        }
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

    /**
     * Takes {@code removed} out and puts {@code added} in (they take the list position of the first removed wire);
     * {@code wiresNow} is the resulting list, as {@link #wires()} will return it (not copied: the caller never
     * changes it).
     */
    void update(List<Wire> removed, List<Wire> added, List<Wire> wiresNow) {
        int rank = removed.stream().mapToInt(wire -> ranks.get(wire.id())).min().orElse(Integer.MAX_VALUE);
        for (Wire wire : removed) {
            ranks.remove(wire.id());
            unindex(wire);
        }
        for (Wire wire : added) {
            ranks.put(wire.id(), rank);
            index(wire);
        }
        wires = wiresNow;
    }

    private void index(Wire wire) {
        if (wire.isHorizontal()) {
            horizontalsByRow.computeIfAbsent(wire.start().y(), row -> new ArrayList<>()).add(wire);
        } else {
            verticalsByColumn.computeIfAbsent(wire.start().x(), column -> new ArrayList<>()).add(wire);
        }
        for (GridPoint end : List.of(wire.start(), wire.end())) {
            endsByRow.computeIfAbsent(end.y(), row -> new TreeMap<>())
                    .computeIfAbsent(end.x(), column -> new ArrayList<>()).add(wire);
            endsByColumn.computeIfAbsent(end.x(), column -> new TreeMap<>())
                    .computeIfAbsent(end.y(), row -> new ArrayList<>()).add(wire);
        }
    }

    private void unindex(Wire wire) {
        if (wire.isHorizontal()) {
            horizontalsByRow.get(wire.start().y()).remove(wire);
        } else {
            verticalsByColumn.get(wire.start().x()).remove(wire);
        }
        for (GridPoint end : List.of(wire.start(), wire.end())) {
            removeEnd(endsByRow.get(end.y()), end.x(), wire);
            removeEnd(endsByColumn.get(end.x()), end.y(), wire);
        }
    }

    private static void removeEnd(TreeMap<Integer, List<Wire>> line, int position, Wire wire) {
        List<Wire> owners = line.get(position);
        owners.remove(wire);
        if (owners.isEmpty()) {
            line.remove(position);
        }
    }

    public List<Wire> wires() {
        return wires;
    }

    public List<PinRef> pinsAt(GridPoint point) {
        return pinsAtPoint.getOrDefault(point, List.of());
    }

    /** Wires that have {@code point} at an end or in their interior, in list order. */
    public List<Wire> wiresThrough(GridPoint point) {
        List<Wire> through = new ArrayList<>();
        for (Wire wire : horizontalsByRow.getOrDefault(point.y(), List.of())) {
            if (wire.contains(point)) {
                through.add(wire);
            }
        }
        for (Wire wire : verticalsByColumn.getOrDefault(point.x(), List.of())) {
            if (wire.contains(point)) {
                through.add(wire);
            }
        }
        through.sort(Comparator.comparingInt(wire -> ranks.get(wire.id())));
        return through;
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
        boolean horizontal = wire.isHorizontal();
        int line = horizontal ? wire.start().y() : wire.start().x();
        int from = horizontal ? wire.start().x() : wire.start().y();
        int to = horizontal ? wire.end().x() : wire.end().y();
        TreeSet<Integer> pins = (horizontal ? pinColumnsByRow : pinRowsByColumn).get(line);
        if (pins != null) {
            for (int position : pins.subSet(from, true, to, true)) {
                points.add(pointOnLine(horizontal, line, position));
            }
        }
        TreeMap<Integer, List<Wire>> ends = (horizontal ? endsByRow : endsByColumn).get(line);
        if (ends != null) {
            ends.subMap(from, true, to, true).forEach((position, owners) -> {
                if (owners.stream().anyMatch(owner -> !owner.id().equals(wire.id()))) {
                    points.add(pointOnLine(horizontal, line, position));
                }
            });
        }
        for (GridPoint end : List.of(wire.start(), wire.end())) {
            if (wiresThrough(end).stream().anyMatch(other -> !other.id().equals(wire.id()))) {
                points.add(end);
            }
        }
        return points;
    }

    private static GridPoint pointOnLine(boolean horizontal, int line, int position) {
        return horizontal ? new GridPoint(position, line) : new GridPoint(line, position);
    }
}
