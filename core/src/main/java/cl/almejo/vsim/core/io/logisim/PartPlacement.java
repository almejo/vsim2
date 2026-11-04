package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where a part goes on a grid already partly drawn ({@link GridOccupancy}), for both Logisim layouts: the import places
 * VSim parts by their pins, the export Logisim parts by their ends. The part is tried at each of its choices (the
 * import's rotations; the export has one) around where its ends come nearest their targets; the spot wins whose ends
 * come nearest them, plus the choice's penalty, without any end touching another net. Spots further away are tried
 * when the near ones are taken; with none free the caller puts the part {@link #apart}.
 */
final class PartPlacement {

    /** How far (in units, each way) from the estimate a part is tried when its ends there would touch other nets. */
    private static final int SEARCH_RADIUS = 4;
    private static final int LARGEST_SEARCH_RADIUS = 16;
    /** How far below everything drawn a part that fits nowhere near its place goes, in units. */
    private static final int APART_GAP = 3;

    /** One end of the part: its offset from the part's position, its net and, when it must meet one, its target. */
    record End(GridPoint offset, int net, Optional<GridPoint> target) {
    }

    /** One way to place the part: its ends, where its search starts and what choosing it costs on top. */
    record Choice(List<End> ends, GridPoint estimate, int penalty) {
        Choice {
            ends = List.copyOf(ends);
        }
    }

    /** Where the part goes, and which of the choices (its index in the list searched). */
    record Spot(GridPoint position, int choice) {
    }

    private final GridOccupancy occupancy;

    PartPlacement(GridOccupancy occupancy) {
        this.occupancy = occupancy;
    }

    /**
     * The position that puts the ends nearest their targets: on each axis the median of (target − offset). With no
     * end to meet, {@code fallback}'s position.
     */
    static GridPoint estimate(List<End> ends, Supplier<GridPoint> fallback) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (End end : ends) {
            if (end.target().isPresent()) {
                xs.add(end.target().get().x() - end.offset().x());
                ys.add(end.target().get().y() - end.offset().y());
            }
        }
        if (xs.isEmpty()) {
            return fallback.get();
        }
        return new GridPoint(median(xs), median(ys));
    }

    /**
     * The cheapest free spot near its choice's estimate, the near ones first; the earlier choice wins on equal cost,
     * and the spot nearest the estimate (a part with no end to meet stays put). Empty when none is free.
     */
    Optional<Spot> bestFree(List<Choice> choices) {
        return bestFree(choices, SEARCH_RADIUS).or(() -> bestFree(choices, LARGEST_SEARCH_RADIUS));
    }

    /** Below everything drawn so far, under {@code choice}'s estimate: nothing there to touch. */
    GridPoint apart(Choice choice) {
        int highestEnd = choice.ends().stream().mapToInt(end -> end.offset().y()).min().orElse(0);
        GridPoint position = new GridPoint(choice.estimate().x(), occupancy.bottom() + APART_GAP - highestEnd);
        while (!isFree(position, choice.ends())) {
            position = position.translate(0, 1);
        }
        return position;
    }

    private Optional<Spot> bestFree(List<Choice> choices, int radius) {
        Spot best = null;
        int bestCost = 0;
        int bestDistance = 0;
        for (int index = 0; index < choices.size(); index++) {
            Choice choice = choices.get(index);
            for (int deltaX = -radius; deltaX <= radius; deltaX++) {
                for (int deltaY = -radius; deltaY <= radius; deltaY++) {
                    GridPoint position = choice.estimate().translate(deltaX, deltaY);
                    if (!isFree(position, choice.ends())) {
                        continue;
                    }
                    int cost = cost(position, choice.ends()) + choice.penalty();
                    int distance = Math.abs(deltaX) + Math.abs(deltaY);
                    if (best == null || cost < bestCost || cost == bestCost && distance < bestDistance) {
                        best = new Spot(position, index);
                        bestCost = cost;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isFree(GridPoint position, List<End> ends) {
        Map<GridPoint, Integer> taken = new HashMap<>();
        for (End end : ends) {
            GridPoint point = position.plus(end.offset());
            Integer other = taken.putIfAbsent(point, end.net());
            if (other != null && other != end.net() || !occupancy.admitsPoint(point, end.net())) {
                return false;
            }
        }
        return true;
    }

    private static int cost(GridPoint position, List<End> ends) {
        int cost = 0;
        for (End end : ends) {
            if (end.target().isPresent()) {
                GridPoint point = position.plus(end.offset());
                cost += Math.abs(point.x() - end.target().get().x()) + Math.abs(point.y() - end.target().get().y());
            }
        }
        return cost;
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
