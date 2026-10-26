package cl.almejo.vsim.core.geometry;

import cl.almejo.vsim.core.document.Wire;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Grid points indexed by row and by column, so the points a wire contains are found with a range lookup. The wire's
 * own ends must have been added.
 */
public final class WireLines {

    private final Map<Integer, TreeMap<Integer, GridPoint>> rows = new HashMap<>();
    private final Map<Integer, TreeMap<Integer, GridPoint>> columns = new HashMap<>();

    public WireLines() {
    }

    public WireLines(Collection<GridPoint> points) {
        points.forEach(this::add);
    }

    public void add(GridPoint point) {
        rows.computeIfAbsent(point.y(), row -> new TreeMap<>()).put(point.x(), point);
        columns.computeIfAbsent(point.x(), column -> new TreeMap<>()).put(point.y(), point);
    }

    /** The added points lying on {@code wire} (ends included), in ascending order along it. */
    public Collection<GridPoint> pointsOn(Wire wire) {
        return (wire.isHorizontal()
                ? rows.get(wire.start().y()).subMap(wire.start().x(), true, wire.end().x(), true)
                : columns.get(wire.start().x()).subMap(wire.start().y(), true, wire.end().y(), true)).values();
    }
}
