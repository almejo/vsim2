package cl.almejo.vsim.core.geometry;

import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedSet;

/**
 * Pure snapping geometry (alignment-guides spec §3.1–§3.5, §4.4). Each axis is solved on its own: every pair (moving
 * line, guide or part line) whose correction is a whole number of grid units within the threshold is a candidate,
 * and the smallest correction wins, ties going to a guide, then centre↔centre, then the target whose box is nearest
 * to the moving box, then the smallest element id (then the smaller position and the earlier moving line, so the
 * choice never depends on the order of the targets). Grid lines are only tried on an axis where no guide or part was
 * admitted, so part alignment is never hidden by the grid.
 */
public final class Snapping {

    /** The magnet's reach in logical screen pixels (spec §3.2); tune it after trying it in the app. */
    static final double REACH_PIXELS = 12;

    /** Alignment lines drawn per axis at most, so a column of many aligned parts stays readable (spec §3.3). */
    static final int MAXIMUM_MATCHES_PER_AXIS = 3;

    private static final Comparator<Candidate> ALIGNMENT_ORDER = Comparator
            .comparingInt(Candidate::distance)
            .thenComparing(candidate -> candidate.target().kind() != SnapKind.GUIDE)
            .thenComparing(candidate -> !candidate.centreToCentre())
            .thenComparingLong(Candidate::boxGap)
            .thenComparing(candidate -> candidate.target().ownerId().map(ElementId::value).orElse(""))
            .thenComparingDouble(candidate -> candidate.target().position())
            .thenComparingInt(Candidate::lineIndex);

    private static final Comparator<Candidate> GRID_LINE_ORDER = Comparator
            .comparingInt(Candidate::distance)
            .thenComparingInt(Candidate::lineIndex)
            .thenComparingDouble(candidate -> candidate.target().position());

    private Snapping() {
    }

    /** The snapped delta for {@code input}, the target won on each axis and the alignment lines to draw. */
    public static SnapResult solve(SnapInput input) {
        Optional<Candidate> winnerX = choose(input, Axis.X);
        Optional<Candidate> winnerY = choose(input, Axis.Y);
        int deltaX = input.rawDeltaX() + winnerX.map(Candidate::correction).orElse(0);
        int deltaY = input.rawDeltaY() + winnerY.map(Candidate::correction).orElse(0);
        Optional<SnapTarget> targetX = winnerX.map(Candidate::target);
        Optional<SnapTarget> targetY = winnerY.map(Candidate::target);
        List<SnapMatch> matches = new ArrayList<>();
        matches.addAll(axisMatches(input, Axis.X, deltaX, targetX));
        matches.addAll(axisMatches(input, Axis.Y, deltaY, targetY));
        return new SnapResult(deltaX, deltaY, targetX, targetY, matches);
    }

    /**
     * The alignment lines that really hold at {@code deltaX, deltaY}, for when a clamp (locked frame walls, the
     * segment-drag axis lock) changed the snapped delta: per axis in the order of the moving lines, at most three.
     */
    public static List<SnapMatch> matchesAt(SnapInput input, int deltaX, int deltaY) {
        List<SnapMatch> matches = new ArrayList<>();
        matches.addAll(axisMatches(input, Axis.X, deltaX, Optional.empty()));
        matches.addAll(axisMatches(input, Axis.Y, deltaY, Optional.empty()));
        return matches;
    }

    /** The magnet's reach in grid units at a zoom of {@code pixelsPerUnit} screen pixels per grid unit. */
    public static int thresholdUnits(double pixelsPerUnit) {
        if (!(pixelsPerUnit > 0)) {
            throw new IllegalArgumentException("pixels per unit must be positive: " + pixelsPerUnit);
        }
        return Math.max(1, (int) Math.floor(REACH_PIXELS / pixelsPerUnit));
    }

    private static Optional<Candidate> choose(SnapInput input, Axis axis) {
        Optional<Candidate> alignment = alignmentCandidates(input, axis).stream().min(ALIGNMENT_ORDER);
        if (alignment.isPresent()) {
            return alignment;
        }
        return gridLineCandidates(input, axis).stream().min(GRID_LINE_ORDER);
    }

    private static List<Candidate> alignmentCandidates(SnapInput input, Axis axis) {
        List<Double> movingLines = movingLines(input, axis);
        int rawDelta = rawDelta(input, axis);
        Optional<Bounds> movedBox = input.movingBox().map(box -> box.translate(input.rawDeltaX(), input.rawDeltaY()));
        List<Candidate> candidates = new ArrayList<>();
        for (SnapTarget target : alignmentTargets(input, axis)) {
            for (int lineIndex = 0; lineIndex < movingLines.size(); lineIndex++) {
                double movingLine = movingLines.get(lineIndex);
                OptionalInt correction = admittedCorrection(target.position() - (movingLine + rawDelta),
                        input.thresholdUnits());
                if (correction.isPresent()) {
                    candidates.add(new Candidate(target, lineIndex, correction.getAsInt(),
                            isCentreToCentre(input, axis, movingLine, target), boxGap(movedBox, target, axis)));
                }
            }
        }
        return candidates;
    }

