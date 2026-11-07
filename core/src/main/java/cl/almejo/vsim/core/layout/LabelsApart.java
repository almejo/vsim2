package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * «Separar lo que se tapa» for labels (spec 2026-11-06 §6.2): a part whose label covers other writing (another part's
 * label or a free text; the user's LEDs «Salta» and «Rs1 = Rs2» on riscv-maniqui read «SaltaRs1 = Rs2») moves the
 * least it can so its labels cover none: along its row (x) first, right before left at each distance, then up or
 * down, at most {@value #MAXIMUM_SHIFT} units. A label covers other writing by {@link LayoutItems#writingOverlap}'s
 * rule (the first shared column is rounding); a place where it ends clear (no shared column) is preferred.
 *
 * <p>Every place obeys {@link Placement}'s rules (free, frames kept, no new overlap, no writing covered more, the
 * meaning kept), keeps the part's order with its neighbours ({@link Placement#keepsOrder}) and keeps the rows it is
 * in: the parts side by side with it ({@link Straightening#sideBySide}) stay the same, so a row is neither broken nor
 * joined. Only parts move: a free text is never moved for this, and free text over free text stays (tables). A part
 * with no such place stays and is not counted: labels touching are common and harmless.
 */
final class LabelsApart {

    /**
     * How far a part moves at most to free its label, in grid units (spec §6.2): about half a short label; further,
     * the part would no longer sit where the user put it.
     */
    static final int MAXIMUM_SHIFT = 4;
    /** Shared columns not counted when a label must end clear of other writing: none. */
    private static final int CLEAR = 0;

    private final Placement placement;
    private final BooleanSupplier cancelled;

    private LabelsApart(Placement placement, BooleanSupplier cancelled) {
        this.placement = placement;
        this.cancelled = cancelled;
    }

    /**
     * Visits the movable parts of {@code placement}'s reading in id order and moves each whose labels cover other
     * writing to the first place that frees them.
     *
     * @return how many parts moved
     * @throws CancellationException when {@code cancelled} says yes (polled before every part)
     */
    static int separate(Placement placement, BooleanSupplier cancelled) {
        return new LabelsApart(placement, cancelled).run();
    }

    private int run() {
        int moved = 0;
        for (LayoutItems.Item part : placement.items().items()) {
            if (part.isText() || !part.movable() || part.writing().isEmpty()) {
                continue;
            }
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            if (coversOtherWriting(part) && moveApart(part)) {
                moved++;
            }
        }
        return moved;
    }

    /** True when {@code part}'s labels, where it is now, cover other writing (the first shared column is rounding). */
    private boolean coversOtherWriting(LayoutItems.Item part) {
        return placement.writingOverOtherWriting(part, placement.current(part), LayoutItems.TEXT_ROUNDING_COLUMNS) > 0;
    }

    /**
     * Moves {@code part} to the first candidate place that passes every rule where its labels share no column with
     * other writing; with none, to the first where they share at most the column {@link LayoutItems#writingOverlap}
     * takes for rounding. The clear places come first: labels left sharing that column may still read as one word
     * («SaltaRs1 = Rs2» with the user's font).
     */
    private boolean moveApart(LayoutItems.Item part) {
        Set<ElementId> neighbours = rowNeighbours(part, placement.current(part));
        return moveTo(part, neighbours, CLEAR) || moveTo(part, neighbours, LayoutItems.TEXT_ROUNDING_COLUMNS);
    }

    /** Tries the shifts in order; true at the first that frees the labels (by {@code ignoredColumns}) and is kept. */
    private boolean moveTo(LayoutItems.Item part, Set<ElementId> neighbours, int ignoredColumns) {
        for (GridPoint shift : shifts()) {
            Bounds place = Placement.moved(part, shift);
            if (placement.writingOverOtherWriting(part, place, ignoredColumns) == 0
                    && placement.keepsOrder(part, place)
                    && rowNeighbours(part, place).equals(neighbours)
                    && placement.tryMove(part, shift)) {
                return true;
            }
        }
        return false;
    }

    /** Along x (right, then left) at every distance, then along y (down, then up), up to {@value #MAXIMUM_SHIFT}. */
    private static List<GridPoint> shifts() {
        List<GridPoint> shifts = new ArrayList<>();
        for (int distance = 1; distance <= MAXIMUM_SHIFT; distance++) {
            shifts.add(new GridPoint(distance, 0));
            shifts.add(new GridPoint(-distance, 0));
        }
        for (int distance = 1; distance <= MAXIMUM_SHIFT; distance++) {
            shifts.add(new GridPoint(0, distance));
            shifts.add(new GridPoint(0, -distance));
        }
        return shifts;
    }

    /** The parts side by side with {@code part} in a row when it is at {@code place}, every other where it is now. */
    private Set<ElementId> rowNeighbours(LayoutItems.Item part, Bounds place) {
        Bounds body = bodyBoxAt(part, place);
        Set<ElementId> neighbours = new LinkedHashSet<>();
        for (LayoutItems.Item other : placement.items().items()) {
            if (!other.isText() && !other.id().equals(part.id())
                    && Straightening.sideBySide(body, bodyBoxAt(other, placement.current(other)))) {
                neighbours.add(other.id());
            }
        }
        return neighbours;
    }

    private static Bounds bodyBoxAt(LayoutItems.Item item, Bounds place) {
        return item.bodyBox().translate(place.x() - item.bounds().x(), place.y() - item.bounds().y());
    }
}
