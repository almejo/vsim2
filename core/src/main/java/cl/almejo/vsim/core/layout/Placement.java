package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Moves the items of one reading of the copy ({@link LayoutItems}) on the copy, with the rules every stage of «Ordenar
 * circuito» obeys (spec 2026-11-06 §4.4, §5): the new place keeps clear of every other item and obstacle with the gap
 * and keeps the item's frames ({@link LayoutItems#isFree}), makes no new overlap with an item where it is now
 * ({@link LayoutItems#addsNoOverlap}), a text lands on no new writing, the wires follow
 * ({@link MoveWithWiresCommand#of}), no foreign wire runs through a moved body and every pin stays in the net it was
 * in. A move that breaks a rule after it was applied is undone.
 *
 * <p>Each item moves at most once per reading: a shift is always counted from where the reading found the item. Phase
 * 1 may give an item a planned place ({@link #plan}) before it moves, so a neighbour does not take it; the stages that
 * only move parts where they are leave the planned place equal to the current one.
 */
final class Placement {

    private final CircuitDocument copy;
    private final ComponentTypeRegistry registry;
    private final LayoutItems items;
    /** Which pins are connected to which before any move of the whole clean-up: every move must keep it. */
    private final Set<Set<PinRef>> originalNets;
    /** Where each item is or will be: its planned place until it is placed or refused. */
    private final Map<ElementId, Bounds> places = new HashMap<>();
    /** Where each item that moved is on the copy now (the others are where the reading found them). */
    private final Map<ElementId, Bounds> actual = new HashMap<>();

    Placement(CircuitDocument copy, ComponentTypeRegistry registry, LayoutItems items,
              Set<Set<PinRef>> originalNets) {
        this.copy = copy;
        this.registry = registry;
        this.items = items;
        this.originalNets = originalNets;
    }

    /** The reading of the copy the shifts are counted from. */
    LayoutItems items() {
        return items;
    }

    /** Says where {@code id} is planned to go (or, when refused, to stay): others keep clear of it. */
    void plan(ElementId id, Bounds place) {
        places.put(id, place);
    }

    /** Where {@code item} is on the copy now. */
    Bounds current(LayoutItems.Item item) {
        return actual.getOrDefault(item.id(), item.bounds());
    }

    /**
     * Applies the move on the copy when the place is clear (of items, walls and frames) and keeps it when no foreign
     * wire runs through a moved body and every pin stays in the net it was in; otherwise undoes it. A kept move is
     * remembered as the item's place.
     */
    boolean tryMove(LayoutItems.Item item, GridPoint shift) {
        if (shift.equals(GridPoint.ORIGIN) || !isClear(item, moved(item, shift))) {
            return false;
        }
        return moveIfStillWanted(item, shift, () -> true);
    }

    /**
     * A move of the stages that tidy (aligning, straightening): {@link #tryMove}'s rules, and also no body lands over
     * a text it did not cover ({@link #coversNoNewText}) and the wires end with no more corners ({@link WireCorners})
     * than before; otherwise the move is undone. Those stages are there to tidy, so a move that bends the wires that
     * follow it more than it straightens, or hides a text, is no gain.
     */
    boolean tryTidyingMove(LayoutItems.Item item, GridPoint shift) {
        return tryTidyingMove(List.of(item), shift);
    }

    /**
     * {@link #tryTidyingMove(LayoutItems.Item, GridPoint)} for items that move together by one shift (a row kept
     * whole): every one must find its place clear of the items that stay, and all move in one go or none does. What
     * lies between them does not change, so they are not checked against each other.
     */
    boolean tryTidyingMove(List<LayoutItems.Item> group, GridPoint shift) {
        if (shift.equals(GridPoint.ORIGIN) || group.isEmpty()) {
            return false;
        }
        Set<ElementId> together = new LinkedHashSet<>();
        group.forEach(item -> together.add(item.id()));
        for (LayoutItems.Item item : group) {
            Bounds candidate = moved(item, shift);
            if (!isClear(item, candidate, together) || !coversNoNewText(item, candidate)) {
                return false;
            }
        }
        int cornersBefore = WireCorners.count(copy);
        return moveIfStillWanted(group, shift, () -> WireCorners.count(copy) <= cornersBefore);
    }

    /** True when {@code item} at {@code candidate} covers no block of texts it does not cover where it is now. */
    private boolean coversNoNewText(LayoutItems.Item item, Bounds candidate) {
        Bounds from = current(item);
        for (LayoutItems.Item other : items.items()) {
            if (!other.isText() || other.id().equals(item.id())) {
                continue;
            }
            Bounds otherNow = current(other);
            if (LayoutItems.covers(item, candidate, other, otherNow)
                    && !LayoutItems.covers(item, from, other, otherNow)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies a move whose place the caller already found clear, and undoes it unless it keeps the meaning and
     * {@code stillWanted} (asked on the moved copy) agrees.
     */
    private boolean moveIfStillWanted(LayoutItems.Item item, GridPoint shift, BooleanSupplier stillWanted) {
        return moveIfStillWanted(List.of(item), shift, stillWanted);
    }

    /** Moves the items of {@code group} together, as one move: kept or undone for all of them. */
    private boolean moveIfStillWanted(List<LayoutItems.Item> group, GridPoint shift, BooleanSupplier stillWanted) {
        Set<ElementId> members = new LinkedHashSet<>();
        group.forEach(item -> members.addAll(item.members()));
        MoveWithWiresCommand move = MoveWithWiresCommand.of(copy, registry, members, shift.x(), shift.y());
        move.apply(copy);
        // texts have no pins and no wire follows them: the nets cannot change
        boolean onlyTexts = group.stream().allMatch(LayoutItems.Item::isText);
        if (!onlyTexts && !keepsTheMeaning(members) || !stillWanted.getAsBoolean()) {
            move.revert(copy);
            return false;
        }
        for (LayoutItems.Item item : group) {
            places.put(item.id(), moved(item, shift));
            actual.put(item.id(), moved(item, shift));
        }
        return true;
    }

    /** Checked after the move, on the wires as they end up: the wires on the moved pins follow them. */
    private boolean keepsTheMeaning(Set<ElementId> members) {
        return !LayoutItems.crossedByForeignWire(copy, registry, members) && nets().equals(originalNets);
    }

    /**
     * True when {@code item} at {@code candidate} keeps clear of every other item's planned place, and makes no new
     * overlap with any item where it is now: a neighbour planned to leave may still have to stay, so its spot is not
     * taken before it left.
     */
    boolean isClear(LayoutItems.Item item, Bounds candidate) {
        return isClear(item, candidate, Set.of());
    }

    /** {@link #isClear(LayoutItems.Item, Bounds)}, leaving out the items in {@code together} (moved by the same shift). */
    private boolean isClear(LayoutItems.Item item, Bounds candidate, Set<ElementId> together) {
        return items.isFree(item, candidate, places, together) && items.addsNoOverlap(item, candidate, actual, together)
                && !(item.isText() && items.writesOverNewWriting(item, candidate, actual, together));
    }

    /**
     * True when {@code item} at {@code candidate} keeps its order with the parts around it, each where it is now: a
     * part above or below it in its column (their x ranges overlap) stays on the same side, and so does a part left
     * or right of it in its row (their y ranges overlap). A move never jumps over a neighbour, even to a free place
     * (spec §5.1, §5.2).
     */
    boolean keepsOrder(LayoutItems.Item item, Bounds candidate) {
        Bounds from = current(item);
        for (LayoutItems.Item other : items.items()) {
            if (other.isText() || other.id().equals(item.id())) {
                continue;
            }
            Bounds otherNow = current(other);
            boolean sameColumn = from.overlapsOnX(otherNow);
            boolean sameRow = from.overlapsOnY(otherNow);
            if (sameColumn && !keepsSide(from.y(), from.maximumY(), candidate.y(), candidate.maximumY(),
                    otherNow.y(), otherNow.maximumY())) {
                return false;
            }
            if (sameRow && !keepsSide(from.x(), from.maximumX(), candidate.x(), candidate.maximumX(),
                    otherNow.x(), otherNow.maximumX())) {
                return false;
            }
        }
        return true;
    }

    /** On one axis: a neighbour wholly before (or after) the item stays wholly before (or after) it. */
    private static boolean keepsSide(int from, int fromEnd, int to, int toEnd, int other, int otherEnd) {
        boolean wasBefore = otherEnd <= from;
        boolean wasAfter = other >= fromEnd;
        return (!wasBefore || otherEnd <= to) && (!wasAfter || other >= toEnd);
    }

    /** Which pins of {@code document} are connected to which, by its wires. */
    static Set<Set<PinRef>> netsOf(CircuitDocument document, ComponentTypeRegistry registry) {
        return Connectivity.of(document.wires(), document, registry).pinNets();
    }

    private Set<Set<PinRef>> nets() {
        return netsOf(copy, registry);
    }

    /** {@code item}'s box moved by {@code shift} from where the reading found it. */
    static Bounds moved(LayoutItems.Item item, GridPoint shift) {
        return item.bounds().translate(shift.x(), shift.y());
    }
}
