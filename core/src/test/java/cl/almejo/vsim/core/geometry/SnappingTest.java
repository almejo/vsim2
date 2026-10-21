package cl.almejo.vsim.core.geometry;

import cl.almejo.vsim.core.document.ElementId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnappingTest {

    private static final int ONE_UNIT = 1;
    private static final OptionalInt NO_GRID_LINES = OptionalInt.empty();
    private static final OptionalInt GRID_LINES_EVERY_TWO = OptionalInt.of(2);

    @Test
    void theThresholdIsTwelvePixelsWithAMinimumOfOneUnit() {
        assertThat(Snapping.thresholdUnits(2)).as("25 %").isEqualTo(6);
        assertThat(Snapping.thresholdUnits(4)).as("50 %").isEqualTo(3);
        assertThat(Snapping.thresholdUnits(8)).as("100 %").isEqualTo(1);
        assertThat(Snapping.thresholdUnits(16)).as("200 %").isEqualTo(1);
        assertThat(Snapping.thresholdUnits(32)).as("400 %").isEqualTo(1);
    }

    @Test
    void theThresholdRejectsAScaleThatIsNotPositive() {
        assertThatThrownBy(() -> Snapping.thresholdUnits(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEdgeSnapsToTheEdgeOfAnotherPart() {
        List<SnapTarget> targets = partLines("gate", new Bounds(10, 30, 6, 6));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 5, 0, targets, ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(6);
        assertThat(result.deltaY()).isZero();
        assertThat(result.winnerX()).contains(targets.get(0));
        assertThat(result.winnerY()).isEmpty();
    }

    @Test
    void aCentreSnapsToTheCentreOfAnotherPart() {
        Bounds owner = new Bounds(10, 30, 6, 6);
        SnapTarget centre = partLine("gate", Axis.X, 13, owner);

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 6, 6), 9, 0, List.of(centre), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(10);
        assertThat(result.winnerX()).contains(centre);
    }

    @Test
    void anEdgeSnapsToTheCentreOfAnotherPart() {
        SnapTarget centre = partLine("gate", Axis.X, 13, new Bounds(10, 30, 6, 6));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 8, 0, List.of(centre), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(9);
        assertThat(result.winnerX()).contains(centre);
    }

    @Test
    void aHalfUnitCentreIsNeverACandidate() {
        SnapTarget halfUnitCentre = partLine("gate", Axis.X, 12.5, new Bounds(10, 30, 5, 5));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0, List.of(halfUnitCentre), 3));

        assertThat(result.deltaX()).isEqualTo(10);
        assertThat(result.winnerX()).isEmpty();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void aHalfUnitMismatchLosesToAWholeOneEvenWhenItIsCloser() {
        SnapTarget edge = partLine("gate", Axis.X, 10, new Bounds(10, 30, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 3, 3), 8, 0, List.of(edge), ONE_UNIT));

        assertThat(result.deltaX()).as("the right edge 11 moves to 10, not the centre 9.5").isEqualTo(7);
    }

    @Test
    void aTargetBeyondTheThresholdDoesNotPull() {
        SnapTarget edge = partLine("gate", Axis.X, 10, new Bounds(10, 30, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 4, 0, List.of(edge), ONE_UNIT));

        assertThat(result).isEqualTo(SnapResult.unsnapped(4, 0));
    }

    @Test
    void theSmallestCorrectionWins() {
        SnapTarget nearer = partLine("near", Axis.X, 15, new Bounds(15, 30, 4, 4));
        SnapTarget farther = partLine("far", Axis.X, 8, new Bounds(4, 30, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0, List.of(farther, nearer), 3));

        assertThat(result.deltaX()).isEqualTo(11);
        assertThat(result.winnerX()).contains(nearer);
    }

    @Test
    void aGuideWinsATieOverAPart() {
        SnapTarget part = partLine("gate", Axis.X, 9, new Bounds(5, 30, 4, 4));
        SnapTarget guide = guide(Axis.X, 15);

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0, List.of(part, guide), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(11);
        assertThat(result.winnerX()).contains(guide);
    }

    @Test
    void centreToCentreWinsATieOverAnEdge() {
        SnapTarget centre = partLine("gate", Axis.X, 13, new Bounds(10, 30, 6, 6));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0, List.of(centre), ONE_UNIT));

        assertThat(result.deltaX()).as("centre 12 to 13, not right edge 14 to 13").isEqualTo(11);
    }

    @Test
    void theNearestBoxWinsATie() {
        SnapTarget farAway = partLine("far", Axis.X, 15, new Bounds(15, 100, 4, 4));
        SnapTarget close = partLine("near", Axis.X, 9, new Bounds(5, 10, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0, List.of(farAway, close), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(9);
        assertThat(result.winnerX()).contains(close);
    }

    @Test
    void anOverlappingBoxCountsAsNoGap() {
        SnapTarget overlapping = partLine("over", Axis.X, 15, new Bounds(15, 2, 4, 4));
        SnapTarget touchingBelow = partLine("below", Axis.X, 9, new Bounds(5, 5, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0,
                List.of(touchingBelow, overlapping), ONE_UNIT));

        assertThat(result.winnerX()).contains(overlapping);
    }

    @Test
    void theSmallestIdWinsATieAtTheSameDistance() {
        Bounds right = new Bounds(15, 10, 4, 4);
        Bounds left = new Bounds(5, 10, 4, 4);

        SnapResult leftIsFirst = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0,
                List.of(partLine("b", Axis.X, 15, right), partLine("a", Axis.X, 9, left)), ONE_UNIT));
        SnapResult rightIsFirst = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 10, 0,
                List.of(partLine("a", Axis.X, 15, right), partLine("b", Axis.X, 9, left)), ONE_UNIT));

        assertThat(leftIsFirst.deltaX()).isEqualTo(9);
        assertThat(rightIsFirst.deltaX()).isEqualTo(11);
    }

    @Test
    void eachAxisIsSolvedOnItsOwn() {
        SnapTarget verticalEdge = partLine("gate", Axis.X, 10, new Bounds(10, 50, 4, 4));
        SnapTarget horizontalGuide = guide(Axis.Y, 12);

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 5, 7,
                List.of(verticalEdge, horizontalGuide), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(6);
        assertThat(result.deltaY()).isEqualTo(8);
        assertThat(result.winnerX()).contains(verticalEdge);
        assertThat(result.winnerY()).contains(horizontalGuide);
    }

    @Test
    void aTargetOnOneAxisNeverPullsTheOther() {
        SnapTarget horizontalGuide = guide(Axis.Y, 6);

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 5, 0, List.of(horizontalGuide), ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(5);
        assertThat(result.winnerX()).isEmpty();
    }

    @Test
    void gridLinesPullOnlyAnAxisWithoutAnyOtherCandidate() {
        SnapTarget verticalEdge = partLine("gate", Axis.X, 10, new Bounds(10, 50, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 5, 5, List.of(verticalEdge),
                ONE_UNIT, GRID_LINES_EVERY_TWO));

        assertThat(result.deltaX()).isEqualTo(6);
        assertThat(result.winnerX()).contains(verticalEdge);
        assertThat(result.deltaY()).as("the top edge 5 goes to the lower line 4").isEqualTo(4);
        assertThat(result.winnerY()).hasValueSatisfying(winner -> {
            assertThat(winner.kind()).isEqualTo(SnapKind.GRID_LINE);
            assertThat(winner.position()).isEqualTo(4);
        });
        assertThat(result.matches()).as("grid-line snaps draw nothing")
                .allSatisfy(match -> assertThat(match.axis()).isEqualTo(Axis.X));
    }

    @Test
    void aPartBeatsAGridLineThatIsCloser() {
        SnapTarget edge = partLine("gate", Axis.X, 11, new Bounds(11, 50, 4, 4));

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 6, 0, List.of(edge),
                ONE_UNIT, GRID_LINES_EVERY_TWO));

        assertThat(result.deltaX()).isEqualTo(7);
    }

    @Test
    void aBoxAlreadyOnAGridLineStaysThere() {
        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 6, 2, List.of(),
                ONE_UNIT, GRID_LINES_EVERY_TWO));

        assertThat(result.deltaX()).isEqualTo(6);
        assertThat(result.deltaY()).isEqualTo(2);
        assertThat(result.winnerX()).hasValueSatisfying(winner -> assertThat(winner.position()).isEqualTo(6));
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void withoutGridLinesNothingPullsAnEmptyAxis() {
        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 5, 5, List.of(), ONE_UNIT));

        assertThat(result).isEqualTo(SnapResult.unsnapped(5, 5));
    }

    @Test
    void fortyAlignedInputsDrawOneLinePerCoordinate() {
        List<SnapTarget> targets = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            targets.addAll(partLines("input-%02d".formatted(index), new Bounds(10, 20 + 10 * index, 4, 4)));
        }

        SnapResult result = Snapping.solve(boxInput(new Bounds(0, 0, 4, 4), 9, 0, targets, ONE_UNIT));

        assertThat(result.deltaX()).isEqualTo(10);
        assertThat(result.winnerX()).hasValueSatisfying(winner -> {
            assertThat(winner.position()).as("centre to centre").isEqualTo(12);
            assertThat(winner.ownerId()).contains(new ElementId("input-00"));
        });
        assertThat(result.matches()).extracting(SnapMatch::position).as("winner, then left edge, right edge")
                .containsExactly(12.0, 10.0, 14.0);
        assertThat(result.matches()).allSatisfy(match -> {
            assertThat(match.axis()).isEqualTo(Axis.X);
            assertThat(match.targets()).hasSize(40);
        });
    }

    @Test
    void atMostThreeLinesAreDrawnPerAxis() {
        List<Double> wireEnds = List.of(0.0, 4.0, 8.0, 12.0, 16.0);
        List<SnapTarget> guides = wireEnds.stream().map(position -> guide(Axis.X, position.intValue())).toList();

        SnapResult result = Snapping.solve(new SnapInput(Map.of(Axis.X, wireEnds), 0, 0, guides, ONE_UNIT,
                NO_GRID_LINES, Optional.empty()));

        assertThat(result.matches()).extracting(SnapMatch::position).containsExactly(0.0, 4.0, 8.0);
    }

    @Test
    void theEndsOfMovedWiresSnapToAGuide() {
        SnapTarget guide = guide(Axis.X, 8);

        SnapResult result = Snapping.solve(new SnapInput(Map.of(Axis.X, List.of(3.0, 7.0, 20.0)), 0, 4,
                List.of(guide), ONE_UNIT, NO_GRID_LINES, Optional.empty()));

        assertThat(result.deltaX()).isEqualTo(1);
        assertThat(result.deltaY()).as("no moving lines on y").isEqualTo(4);
        assertThat(result.matches()).containsExactly(new SnapMatch(Axis.X, 8, List.of(guide)));
    }

    @Test
    void nothingMovingMeansNoSnap() {
        SnapResult result = Snapping.solve(new SnapInput(Map.of(), 3, 4, List.of(guide(Axis.X, 3)), ONE_UNIT,
                GRID_LINES_EVERY_TWO, Optional.empty()));

        assertThat(result).isEqualTo(SnapResult.unsnapped(3, 4));
    }

    @Test
    void theMatchesCanBeRecomputedForAClampedDelta() {
        SnapInput input = boxInput(new Bounds(0, 0, 4, 4), 5, 0,
                partLines("gate", new Bounds(10, 30, 6, 6)), ONE_UNIT);

        assertThat(Snapping.matchesAt(input, 6, 0)).extracting(SnapMatch::position).containsExactly(10.0);
        assertThat(Snapping.matchesAt(input, 5, 0)).isEmpty();
    }

    private static SnapInput boxInput(Bounds movingBox, int rawDeltaX, int rawDeltaY, List<SnapTarget> targets,
                                      int thresholdUnits) {
        return boxInput(movingBox, rawDeltaX, rawDeltaY, targets, thresholdUnits, NO_GRID_LINES);
    }

    private static SnapInput boxInput(Bounds movingBox, int rawDeltaX, int rawDeltaY, List<SnapTarget> targets,
                                      int thresholdUnits, OptionalInt gridLineSpacing) {
        Map<Axis, List<Double>> movingLines = Map.of(
                Axis.X, List.of((double) movingBox.x(), movingBox.x() + movingBox.width() / 2.0,
                        (double) movingBox.maximumX()),
                Axis.Y, List.of((double) movingBox.y(), movingBox.y() + movingBox.height() / 2.0,
                        (double) movingBox.maximumY()));
        return new SnapInput(movingLines, rawDeltaX, rawDeltaY, targets, thresholdUnits, gridLineSpacing,
                Optional.of(movingBox));
    }

    private static List<SnapTarget> partLines(String id, Bounds box) {
        return List.of(
                partLine(id, Axis.X, box.x(), box),
                partLine(id, Axis.X, box.x() + box.width() / 2.0, box),
                partLine(id, Axis.X, box.maximumX(), box),
                partLine(id, Axis.Y, box.y(), box),
                partLine(id, Axis.Y, box.y() + box.height() / 2.0, box),
                partLine(id, Axis.Y, box.maximumY(), box));
    }

    private static SnapTarget partLine(String id, Axis axis, double position, Bounds box) {
        return new SnapTarget(SnapKind.PART, axis, position, Optional.of(new ElementId(id)), Optional.of(box));
    }

    private static SnapTarget guide(Axis axis, int position) {
        return new SnapTarget(SnapKind.GUIDE, axis, position, Optional.empty(), Optional.empty());
    }
}
