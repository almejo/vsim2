package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the bends of a circuit's wires for the summary of «Ordenar circuito» («codos: 140 → 96», spec 2026-11-06
 * §5.3): a corner is a point where exactly two wires end, one horizontal and one vertical, and no other wire runs
 * through. Joints of three or four wires (T-junctions), crossings and two wires in line are not corners.
 */
final class WireCorners {

    private WireCorners() {
    }

    /** The number of L corners of {@code document}'s wires. */
    static int count(CircuitDocument document) {
        List<Wire> wires = document.wires();
        Map<GridPoint, List<Wire>> endingAt = new HashMap<>();
        Map<Integer, List<Wire>> horizontalByRow = new HashMap<>();
        Map<Integer, List<Wire>> verticalByColumn = new HashMap<>();
        for (Wire wire : wires) {
            endingAt.computeIfAbsent(wire.start(), point -> new ArrayList<>()).add(wire);
            endingAt.computeIfAbsent(wire.end(), point -> new ArrayList<>()).add(wire);
            if (wire.isHorizontal()) {
                horizontalByRow.computeIfAbsent(wire.start().y(), row -> new ArrayList<>()).add(wire);
            } else {
                verticalByColumn.computeIfAbsent(wire.start().x(), column -> new ArrayList<>()).add(wire);
            }
        }
        int corners = 0;
        for (Map.Entry<GridPoint, List<Wire>> point : endingAt.entrySet()) {
            List<Wire> ends = point.getValue();
            boolean bend = ends.size() == 2 && ends.get(0).isHorizontal() != ends.get(1).isHorizontal();
            GridPoint at = point.getKey();
            if (bend && !runsThrough(horizontalByRow.getOrDefault(at.y(), List.of()), at)
                    && !runsThrough(verticalByColumn.getOrDefault(at.x(), List.of()), at)) {
                corners++;
            }
        }
        return corners;
    }

    /** True when one of {@code wires} passes over {@code point} without ending there. */
    private static boolean runsThrough(List<Wire> wires, GridPoint point) {
        for (Wire wire : wires) {
            if (wire.contains(point) && !wire.start().equals(point) && !wire.end().equals(point)) {
                return true;
            }
        }
        return false;
    }
}
