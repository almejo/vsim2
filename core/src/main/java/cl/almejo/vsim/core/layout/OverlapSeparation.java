package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/**
 * Moves overlapping boxes apart by the least it can, keeping each pair's order (spec «Ordenar circuito» §4.3).
 * Pure geometry: it knows nothing about components, wires or documents.
 *
 * <p>Two boxes overlap when their interiors share an area (spec §4.1: touching edges is fine, and so is being closer
 * than the gap). Each overlapping pair gets one separation constraint, which does ask for the larger of the two gaps,
 * on the axis that needs the smaller displacement (ties: x, which keeps the left-to-right flow), in the pair's current
 * order (by centre, then by id). Every round solves both axes from the original positions with all the constraints so
 * far ({@link AxisProjection}), so the result is the least movement that meets them (each box's movement counted by
 * its {@linkplain LayoutBox#weight() weight}: a heavy box lets the light one move) and a pair, once separated, stays
 * separated. Rounds repeat while new overlaps appear, at most {@value #MAXIMUM_ROUNDS}.
 */
public final class OverlapSeparation {

    /** Spec §4.3: a chain of pushes rarely needs more; what still overlaps then is reported. */
    static final int MAXIMUM_ROUNDS = 10;

    private final List<LayoutBox> boxes;
    private final BooleanSupplier cancelled;
    private final int[] currentX;
    private final int[] currentY;
    private final Set<Long> constrainedPairs = new HashSet<>();
    private final AxisConstraints horizontal;
    private final AxisConstraints vertical;

    private OverlapSeparation(List<LayoutBox> boxes, BooleanSupplier cancelled) {
        this.boxes = boxes;
        this.cancelled = cancelled;
        this.currentX = new int[boxes.size()];
        this.currentY = new int[boxes.size()];
        for (int index = 0; index < boxes.size(); index++) {
            currentX[index] = boxes.get(index).bounds().x();
            currentY[index] = boxes.get(index).bounds().y();
        }
        this.horizontal = new AxisConstraints(boxes.size());
        this.vertical = new AxisConstraints(boxes.size());
    }

    /**
     * The whole-unit moves that separate {@code boxes}; the result does not depend on the order of the list.
     *
     * @throws java.util.concurrent.CancellationException when {@code cancelled} says yes
     * @throws IllegalArgumentException when two boxes share an id or a gap is negative
     */
    public static Separation separate(List<LayoutBox> boxes, BooleanSupplier cancelled) {
        Objects.requireNonNull(boxes, "boxes");
        Objects.requireNonNull(cancelled, "cancelled");
        return new OverlapSeparation(sortedById(boxes), cancelled).run();
    }

    private static List<LayoutBox> sortedById(List<LayoutBox> boxes) {
        Set<ElementId> seen = new HashSet<>();
        for (LayoutBox box : boxes) {
            if (!seen.add(box.id())) {
                throw new IllegalArgumentException("two boxes share the id " + box.id());
            }
            if (box.gap() < 0) {
                throw new IllegalArgumentException("negative gap for " + box.id() + ": " + box.gap());
            }
        }
        return boxes.stream().sorted(Comparator.comparing(box -> box.id().value())).toList();
    }

    private Separation run() {
        for (int round = 0; round < MAXIMUM_ROUNDS; round++) {
            throwIfCancelled();
            List<int[]> newPairs = overlappingPairsWithoutAConstraint();
            if (newPairs.isEmpty()) {
                break;
            }
            newPairs.forEach(pair -> constrain(pair[0], pair[1]));
            solve(horizontal, currentX, Bounds::x);
            solve(vertical, currentY, Bounds::y);
        }
        throwIfCancelled();
        return new Separation(deltas(), stillOverlapping());
    }

