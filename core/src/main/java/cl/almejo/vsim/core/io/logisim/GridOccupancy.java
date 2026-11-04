package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the layout has put on VSim's grid so far, by net, with VSim's connection rules ({@code NetlistCompiler}): a
 * pin or a wire end («point of interest») joins every wire that passes over it, while two wires crossing without an
 * end there do not join. So a new pin or wire end must not land on another net's point or wire, and a new wire must
 * not pass over another net's point nor run along another net's wire; it may only cross one at a right angle. It
 * also knows which points are already joined to the rest of their net ({@link #anchor}), the only places a new wire
 * may end.
 */
final class GridOccupancy {

    /** Which way a wire covers a point. */
    private static final int HORIZONTAL = 1;
    private static final int VERTICAL = 2;

    private final Map<GridPoint, Set<Integer>> pointNets = new HashMap<>();
    /** For each point under a wire: net → the ways its wires cover it ({@link #HORIZONTAL} | {@link #VERTICAL}). */
    private final Map<GridPoint, Map<Integer, Integer>> coverage = new HashMap<>();
    private final Map<GridPoint, Set<Integer>> anchored = new HashMap<>();
    private int minimumX = Integer.MAX_VALUE;
    private int minimumY = Integer.MAX_VALUE;
    private int maximumX = Integer.MIN_VALUE;
    private int maximumY = Integer.MIN_VALUE;

    /** A pin or a wire end of {@code net} at {@code point}. */
    void addPoint(GridPoint point, int net) {
        pointNets.computeIfAbsent(point, key -> new HashSet<>()).add(net);
        grow(point);
    }

    /** A straight wire of {@code net}; its ends become points of interest. */
    void addWire(GridPoint from, GridPoint to, int net) {
        int way = from.y() == to.y() ? HORIZONTAL : VERTICAL;
        for (GridPoint point : pointsOf(from, to)) {
            coverage.computeIfAbsent(point, key -> new HashMap<>()).merge(net, way, (first, second) -> first | second);
        }
        addPoint(from, net);
        addPoint(to, net);
    }

    /**
     * Marks {@code point} as joined to the rest of {@code net}: Logisim's own wires and ends are, and so is every pin
     * and added wire once joined to them. A pin placed but not yet joined is of its net without being anchored.
     */
    void anchor(GridPoint point, int net) {
        anchored.computeIfAbsent(point, key -> new HashSet<>()).add(net);
    }

    /** Every point of a straight wire, anchored to {@code net}. */
    void anchorWire(GridPoint from, GridPoint to, int net) {
        pointsOf(from, to).forEach(point -> anchor(point, net));
    }

    /** A straight wire of {@code net} already joined to the rest of it: {@link #addWire} and {@link #anchorWire}. */
    void addAnchoredWire(GridPoint from, GridPoint to, int net) {
        addWire(from, to, net);
        anchorWire(from, to, net);
    }

    /** Whether {@code point} is joined to the rest of {@code net} ({@link #anchor}). */
    boolean isAnchored(GridPoint point, int net) {
        return anchored.getOrDefault(point, Set.of()).contains(net);
    }

    /** Whether a pin or a wire end of {@code net} may go at {@code point} without touching another net. */
    boolean admitsPoint(GridPoint point, int net) {
        return !hasOtherPoint(point, net) && !isCrossing(point, net);
    }

    /** Whether a wire of {@code net} going {@code horizontal}ly may pass over {@code point}. */
    boolean admitsPassage(GridPoint point, int net, boolean horizontal) {
        if (hasOtherPoint(point, net)) {
            return false;
        }
        int sameWay = horizontal ? HORIZONTAL : VERTICAL;
        return coverage.getOrDefault(point, Map.of()).entrySet().stream()
                .noneMatch(entry -> entry.getKey() != net && (entry.getValue() & sameWay) != 0);
    }

    /** Whether another net's wire passes over {@code point}. */
    boolean isCrossing(GridPoint point, int net) {
        return coverage.getOrDefault(point, Map.of()).keySet().stream().anyMatch(other -> other != net);
    }

    /** Whether {@code point} lies within the drawn area grown by {@code margin} on each side. */
    boolean isNear(GridPoint point, int margin) {
        return point.x() >= minimumX - margin && point.x() <= maximumX + margin
                && point.y() >= minimumY - margin && point.y() <= maximumY + margin;
    }

    /** The lowest row anything drawn reaches (the largest y), or 0 when nothing is drawn yet. */
    int bottom() {
        return maximumY == Integer.MIN_VALUE ? 0 : maximumY;
    }

    /** The grid points of a straight wire, both ends included. */
    private static List<GridPoint> pointsOf(GridPoint from, GridPoint to) {
        int steps = Math.abs(to.x() - from.x()) + Math.abs(to.y() - from.y());
        int stepX = Integer.signum(to.x() - from.x());
        int stepY = Integer.signum(to.y() - from.y());
        List<GridPoint> points = new ArrayList<>();
        for (int step = 0; step <= steps; step++) {
            points.add(from.translate(step * stepX, step * stepY));
        }
        return points;
    }

    private boolean hasOtherPoint(GridPoint point, int net) {
        return pointNets.getOrDefault(point, Set.of()).stream().anyMatch(other -> other != net);
    }

    private void grow(GridPoint point) {
        minimumX = Math.min(minimumX, point.x());
        minimumY = Math.min(minimumY, point.y());
        maximumX = Math.max(maximumX, point.x());
        maximumY = Math.max(maximumY, point.y());
    }
}