    /**
     * Multiples of the spacing on both sides of each moving line, generated arithmetically (spec §3.5). A box only
     * offers its left/top edge (its first line), so its top-left corner always lands on a grid line whatever its size;
     * wire end points all count. Grid lines reach half their spacing (or the magnet's reach when larger), so on an axis
     * with nothing else to align to the move always lands on the nearest line, whatever the zoom.
     */
    private static List<Candidate> gridLineCandidates(SnapInput input, Axis axis) {
        if (input.gridLineSpacing().isEmpty()) {
            return List.of();
        }
        int spacing = input.gridLineSpacing().getAsInt();
        if (spacing <= 0) {
            throw new IllegalArgumentException("grid line spacing must be positive: " + spacing);
        }
        List<Double> movingLines = movingLines(input, axis);
        int rawDelta = rawDelta(input, axis);
        List<Candidate> candidates = new ArrayList<>();
        int reach = Math.max(input.thresholdUnits(), spacing / 2);
        int lineCount = input.movingBox().isPresent() ? Math.min(1, movingLines.size()) : movingLines.size();
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            double movedLine = movingLines.get(lineIndex) + rawDelta;
            double lowerLine = Math.floor(movedLine / spacing) * spacing;
            for (double gridLine : List.of(lowerLine, lowerLine + spacing)) {
                OptionalInt correction = admittedCorrection(gridLine - movedLine, reach);
                if (correction.isPresent()) {
                    SnapTarget target = new SnapTarget(SnapKind.GRID_LINE, axis, gridLine, Optional.empty(),
                            Optional.empty());
                    candidates.add(new Candidate(target, lineIndex, correction.getAsInt(), false, Long.MAX_VALUE));
                }
            }
        }
        return candidates;
    }

    /** The correction as whole grid units when it is one and within reach; moves always stay on the grid. */
    private static OptionalInt admittedCorrection(double correction, int thresholdUnits) {
        if (Math.abs(correction) > thresholdUnits || correction != Math.rint(correction)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) correction);
    }

    private static boolean isCentreToCentre(SnapInput input, Axis axis, double movingLine, SnapTarget target) {
        if (input.movingBox().isEmpty() || target.ownerBox().isEmpty()) {
            return false;
        }
        return movingLine == centre(input.movingBox().get(), axis)
                && target.position() == centre(target.ownerBox().get(), axis);
    }

    /** The gap between the boxes along the other axis (0 when they overlap); no box is the farthest. */
    private static long boxGap(Optional<Bounds> movedBox, SnapTarget target, Axis axis) {
        if (movedBox.isEmpty() || target.ownerBox().isEmpty()) {
            return Long.MAX_VALUE;
        }
        Bounds moving = movedBox.get();
        Bounds owner = target.ownerBox().get();
        if (axis == Axis.X) {
            return intervalGap(moving.y(), moving.maximumY(), owner.y(), owner.maximumY());
        }
        return intervalGap(moving.x(), moving.maximumX(), owner.x(), owner.maximumX());
    }

    private static long intervalGap(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return Math.max(0L, Math.max((long) firstStart, secondStart) - Math.min((long) firstEnd, secondEnd));
    }

    private static double centre(Bounds box, Axis axis) {
        return axis == Axis.X ? box.x() + box.width() / 2.0 : box.y() + box.height() / 2.0;
    }

    /**
     * The target lines exactly matching a moving line at {@code delta}, one match per coordinate: the winner's
     * coordinate first, then the others in the order of the moving lines (edge-start, centre, edge-end), at most
     * {@link #MAXIMUM_MATCHES_PER_AXIS}. A grid-line winner draws nothing.
     */
    private static List<SnapMatch> axisMatches(SnapInput input, Axis axis, int delta, Optional<SnapTarget> winner) {
        List<SnapTarget> targets = alignmentTargets(input, axis);
        SequencedSet<Double> coordinates = new LinkedHashSet<>();
        winner.filter(target -> target.kind() != SnapKind.GRID_LINE)
                .ifPresent(target -> coordinates.add(target.position()));
        for (double movingLine : movingLines(input, axis)) {
            double movedLine = movingLine + delta;
            if (targets.stream().anyMatch(target -> target.position() == movedLine)) {
                coordinates.add(movedLine);
            }
        }
        return coordinates.stream()
                .limit(MAXIMUM_MATCHES_PER_AXIS)
                .map(coordinate -> new SnapMatch(axis, coordinate,
                        targets.stream().filter(target -> target.position() == coordinate).toList()))
                .toList();
    }

    private static List<SnapTarget> alignmentTargets(SnapInput input, Axis axis) {
        return input.targets().stream()
                .filter(target -> target.axis() == axis && target.kind() != SnapKind.GRID_LINE)
                .toList();
    }

    private static List<Double> movingLines(SnapInput input, Axis axis) {
        return input.movingLines().getOrDefault(axis, List.of());
    }

    private static int rawDelta(SnapInput input, Axis axis) {
        return axis == Axis.X ? input.rawDeltaX() : input.rawDeltaY();
    }

    /** One admitted pair (moving line, target) with what the tie-breaks need. */
    private record Candidate(SnapTarget target, int lineIndex, int correction, boolean centreToCentre, long boxGap) {

        int distance() {
            return Math.abs(correction);
        }
    }
}
