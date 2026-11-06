package cl.almejo.vsim.core.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * The positions on one axis closest to the desired ones (weighted least squares: the least Σ weight · movement², a
 * fixed variable never moves) that keep every separation constraint «right ≥ left + distance». It is VPSC's block
 * merging (Dwyer, Marriott, Stuckey, «Fast Node Overlap Removal», GD 2005), our own code: variables joined by a tight
 * constraint form a block that moves as one; blocks are merged while a constraint is violated and split while a
 * Lagrange multiplier says a tight constraint is pulling instead of pushing.
 *
 * <p>Arithmetic is exact (positions are fractions with the block's total weight as denominator), so the result never
 * depends on floating-point noise. Each block is rounded to a whole unit as one, which keeps every satisfied
 * constraint satisfied (offsets and distances are whole, and rounding half up is monotone and commutes with whole
 * shifts).
 *
 * <p>Constraints that cannot be met (two fixed variables too close, or a cycle) are left unmet; the caller sees the
 * overlap that remains.
 */
final class AxisProjection {

    /** {@code position[right] ≥ position[left] + distance}; indexes into the variables. */
    record Constraint(int left, int right, long distance) {
    }

    /** Split-and-merge rounds per constraint before giving up on optimality (VPSC can cycle on degenerate input). */
    private static final int REFINE_ROUNDS_PER_CONSTRAINT = 4;
    private static final int MINIMUM_REFINE_ROUNDS = 100;

    private final long[] desired;
    private final long[] weights;
    private final boolean[] fixed;
    private final List<Constraint> constraints;
    private final List<List<Integer>> incoming = new ArrayList<>();
    private final boolean[] active;
    private final boolean[] unsatisfiable;
    private final long[] offset;
    private final Block[] blockOf;
    private final List<Block> blocks = new ArrayList<>();
    private final BooleanSupplier cancelled;

    private AxisProjection(long[] desired, long[] weights, boolean[] fixed, List<Constraint> constraints,
                           BooleanSupplier cancelled) {
        this.desired = desired;
        this.weights = weights;
        this.fixed = fixed;
        this.constraints = constraints;
        this.cancelled = cancelled;
        this.active = new boolean[constraints.size()];
        this.unsatisfiable = new boolean[constraints.size()];
        this.offset = new long[desired.length];
        this.blockOf = new Block[desired.length];
        for (int variable = 0; variable < desired.length; variable++) {
            incoming.add(new ArrayList<>());
            Block block = new Block();
            block.members.add(variable);
            block.fixedMember = fixed[variable] ? variable : -1;
            blockOf[variable] = block;
            blocks.add(block);
            recompute(block);
        }
        for (int constraint = 0; constraint < constraints.size(); constraint++) {
            incoming.get(constraints.get(constraint).right()).add(constraint);
        }
    }

    /**
     * Whole positions for {@code desired} under {@code constraints}, every variable weighing 1; fixed variables keep
     * their desired position.
     *
     * @throws CancellationException when {@code cancelled} says yes between two refinement rounds
     */
    static long[] solve(long[] desired, boolean[] fixed, List<Constraint> constraints, BooleanSupplier cancelled) {
        long[] evenWeights = new long[desired.length];
        Arrays.fill(evenWeights, 1);
        return solve(desired, evenWeights, fixed, constraints, cancelled);
    }

    /**
     * Whole positions for {@code desired} under {@code constraints}, each variable weighing {@code weights} (at least
     * 1: a heavier one moves less); fixed variables keep their desired position.
     *
     * @throws CancellationException when {@code cancelled} says yes between two refinement rounds
     */
    static long[] solve(long[] desired, long[] weights, boolean[] fixed, List<Constraint> constraints,
                        BooleanSupplier cancelled) {
        return new AxisProjection(desired, weights, fixed, constraints, cancelled).solve();
    }

    private long[] solve() {
        List<Integer> order = topologicalOrder();
        satisfy(order);
        int limit = Math.max(MINIMUM_REFINE_ROUNDS, REFINE_ROUNDS_PER_CONSTRAINT * constraints.size());
        for (int round = 0; round < limit; round++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("overlap separation cancelled");
            }
            if (!splitAtNegativeMultipliers()) {
                break;
            }
            satisfy(order);
        }
        return roundedPositions();
    }

    /** Variables so that every constraint's left comes first; ties (and any cycle) by index. */
    private List<Integer> topologicalOrder() {
        int[] pending = new int[desired.length];
        List<List<Integer>> outgoing = new ArrayList<>();
        for (int variable = 0; variable < desired.length; variable++) {
            outgoing.add(new ArrayList<>());
        }
        for (Constraint constraint : constraints) {
            pending[constraint.right()]++;
            outgoing.get(constraint.left()).add(constraint.right());
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int variable = 0; variable < desired.length; variable++) {
            if (pending[variable] == 0) {
                ready.add(variable);
            }
        }
        List<Integer> order = new ArrayList<>();
        boolean[] placed = new boolean[desired.length];
        while (!ready.isEmpty()) {
            int variable = ready.poll();
            order.add(variable);
            placed[variable] = true;
            for (int successor : outgoing.get(variable)) {
                if (--pending[successor] == 0) {
                    ready.add(successor);
                }
            }
        }
        for (int variable = 0; variable < desired.length; variable++) {
            if (!placed[variable]) {
                order.add(variable);
            }
        }
        return order;
    }

    /** Merges blocks leftwards, in topological order, until no pass merges anything. */
    private void satisfy(List<Integer> order) {
        // A split may free a constraint that an earlier pass could not meet: judge every one again.
        Arrays.fill(unsatisfiable, false);
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int variable : order) {
                int blocksBefore = blocks.size();
                mergeLeft(blockOf[variable]);
                merged |= blocks.size() < blocksBefore;
            }
        }
    }

    private void mergeLeft(Block start) {
        Block block = start;
        while (true) {
            int violated = mostViolatedIncoming(block);
            if (violated < 0) {
                return;
            }
            Constraint constraint = constraints.get(violated);
            Block leftBlock = blockOf[constraint.left()];
            if (leftBlock.fixedMember >= 0 && block.fixedMember >= 0) {
                unsatisfiable[violated] = true;
                continue;
            }
            merge(leftBlock, block, violated);
            block = leftBlock;
        }
    }

    /** The violated constraint from another block into {@code block} with the largest violation; -1 when none. */
    private int mostViolatedIncoming(Block block) {
        int best = -1;
        double bestViolation = 0;
        for (int member : block.members) {
            for (int candidate : incoming.get(member)) {
                if (active[candidate] || unsatisfiable[candidate]) {
                    continue;
                }
                Constraint constraint = constraints.get(candidate);
                if (blockOf[constraint.left()] == block) {
                    // Inside one block the positions are rigid: a violated constraint here cannot be met.
                    unsatisfiable[candidate] = violationNumerator(constraint) > 0;
                    continue;
                }
                if (violationNumerator(constraint) <= 0) {
                    continue;
                }
                double violation = violation(constraint);
                if (best < 0 || violation > bestViolation || (violation == bestViolation && candidate < best)) {
                    best = candidate;
                    bestViolation = violation;
                }
            }
        }
        return best;
    }

    private void merge(Block into, Block from, int constraintIndex) {
        Constraint constraint = constraints.get(constraintIndex);
        long shift = offset[constraint.left()] + constraint.distance() - offset[constraint.right()];
        for (int member : from.members) {
            offset[member] += shift;
            blockOf[member] = into;
            into.members.add(member);
        }
        into.activeConstraints.addAll(from.activeConstraints);
        into.activeConstraints.add(constraintIndex);
        active[constraintIndex] = true;
        if (from.fixedMember >= 0) {
            into.fixedMember = from.fixedMember;
        }
        blocks.remove(from);
        recompute(into);
    }

    /**
     * Splits every block at its tight constraint with the most negative multiplier (one that pulls instead of pushes);
     * false when no block has one.
     */
    private boolean splitAtNegativeMultipliers() {
        List<Block> toSplit = new ArrayList<>();
        List<Integer> cuts = new ArrayList<>();
        for (Block block : blocks) {
            Map<Integer, Long> multipliers = multiplierNumerators(block);
            int cut = -1;
            long mostNegative = 0;
            for (int candidate : block.activeConstraints) {
                long numerator = multipliers.get(candidate);
                if (numerator < mostNegative) {
                    cut = candidate;
                    mostNegative = numerator;
                }
            }
            if (cut >= 0) {
                toSplit.add(block);
                cuts.add(cut);
            }
        }
        for (int index = 0; index < toSplit.size(); index++) {
            split(toSplit.get(index), cuts.get(index));
        }
        return !toSplit.isEmpty();
    }

    /**
     * Every tight constraint's Lagrange multiplier times the block's denominator, in one walk of the block's tree of
     * tight constraints rooted at its fixed variable (or its first member). Cutting a constraint leaves a subtree that
     * never holds the fixed variable; the multiplier is the subtree's Σ weight · (x − desired) when the subtree is the
     * constraint's right side, and minus it when it is the left side.
     */
    private Map<Integer, Long> multiplierNumerators(Block block) {
        Map<Integer, Long> multipliers = new HashMap<>();
        if (block.activeConstraints.isEmpty()) {
            return multipliers;
        }
        Map<Integer, List<Integer>> edgesOf = edgesByVariable(block);
        int root = block.fixedMember >= 0 ? block.fixedMember : block.members.get(0);
        List<Integer> visitOrder = new ArrayList<>();
        Map<Integer, Integer> edgeToParent = new HashMap<>();
        Deque<Integer> pending = new ArrayDeque<>();
        pending.push(root);
        edgeToParent.put(root, -1);
        while (!pending.isEmpty()) {
            int variable = pending.pop();
            visitOrder.add(variable);
            for (int edge : edgesOf.getOrDefault(variable, List.of())) {
                int neighbour = otherEnd(edge, variable);
                if (!edgeToParent.containsKey(neighbour)) {
                    edgeToParent.put(neighbour, edge);
                    pending.push(neighbour);
                }
            }
        }
        Map<Integer, Long> subtreeSum = new HashMap<>();
        for (int index = visitOrder.size() - 1; index > 0; index--) {
            int variable = visitOrder.get(index);
            long ownTerm = block.numerator + block.denominator * (offset[variable] - desired[variable]);
            long sum = subtreeSum.getOrDefault(variable, 0L) + weights[variable] * ownTerm;
            int edge = edgeToParent.get(variable);
            multipliers.put(edge, constraints.get(edge).right() == variable ? sum : -sum);
            subtreeSum.merge(otherEnd(edge, variable), sum, Long::sum);
        }
        return multipliers;
    }

    private Map<Integer, List<Integer>> edgesByVariable(Block block) {
        Map<Integer, List<Integer>> edgesOf = new HashMap<>();
        for (int edge : block.activeConstraints) {
            Constraint constraint = constraints.get(edge);
            edgesOf.computeIfAbsent(constraint.left(), key -> new ArrayList<>()).add(edge);
            edgesOf.computeIfAbsent(constraint.right(), key -> new ArrayList<>()).add(edge);
        }
        return edgesOf;
    }

    private int otherEnd(int edge, int variable) {
        Constraint constraint = constraints.get(edge);
        return constraint.left() == variable ? constraint.right() : constraint.left();
    }

    /** The members still joined to the constraint's right variable once the constraint is cut. */
    private Set<Integer> rightSide(Block block, int cut) {
        Map<Integer, List<Integer>> edgesOf = edgesByVariable(block);
        Set<Integer> side = new HashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        int start = constraints.get(cut).right();
        side.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            int variable = pending.poll();
            for (int edge : edgesOf.getOrDefault(variable, List.of())) {
                int neighbour = otherEnd(edge, variable);
                if (edge != cut && side.add(neighbour)) {
                    pending.add(neighbour);
                }
            }
        }
        return side;
    }

    private void split(Block block, int cut) {
        Set<Integer> rightSide = rightSide(block, cut);
        active[cut] = false;
        block.activeConstraints.remove(Integer.valueOf(cut));
        Block right = new Block();
        for (int member : block.members) {
            if (rightSide.contains(member)) {
                right.members.add(member);
                blockOf[member] = right;
            }
        }
        block.members.removeAll(right.members);
        for (int edge : block.activeConstraints) {
            if (rightSide.contains(constraints.get(edge).left())) {
                right.activeConstraints.add(edge);
            }
        }
        block.activeConstraints.removeAll(right.activeConstraints);
        if (block.fixedMember >= 0 && rightSide.contains(block.fixedMember)) {
            right.fixedMember = block.fixedMember;
            block.fixedMember = -1;
        }
        blocks.add(blocks.indexOf(block) + 1, right);
        recompute(block);
        recompute(right);
    }

    private void recompute(Block block) {
        if (block.fixedMember >= 0) {
            block.numerator = desired[block.fixedMember] - offset[block.fixedMember];
            block.denominator = 1;
            return;
        }
        long sum = 0;
        long weight = 0;
        for (int member : block.members) {
            sum += weights[member] * (desired[member] - offset[member]);
            weight += weights[member];
        }
        block.numerator = sum;
        block.denominator = weight;
    }

    /** Numerator of left + distance − right over the two blocks' denominators: positive when violated. */
    private long violationNumerator(Constraint constraint) {
        Block left = blockOf[constraint.left()];
        Block right = blockOf[constraint.right()];
        long whole = offset[constraint.left()] + constraint.distance() - offset[constraint.right()];
        if (left == right) {
            return whole;
        }
        return left.numerator * right.denominator + whole * left.denominator * right.denominator
                - right.numerator * left.denominator;
    }

    private double violation(Constraint constraint) {
        Block left = blockOf[constraint.left()];
        Block right = blockOf[constraint.right()];
        return (double) violationNumerator(constraint) / ((double) left.denominator * right.denominator);
    }

    private long[] roundedPositions() {
        long[] positions = new long[desired.length];
        for (Block block : blocks) {
            long position = Math.floorDiv(2 * block.numerator + block.denominator, 2 * block.denominator);
            for (int member : block.members) {
                positions[member] = position + offset[member];
            }
        }
        return positions;
    }

    /** Variables that move as one: position(member) = numerator / denominator + offset[member]. */
    private static final class Block {
        private final List<Integer> members = new ArrayList<>();
        private final List<Integer> activeConstraints = new ArrayList<>();
        private int fixedMember = -1;
        private long numerator;
        private long denominator = 1;
    }
}
