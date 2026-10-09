package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Points where wires branch: a wire end from which wires leave in three or four directions. The eye cannot tell such a
 * point from a crossing, so the canvas marks it with a dot. Two wires meeting end to end on one line, or overlapping
 * on one line, leave in only two directions and get no dot.
 */
public final class Junctions {

    private enum Direction { LEFT, RIGHT, UP, DOWN }

    private Junctions() {
    }

    public static Set<GridPoint> of(CircuitDocument document) {
        List<Wire> wires = document.wires();
        Set<GridPoint> endpoints = new HashSet<>();
        for (Wire wire : wires) {
            endpoints.add(wire.start());
            endpoints.add(wire.end());
        }
        Set<GridPoint> junctions = new HashSet<>();
        for (GridPoint endpoint : endpoints) {
            Set<Direction> directions = EnumSet.noneOf(Direction.class);
            for (Wire wire : wires) {
                if (wire.contains(endpoint)) {
                    addDirectionsAway(wire, endpoint, directions);
                }
            }
            if (directions.size() >= 3) {
                junctions.add(endpoint);
            }
        }
        return junctions;
    }

    /** Adds the directions in which {@code wire} leaves {@code point}, a point on it. */
    private static void addDirectionsAway(Wire wire, GridPoint point, Set<Direction> directions) {
        if (wire.isHorizontal()) {
            if (wire.start().x() < point.x()) {
                directions.add(Direction.LEFT);
            }
            if (wire.end().x() > point.x()) {
                directions.add(Direction.RIGHT);
            }
        } else {
            if (wire.start().y() < point.y()) {
                directions.add(Direction.UP);
            }
            if (wire.end().y() > point.y()) {
                directions.add(Direction.DOWN);
            }
        }
    }
}
