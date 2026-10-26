package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.UnionFind;
import cl.almejo.vsim.core.geometry.WireLines;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Union-find over the points of interest (pins and wire ends) of one layout, with the connection rule of
 * {@code NetlistCompiler}: a wire joins every point of interest it contains.
 */
final class PointNets {

    private final UnionFind<GridPoint> unionFind = new UnionFind<>();
    private final Map<PinRef, GridPoint> pinPoints;
    private final Map<ElementId, GridPoint> wireStarts = new HashMap<>();

    PointNets(Map<PinRef, GridPoint> pinPoints, List<Wire> wires) {
        this.pinPoints = pinPoints;
        List<GridPoint> points = new ArrayList<>(pinPoints.values());
        for (Wire wire : wires) {
            points.add(wire.start());
            points.add(wire.end());
            wireStarts.put(wire.id(), wire.start());
        }
        points.forEach(unionFind::add);
        WireLines wireLines = new WireLines(points);
        for (Wire wire : wires) {
            for (GridPoint point : wireLines.pointsOn(wire)) {
                unionFind.union(wire.start(), point);
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
        return unionFind.find(point);
    }

    void union(GridPoint first, GridPoint second) {
        unionFind.union(first, second);
    }
}
