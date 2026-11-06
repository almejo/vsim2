package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * A simple stand-in for {@link OverlapSeparation} so {@link CircuitTidy} can be tested on its own: each movable box
 * that overlaps something, in id order, takes the smallest whole shift (right, left, down, up) that frees it from
 * every other box where it is now. Not minimal like the real one, but it respects every box it is given, walls
 * included.
 */
final class GreedySeparator implements CircuitTidy.Separator {

    private static final int SEARCH_LIMIT = 100;

    @Override
    public Separation separate(List<LayoutBox> boxes, BooleanSupplier cancelled) {
        Map<ElementId, LayoutBox> current = new LinkedHashMap<>();
        boxes.stream().sorted((first, second) -> LayoutItems.ID_ORDER.compare(first.id(), second.id()))
                .forEach(box -> current.put(box.id(), box));
        Map<ElementId, GridPoint> deltas = new LinkedHashMap<>();
        Set<ElementId> stillOverlapping = new HashSet<>();
        for (LayoutBox box : List.copyOf(current.values())) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            if (!box.movable() || isFree(current, box, box.bounds())) {
                continue;
            }
            GridPoint shift = smallestFreeShift(current, box);
            if (shift == null) {
                stillOverlapping.add(box.id());
                continue;
            }
            current.put(box.id(), new LayoutBox(box.id(), box.bounds().translate(shift.x(), shift.y()), true,
                    box.gap()));
            deltas.put(box.id(), shift);
        }
        return new Separation(deltas, stillOverlapping);
    }

    private static GridPoint smallestFreeShift(Map<ElementId, LayoutBox> current, LayoutBox box) {
        for (int distance = 1; distance <= SEARCH_LIMIT; distance++) {
            for (GridPoint shift : List.of(new GridPoint(distance, 0), new GridPoint(-distance, 0),
                    new GridPoint(0, distance), new GridPoint(0, -distance))) {
                if (isFree(current, box, box.bounds().translate(shift.x(), shift.y()))) {
                    return shift;
                }
            }
        }
        return null;
    }

    private static boolean isFree(Map<ElementId, LayoutBox> current, LayoutBox box, Bounds at) {
        for (LayoutBox other : current.values()) {
            if (!other.id().equals(box.id()) && tooClose(at, other.bounds(), Math.max(box.gap(), other.gap()))) {
                return false;
            }
        }
        return true;
    }

    /** Interiors overlap, or (with a gap) the boxes are closer than the gap. */
    static boolean tooClose(Bounds first, Bounds second, int gap) {
        int overlapX = Math.min(first.maximumX(), second.maximumX()) - Math.max(first.x(), second.x());
        int overlapY = Math.min(first.maximumY(), second.maximumY()) - Math.max(first.y(), second.y());
        int needed = gap == 0 ? 0 : -gap;
        return overlapX > needed && overlapY > needed;
    }
}
