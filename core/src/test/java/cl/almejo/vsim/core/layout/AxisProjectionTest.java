package cl.almejo.vsim.core.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AxisProjectionTest {

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;
    /** Desired positions and distances are multiples of 12, so the exact optimum (block sizes 1–4) is whole. */
    private static final long SCALE = 12;
    private static final int SEARCH_RADIUS = 200;

    @Test
    void satisfiedConstraintsMoveNothing() {
        long[] positions = AxisProjection.solve(new long[]{0, 10}, new boolean[]{false, false},
                List.of(new AxisProjection.Constraint(0, 1, 5)), NEVER_CANCELLED);

        assertThat(positions).containsExactly(0, 10);
    }

    @Test
    void aFixedVariablePushesOnlyTheMovableOne() {
        long[] positions = AxisProjection.solve(new long[]{0, 2}, new boolean[]{true, false},
                List.of(new AxisProjection.Constraint(0, 1, 5)), NEVER_CANCELLED);

        assertThat(positions).containsExactly(0, 5);
    }

    @Test
    void aHeavierVariableMovesLess() {
        List<AxisProjection.Constraint> apart = List.of(new AxisProjection.Constraint(0, 1, 3));

        long[] even = AxisProjection.solve(new long[]{0, 0}, new long[]{1, 1}, new boolean[]{false, false}, apart,
                NEVER_CANCELLED);
        long[] heavyRight = AxisProjection.solve(new long[]{0, 0}, new long[]{1, 8}, new boolean[]{false, false},
                apart, NEVER_CANCELLED);
        long[] heavyLeft = AxisProjection.solve(new long[]{0, 0}, new long[]{8, 1}, new boolean[]{false, false},
                apart, NEVER_CANCELLED);

        assertThat(even).containsExactly(-1, 2);
        assertThat(heavyRight).containsExactly(-3, 0);
        assertThat(heavyLeft).containsExactly(0, 3);
    }

    @Test
    void weightsShareAPushInProportion() {
        // Pushed 10 apart, the one four times as heavy moves a fifth of it: the least Σ weight · movement².
        long[] positions = AxisProjection.solve(new long[]{0, 0}, new long[]{1, 4}, new boolean[]{false, false},
                List.of(new AxisProjection.Constraint(0, 1, 10)), NEVER_CANCELLED);

        assertThat(positions).containsExactly(-8, 2);
    }

    @Test
    void aTightConstraintThatPullsIsReleased() {
        // b must clear a and c must clear b; a is far from b, so only b and c need to share the push.
        long[] positions = AxisProjection.solve(new long[]{-20, 0, 0}, new boolean[]{false, false, false},
                List.of(new AxisProjection.Constraint(0, 1, 1), new AxisProjection.Constraint(1, 2, 4)),
                NEVER_CANCELLED);

        assertThat(positions).containsExactly(-20, -2, 2);
    }

    @Test
    void twoFixedVariablesTooCloseLeaveTheirConstraintUnmet() {
        long[] positions = AxisProjection.solve(new long[]{0, 3, 6}, new boolean[]{true, false, true},
                List.of(new AxisProjection.Constraint(0, 1, 5), new AxisProjection.Constraint(1, 2, 5)),
                NEVER_CANCELLED);

        assertThat(positions[0]).isZero();
        assertThat(positions[2]).isEqualTo(6);
        assertThat(positions[1]).isBetween(0L, 6L);
    }

    @Test
    void theResultIsTheBestPlacementFoundByBruteForce() {
        Random random = new Random(20261106L);
        for (int trial = 0; trial < 300; trial++) {
            int count = 3 + random.nextInt(2);
            long[] desired = new long[count];
            boolean[] fixed = new boolean[count];
            for (int variable = 0; variable < count; variable++) {
                desired[variable] = SCALE * (random.nextInt(9) - 4);
            }
            if (count == 4) {
                fixed[random.nextInt(count)] = true;
            }
            List<AxisProjection.Constraint> constraints = randomAcyclicConstraints(random, count);

            long[] positions = AxisProjection.solve(desired, fixed, constraints, NEVER_CANCELLED);

            assertThat(satisfies(positions, constraints)).as("trial %d", trial).isTrue();
            for (int variable = 0; variable < count; variable++) {
                if (fixed[variable]) {
                    assertThat(positions[variable]).isEqualTo(desired[variable]);
                }
            }
            assertThat(cost(positions, desired)).as("trial %d", trial)
                    .isEqualTo(bestCost(desired, fixed, constraints));
        }
    }

    @Test
    void cancelledThrows() {
        assertThatThrownBy(() -> AxisProjection.solve(new long[]{0, 0}, new boolean[]{false, false},
                List.of(new AxisProjection.Constraint(0, 1, 4)), () -> true))
                .isInstanceOf(CancellationException.class);
    }

    private static List<AxisProjection.Constraint> randomAcyclicConstraints(Random random, int count) {
        List<AxisProjection.Constraint> constraints = new ArrayList<>();
        for (int left = 0; left < count; left++) {
            for (int right = left + 1; right < count; right++) {
                if (random.nextBoolean()) {
                    // Reversed index order half of the time, so the solver must find the order itself.
                    boolean reversed = random.nextBoolean();
                    constraints.add(new AxisProjection.Constraint(reversed ? right : left, reversed ? left : right,
                            SCALE * random.nextInt(5)));
                }
            }
        }
        return acyclic(constraints, count) ? constraints : List.of();
    }

    private static boolean acyclic(List<AxisProjection.Constraint> constraints, int count) {
        // With at most four variables, a cycle shows as a variable reaching itself within count steps.
        boolean[][] reaches = new boolean[count][count];
        constraints.forEach(constraint -> reaches[constraint.left()][constraint.right()] = true);
        for (int middle = 0; middle < count; middle++) {
            for (int from = 0; from < count; from++) {
                for (int to = 0; to < count; to++) {
                    reaches[from][to] |= reaches[from][middle] && reaches[middle][to];
                }
            }
        }
        for (int variable = 0; variable < count; variable++) {
            if (reaches[variable][variable]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The least cost over whole placements: every movable variable but the last is enumerated, the last one takes the
     * point of its feasible interval nearest to its desired position (its own term is the only one it changes).
     */
    private static long bestCost(long[] desired, boolean[] fixed, List<AxisProjection.Constraint> constraints) {
        List<Integer> movable = new ArrayList<>();
        for (int variable = 0; variable < desired.length; variable++) {
            if (!fixed[variable]) {
                movable.add(variable);
            }
        }
        return bestCost(0, movable, desired.clone(), desired, constraints);
    }

    private static long bestCost(int depth, List<Integer> movable, long[] positions, long[] desired,
                                 List<AxisProjection.Constraint> constraints) {
        int variable = movable.get(depth);
        if (depth == movable.size() - 1) {
            positions[variable] = nearestFeasible(variable, positions, desired[variable], constraints);
            return satisfies(positions, constraints) ? cost(positions, desired) : Long.MAX_VALUE;
        }
        long best = Long.MAX_VALUE;
        for (long position = -SEARCH_RADIUS; position <= SEARCH_RADIUS; position++) {
            positions[variable] = position;
            best = Math.min(best, bestCost(depth + 1, movable, positions, desired, constraints));
        }
        return best;
    }

    private static long nearestFeasible(int variable, long[] positions, long wanted,
                                        List<AxisProjection.Constraint> constraints) {
        long lowest = Long.MIN_VALUE;
        long highest = Long.MAX_VALUE;
        for (AxisProjection.Constraint constraint : constraints) {
            if (constraint.right() == variable) {
                lowest = Math.max(lowest, positions[constraint.left()] + constraint.distance());
            } else if (constraint.left() == variable) {
                highest = Math.min(highest, positions[constraint.right()] - constraint.distance());
            }
        }
        return Math.max(lowest, Math.min(highest, wanted));
    }

    private static boolean satisfies(long[] positions, List<AxisProjection.Constraint> constraints) {
        return constraints.stream().allMatch(constraint ->
                positions[constraint.right()] >= positions[constraint.left()] + constraint.distance());
    }

    private static long cost(long[] positions, long[] desired) {
        long cost = 0;
        for (int variable = 0; variable < positions.length; variable++) {
            long difference = positions[variable] - desired[variable];
            cost += difference * difference;
        }
        return cost;
    }
}
