package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.UnionFind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * «Alinear en columnas» (spec 2026-11-06 §5.1): parts stacked one above the other whose left edges differ by at most
 * {@value #COLUMN_TOLERANCE} units form a column (chained: a–b and b–c), and the column's members move on x to the
 * left edge most of them already have (ties: the smallest x). Each move obeys the {@link Placement} rules, keeps the
 * order with the neighbours and leaves the wires with no more corners than before
 * ({@link Placement#tryTidyingMove}), so a member that cannot reach the edge stays.
 *
 * <p>Stacked: their bodies' x ranges overlap and their y ranges do not. A column that holds two parts side by side (a
 * row: their y ranges overlap) is not a column at all and is left alone. Texts never align.
 */
final class ColumnAlignment {

    /** How far apart two left edges may be, in grid units, and still be one column (spec §5.1). */
    static final int COLUMN_TOLERANCE = 2;

    private ColumnAlignment() {
    }

    /**
     * Aligns the columns of {@code placement}'s reading on its copy, reporting {@code ALIGNING} with each column (of
     * the columns found).
     *
     * @return how many parts moved
     * @throws CancellationException when {@code cancelled} says yes (polled before every column)
     */
    static int align(Placement placement, CircuitTidyProgress progress, BooleanSupplier cancelled) {
        List<List<LayoutItems.Item>> columns = columns(placement.items());
        int moved = 0;
        for (int index = 0; index < columns.size(); index++) {
            progress.reached(CircuitTidyProgress.Stage.ALIGNING, index + 1, columns.size());
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            moved += alignColumn(placement, columns.get(index));
        }
        return moved;
    }

    /** Moves the movable members of {@code column} to its target edge, in id order; the parts that moved. */
    private static int alignColumn(Placement placement, List<LayoutItems.Item> column) {
        int target = targetEdge(column);
        int moved = 0;
        for (LayoutItems.Item member : column) {
            int edge = bodiesOf(member).x();
            if (!member.movable() || edge == target) {
                continue;
            }
            GridPoint shift = new GridPoint(target - edge, 0);
            if (placement.keepsOrder(member, Placement.moved(member, shift))
                    && placement.tryTidyingMove(member, shift)) {
                moved += member.parts();
            }
        }
        return moved;
    }

    /** The left edge most members have; ties: the smallest. */
    private static int targetEdge(List<LayoutItems.Item> column) {
        Map<Integer, Integer> counts = new TreeMap<>();
        column.forEach(member -> counts.merge(bodiesOf(member).x(), 1, Integer::sum));
        int target = 0;
        int best = 0;
        for (Map.Entry<Integer, Integer> edge : counts.entrySet()) {
            if (edge.getValue() > best) {
                best = edge.getValue();
                target = edge.getKey();
            }
        }
        return target;
    }

    /**
     * The columns of {@code items}: groups of two or more parts chained by {@link #stacked} pairs, each in id order,
     * in the order of their first member; groups with a row inside are left out.
     */
    static List<List<LayoutItems.Item>> columns(LayoutItems items) {
        List<LayoutItems.Item> parts = items.items().stream().filter(item -> !item.isText()).toList();
        UnionFind<ElementId> joins = new UnionFind<>();
        parts.forEach(part -> joins.add(part.id()));
        for (int first = 0; first < parts.size(); first++) {
            for (int second = first + 1; second < parts.size(); second++) {
                if (stacked(bodiesOf(parts.get(first)), bodiesOf(parts.get(second)))) {
                    joins.union(parts.get(first).id(), parts.get(second).id());
                }
            }
        }
        Map<ElementId, List<LayoutItems.Item>> byRoot = new LinkedHashMap<>();
        parts.forEach(part -> byRoot.computeIfAbsent(joins.find(part.id()), root -> new ArrayList<>()).add(part));
        List<List<LayoutItems.Item>> columns = new ArrayList<>();
        for (List<LayoutItems.Item> group : byRoot.values()) {
            if (group.size() > 1 && !holdsARow(group)) {
                group.sort(Comparator.comparing(LayoutItems.Item::id, LayoutItems.ID_ORDER));
                columns.add(group);
            }
        }
        return columns;
    }

    /** True when the two bodies are one above the other with left edges close enough for a column. */
    private static boolean stacked(Bounds first, Bounds second) {
        return Math.abs(first.x() - second.x()) <= COLUMN_TOLERANCE && first.overlapsOnX(second)
                && !first.overlapsOnY(second);
    }

    /** True when two members of {@code group} sit side by side: their y ranges overlap. */
    private static boolean holdsARow(List<LayoutItems.Item> group) {
        for (int first = 0; first < group.size(); first++) {
            Bounds one = bodiesOf(group.get(first));
            for (int second = first + 1; second < group.size(); second++) {
                Bounds other = bodiesOf(group.get(second));
                if (one.overlapsOnY(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The box around the item's bodies, without its labels: what the user lines up by eye. */
    private static Bounds bodiesOf(LayoutItems.Item item) {
        return item.bodies().stream().reduce(Bounds::union).orElse(item.bounds());
    }
}
