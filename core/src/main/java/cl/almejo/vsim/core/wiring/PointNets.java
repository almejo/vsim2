package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Union-find over the points of interest (pins and wire ends) of one layout, with the connection rule of
 * {@code NetlistCompiler}: a wire joins every point of interest it contains.
 */
final class PointNets {

    private final Map<GridPoint, GridPoint> parents = new HashMap<>();
    private final Map<PinRef, GridPoint> pinPoints;
    private final Map<ElementId, GridPoint> wireStarts = new HashMap<>();

    PointNets(Map<PinRef, GridPoint> pinPoints, List<Wire> wires) {
        this.pinPoints = pinPoints;
        Map<Integer, TreeMap<Integer, GridPoint>> rows = new HashMap<>();
        Map<Integer, TreeMap<Integer, GridPoint>> columns = new HashMap<>();
        List<GridPoint> points = new ArrayList<>(pinPoints.values());
        for (Wire wire : wires) {
            points.add(wire.start());
            points.add(wire.end());
            wireStarts.put(wire.id(), wire.start());
        }
        for (GridPoint point : points) {
            parents.putIfAbsent(point, point);
            rows.computeIfAbsent(point.y(), row -> new TreeMap<>()).put(point.x(), point);
            columns.computeIfAbsent(point.x(), column -> new TreeMap<>()).put(point.y(), point);
        }
        for (Wire wire : wires) {
            Map<Integer, GridPoint> contained = wire.isHorizontal()
                    ? rows.get(wire.start().y()).subMap(wire.start().x(), true, wire.end().x(), true)
                    : columns.get(wire.start().x()).subMap(wire.start().y(), true, wire.end().y(), true);
            for (GridPoint point : contained.values()) {
                union(wire.start(), point);
            }
        }
    }

    GridPoint pinPoint(PinRef pin) {
        return pinPoints.get(pin);
    }

    GridPoint wireStart(ElementId wireId) {
        return wireStarts.get(wireId);
    }

    GridPoint find(GridPoint point) {
        GridPoint root = point;
        while (!parents.get(root).equals(root)) {
            root = parents.get(root);
        }
        GridPoint current = point;
        while (!current.equals(root)) {
            GridPoint next = parents.get(current);
            parents.put(current, root);
            current = next;
        }
        return root;
    }

    void union(GridPoint first, GridPoint second) {
        GridPoint firstRoot = find(first);
        GridPoint secondRoot = find(second);
        if (!firstRoot.equals(secondRoot)) {
            parents.put(secondRoot, firstRoot);
        }
    }
}