    private void throwIfCancelled() {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("overlap separation cancelled");
        }
    }

    private List<int[]> overlappingPairsWithoutAConstraint() {
        return overlappingPairs().stream().filter(pair -> !constrainedPairs.contains(pairKey(pair[0], pair[1])))
                .toList();
    }

    /**
     * Every pair (at least one movable) whose interiors overlap at the current positions, ordered by first then
     * second index: a sweep over the boxes sorted by left edge only compares boxes whose x ranges can still overlap.
     */
    private List<int[]> overlappingPairs() {
        long[] byLeftEdge = new long[boxes.size()];
        for (int index = 0; index < boxes.size(); index++) {
            // The left edge in the high half and the index in the low half: one primitive sort, ties by index.
            byLeftEdge[index] = ((long) currentX[index] << Integer.SIZE) | index;
        }
        Arrays.sort(byLeftEdge);
        List<int[]> pairs = new ArrayList<>();
        for (int position = 0; position < byLeftEdge.length; position++) {
            int first = (int) byLeftEdge[position];
            long rightEdge = (long) currentX[first] + width(first);
            for (int next = position + 1; next < byLeftEdge.length; next++) {
                int second = (int) byLeftEdge[next];
                if (currentX[second] >= rightEdge) {
                    break;
                }
                if (eitherMoves(first, second) && overlap(first, second)) {
                    pairs.add(new int[]{Math.min(first, second), Math.max(first, second)});
                }
            }
        }
        pairs.sort(Comparator.<int[]>comparingInt(pair -> pair[0]).thenComparingInt(pair -> pair[1]));
        return pairs;
    }

    /** One constraint for the pair, on the cheaper axis, unless it would close a cycle (then the pair is reported). */
    private void constrain(int first, int second) {
        constrainedPairs.add(pairKey(first, second));
        long gap = pairGap(first, second);
        int left = firstByCentre(first, second, currentX, Bounds::width);
        int right = left == first ? second : first;
        int top = firstByCentre(first, second, currentY, Bounds::height);
        int bottom = top == first ? second : first;
        long neededX = currentX[left] + width(left) + gap - currentX[right];
        long neededY = currentY[top] + height(top) + gap - currentY[bottom];
        if (neededX <= neededY) {
            horizontal.addUnlessCyclic(new AxisProjection.Constraint(left, right, width(left) + gap));
        } else {
            vertical.addUnlessCyclic(new AxisProjection.Constraint(top, bottom, height(top) + gap));
        }
    }

    private int firstByCentre(int first, int second, int[] position, ToIntFunction<Bounds> size) {
        long firstCentreTimesTwo = 2L * position[first] + size.applyAsInt(boxes.get(first).bounds());
        long secondCentreTimesTwo = 2L * position[second] + size.applyAsInt(boxes.get(second).bounds());
        // Equal centres: the smaller id first (the boxes are sorted by id, so the smaller index).
        return secondCentreTimesTwo < firstCentreTimesTwo ? second : first;
    }

    /**
     * Solves again, from the original positions, only the parts of one axis whose constraints changed since the last
     * solve: constraints that share no box are independent problems, and a part whose constraints did not change would
     * give the same answer.
     */
    private void solve(AxisConstraints axis, int[] current, ToIntFunction<Bounds> originalPosition) {
        for (List<AxisProjection.Constraint> component : axis.changedComponents()) {
            solveComponent(component, current, originalPosition);
        }
    }

    private void solveComponent(List<AxisProjection.Constraint> component, int[] current,
                                ToIntFunction<Bounds> originalPosition) {
        Map<Integer, Integer> variableOfBox = new HashMap<>();
        List<Integer> involved = new ArrayList<>();
        for (AxisProjection.Constraint constraint : component) {
            for (int box : List.of(constraint.left(), constraint.right())) {
                if (variableOfBox.putIfAbsent(box, involved.size()) == null) {
                    involved.add(box);
                }
            }
        }
        long[] desired = new long[involved.size()];
        long[] weights = new long[involved.size()];
        boolean[] fixed = new boolean[involved.size()];
        for (int variable = 0; variable < involved.size(); variable++) {
            LayoutBox box = boxes.get(involved.get(variable));
            desired[variable] = originalPosition.applyAsInt(box.bounds());
            weights[variable] = box.weight();
            fixed[variable] = !box.movable();
        }
        List<AxisProjection.Constraint> local = component.stream().map(constraint -> new AxisProjection.Constraint(
                variableOfBox.get(constraint.left()), variableOfBox.get(constraint.right()),
                constraint.distance())).toList();
        long[] solved = AxisProjection.solve(desired, weights, fixed, local, cancelled);
        for (int variable = 0; variable < involved.size(); variable++) {
            current[involved.get(variable)] = Math.toIntExact(solved[variable]);
        }
    }

    private Map<ElementId, GridPoint> deltas() {
        Map<ElementId, GridPoint> deltas = new HashMap<>();
        for (int index = 0; index < boxes.size(); index++) {
            LayoutBox box = boxes.get(index);
            int deltaX = currentX[index] - box.bounds().x();
            int deltaY = currentY[index] - box.bounds().y();
            if (box.movable() && (deltaX != 0 || deltaY != 0)) {
                deltas.put(box.id(), new GridPoint(deltaX, deltaY));
            }
        }
        return deltas;
    }

    private Set<ElementId> stillOverlapping() {
        Set<ElementId> overlapping = new HashSet<>();
        for (int[] pair : overlappingPairs()) {
            addIfMovable(overlapping, pair[0]);
            addIfMovable(overlapping, pair[1]);
        }
        return overlapping;
    }

    private void addIfMovable(Set<ElementId> ids, int index) {
        if (boxes.get(index).movable()) {
            ids.add(boxes.get(index).id());
        }
    }

    private boolean eitherMoves(int first, int second) {
        return boxes.get(first).movable() || boxes.get(second).movable();
    }

    /** The interiors share an area at the current positions; touching edges or corners do not count. */
    private boolean overlap(int first, int second) {
        return currentX[first] < currentX[second] + width(second)
                && currentX[second] < currentX[first] + width(first)
                && currentY[first] < currentY[second] + height(second)
                && currentY[second] < currentY[first] + height(first);
    }

    private long pairGap(int first, int second) {
        return Math.max(boxes.get(first).gap(), boxes.get(second).gap());
    }

    private int width(int index) {
        return boxes.get(index).bounds().width();
    }

    private int height(int index) {
        return boxes.get(index).bounds().height();
    }

    private long pairKey(int first, int second) {
        return (long) Math.min(first, second) * boxes.size() + Math.max(first, second);
    }

    /**
     * The separation constraints of one axis, kept acyclic so the axis always has a left-to-right order, and grouped
     * into components of boxes joined through constraints. A fixed box joins components too: only when two fixed
     * boxes cannot both be met does the order of the solver's merges matter, and joining keeps that order as in one
     * solve of the whole axis.
     */
    private static final class AxisConstraints {
        private final List<AxisProjection.Constraint> constraints = new ArrayList<>();
        private final Map<Integer, List<Integer>> successors = new HashMap<>();
        private final int[] parent;
        private final List<Integer> touchedSinceLastSolve = new ArrayList<>();

        AxisConstraints(int boxCount) {
            this.parent = new int[boxCount];
            for (int box = 0; box < parent.length; box++) {
                parent[box] = box;
            }
        }

        /** Adds the constraint unless the right box already has to stay left of the left one (the pair stays). */
        void addUnlessCyclic(AxisProjection.Constraint constraint) {
            if (reaches(constraint.right(), constraint.left())) {
                return;
            }
            constraints.add(constraint);
            successors.computeIfAbsent(constraint.left(), key -> new ArrayList<>()).add(constraint.right());
            parent[root(constraint.left())] = root(constraint.right());
            touchedSinceLastSolve.add(constraint.left());
        }

        /** The constraints of every component that gained one since the last call, each in insertion order. */
        List<List<AxisProjection.Constraint>> changedComponents() {
            Set<Integer> changed = new HashSet<>();
            touchedSinceLastSolve.forEach(box -> changed.add(root(box)));
            touchedSinceLastSolve.clear();
            Map<Integer, List<AxisProjection.Constraint>> components = new LinkedHashMap<>();
            for (AxisProjection.Constraint constraint : constraints) {
                int component = root(constraint.left());
                if (changed.contains(component)) {
                    components.computeIfAbsent(component, key -> new ArrayList<>()).add(constraint);
                }
            }
            return new ArrayList<>(components.values());
        }

        private int root(int box) {
            int root = box;
            while (parent[root] != root) {
                root = parent[root];
            }
            int walker = box;
            while (parent[walker] != root) {
                int next = parent[walker];
                parent[walker] = root;
                walker = next;
            }
            return root;
        }

        private boolean reaches(int from, int target) {
            Set<Integer> visited = new HashSet<>();
            Deque<Integer> pending = new ArrayDeque<>();
            pending.add(from);
            while (!pending.isEmpty()) {
                int box = pending.poll();
                if (box == target) {
                    return true;
                }
                if (visited.add(box)) {
                    pending.addAll(successors.getOrDefault(box, List.of()));
                }
            }
            return false;
        }
    }
}
