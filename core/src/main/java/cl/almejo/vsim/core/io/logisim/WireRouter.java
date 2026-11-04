package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Finds the wire that joins a pin to its net on VSim's grid without touching another net ({@link GridOccupancy}):
 * the cheapest path in grid steps, each bend costing {@link #BEND_COST} more, that may cross other wires only at a
 * right angle and goes straight through a crossing. The result is the path's corners, from the start to the goal.
 */
final class WireRouter {

    /** A bend costs as much as this many steps, so the wire takes few of them (an L where it can). */
    private static final int BEND_COST = 3;
    /** How far beyond what is drawn a wire may go around something. */
    private static final int MARGIN = 10;
    /** A search that has looked at this many places gives up (the pin is left unconnected and noted). */
    private static final int MOST_STATES = 400_000;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int START = DIRECTIONS.length;

    private final GridOccupancy occupancy;

    WireRouter(GridOccupancy occupancy) {
        this.occupancy = occupancy;
    }

    /** One place and the way the wire arrived there ({@link #START} at the start). */
    private record State(GridPoint point, int heading) {
    }

    private record Step(State state, int cost) {
    }

    /**
     * The corners of a wire of {@code net} from {@code start} to the first point {@code goal} accepts (that no other
     * net's wire crosses), or empty when there is none nearby.
     */
    Optional<List<GridPoint>> route(GridPoint start, int net, Predicate<GridPoint> goal) {
        Map<State, Integer> costs = new HashMap<>();
        Map<State, State> previous = new HashMap<>();
        PriorityQueue<Step> pending = new PriorityQueue<>(Comparator.comparingInt(Step::cost));
        State first = new State(start, START);
        costs.put(first, 0);
        pending.add(new Step(first, 0));
        while (!pending.isEmpty() && costs.size() < MOST_STATES) {
            Step step = pending.poll();
            State current = step.state();
            if (step.cost() > costs.get(current)) {
                continue;
            }
            if (!current.point().equals(start) && goal.test(current.point())
                    && !occupancy.isCrossing(current.point(), net)) {
                return Optional.of(corners(current, previous));
            }
            boolean crossing = current.heading() != START && occupancy.isCrossing(current.point(), net);
            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                if (crossing && direction != current.heading()) {
                    continue;
                }
                GridPoint next = current.point().translate(DIRECTIONS[direction][0], DIRECTIONS[direction][1]);
                if (!occupancy.isNear(next, MARGIN) || !occupancy.admitsPassage(next, net, direction % 2 == 0)) {
                    continue;
                }
                boolean bends = current.heading() != START && direction != current.heading();
                int cost = step.cost() + 1 + (bends ? BEND_COST : 0);
                State nextState = new State(next, direction);
                if (cost < costs.getOrDefault(nextState, Integer.MAX_VALUE)) {
                    costs.put(nextState, cost);
                    previous.put(nextState, current);
                    pending.add(new Step(nextState, cost));
                }
            }
        }
        return Optional.empty();
    }

    /** The start, every bend and the goal of the path that ends at {@code last}. */
    private static List<GridPoint> corners(State last, Map<State, State> previous) {
        List<GridPoint> corners = new ArrayList<>();
        corners.add(last.point());
        State current = last;
        State before = previous.get(current);
        while (before != null) {
            if (before.heading() == START || before.heading() != current.heading()) {
                corners.add(before.point());
            }
            current = before;
            before = previous.get(current);
        }
        Collections.reverse(corners);
        return corners;
    }
}
