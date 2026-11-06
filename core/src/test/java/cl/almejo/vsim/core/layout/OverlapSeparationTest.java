package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapSeparationTest {

    private static final int GATE_SIZE = 4;
    private static final int BODY_GAP = 1;
    private static final int TEXT_GAP = 0;
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    @Test
    void twoOverlappingGatesSplitHorizontallyByTheLeastMovement() {
        LayoutBox first = gate("a", 0, 0);
        LayoutBox second = gate("b", 2, 0);

        Separation separation = OverlapSeparation.separate(List.of(first, second), NEVER_CANCELLED);

        Bounds firstAfter = after(first, separation);
        Bounds secondAfter = after(second, separation);
        assertThat(secondAfter.x() - firstAfter.maximumX()).as("gap").isEqualTo(BODY_GAP);
        assertThat(firstAfter.y()).isZero();
        assertThat(secondAfter.y()).isZero();
        assertThat(totalMovement(separation)).isEqualTo(3);
        assertThat(separation.deltas()).containsEntry(id("a"), new GridPoint(-1, 0))
                .containsEntry(id("b"), new GridPoint(2, 0));
        assertThat(separation.stillOverlapping()).isEmpty();
    }

    @Test
    void aFixedBoxNeverMoves() {
        LayoutBox fixed = new LayoutBox(id("a"), new Bounds(0, 0, GATE_SIZE, GATE_SIZE), false, BODY_GAP);
        LayoutBox movable = gate("b", 1, 1);

        Separation separation = OverlapSeparation.separate(List.of(fixed, movable), NEVER_CANCELLED);

        assertThat(separation.deltas()).containsOnlyKeys(id("b"));
        assertThat(overlapsWithGap(fixed.bounds(), after(movable, separation), BODY_GAP)).isFalse();
        assertThat(separation.stillOverlapping()).isEmpty();
    }

    @Test
    void boxesThatOverlapNothingDoNotMove() {
        LayoutBox first = gate("a", 0, 0);
        LayoutBox second = gate("b", 2, 0);
        LayoutBox faraway = gate("c", 40, 40);

        Separation separation = OverlapSeparation.separate(List.of(first, second, faraway), NEVER_CANCELLED);

        assertThat(separation.deltas()).doesNotContainKey(id("c"));
    }

    @Test
    void nothingOverlappingGivesNoDeltas() {
        Separation separation = OverlapSeparation.separate(
                List.of(gate("a", 0, 0), gate("b", 5, 0), gate("c", 0, 5)), NEVER_CANCELLED);

        assertThat(separation.deltas()).isEmpty();
        assertThat(separation.stillOverlapping()).isEmpty();
    }

    @Test
    void textsMayTouchButNotCover() {
        LayoutBox body = new LayoutBox(id("a"), new Bounds(0, 0, 4, 1), true, TEXT_GAP);
        LayoutBox touching = new LayoutBox(id("b"), new Bounds(4, 0, 4, 1), true, TEXT_GAP);
        LayoutBox covering = new LayoutBox(id("c"), new Bounds(6, 0, 4, 1), true, TEXT_GAP);

        Separation separation = OverlapSeparation.separate(List.of(body, touching), NEVER_CANCELLED);
        Separation covered = OverlapSeparation.separate(List.of(touching, covering), NEVER_CANCELLED);

        assertThat(separation.deltas()).isEmpty();
        assertThat(overlapsWithGap(after(touching, covered), after(covering, covered), TEXT_GAP)).isFalse();
        assertThat(totalMovement(covered)).as("one unit up or down is the cheapest").isEqualTo(1);
    }

    @Test
    void aStackOfFiveBecomesACompactColumnOrRow() {
        List<LayoutBox> stack = new ArrayList<>();
        for (String name : List.of("e", "c", "a", "d", "b")) {
            stack.add(gate(name, 10, 10));
        }

        Separation separation = OverlapSeparation.separate(stack, NEVER_CANCELLED);

        List<Bounds> inIdOrder = stack.stream().sorted(Comparator.comparing(box -> box.id().value()))
                .map(box -> after(box, separation)).toList();
        for (int index = 1; index < inIdOrder.size(); index++) {
            assertThat(inIdOrder.get(index).x() - inIdOrder.get(index - 1).maximumX()).isEqualTo(BODY_GAP);
            assertThat(inIdOrder.get(index).y()).isEqualTo(10);
        }
        Bounds union = inIdOrder.stream().reduce(Bounds::union).orElseThrow();
        assertThat(union.width()).isEqualTo(5 * GATE_SIZE + 4);
        assertThat(union.x() + union.width() / 2).as("centred where the stack was").isEqualTo(12);
        assertThat(separation.stillOverlapping()).isEmpty();
    }

    @Test
    void orderIsKept() {
        Random random = new Random(20261106L);
        for (int trial = 0; trial < 40; trial++) {
            List<LayoutBox> boxes = randomBoxes(random, 25);

            Separation separation = OverlapSeparation.separate(boxes, NEVER_CANCELLED);

            for (LayoutBox first : boxes) {
                for (LayoutBox second : boxes) {
                    if (first == second || !overlapsWithGap(first.bounds(), second.bounds(), BODY_GAP)) {
                        continue;
                    }
                    Bounds firstAfter = after(first, separation);
                    Bounds secondAfter = after(second, separation);
                    boolean separatedOnXOnly = !overlapsOnX(firstAfter, secondAfter, BODY_GAP)
                            && overlapsOnY(firstAfter, secondAfter, BODY_GAP);
                    if (separatedOnXOnly && centreTimesTwoX(first.bounds()) < centreTimesTwoX(second.bounds())) {
                        assertThat(firstAfter.x()).as("trial %d, %s left of %s", trial, first.id(), second.id())
                                .isLessThan(secondAfter.x());
                    }
                }
            }
        }
    }

    @Test
    void movableBoxesAloneAlwaysEndApartAndTheReportMatchesTheResult() {
        Random random = new Random(7L);
        for (int trial = 0; trial < 40; trial++) {
            List<LayoutBox> boxes = randomBoxes(random, 20);

            Separation separation = OverlapSeparation.separate(boxes, NEVER_CANCELLED);

            for (LayoutBox box : boxes) {
                boolean overlapsSomething = boxes.stream().anyMatch(other -> other != box
                        && overlapsWithGap(after(box, separation), after(other, separation), BODY_GAP));
                assertThat(separation.stillOverlapping().contains(box.id())).as("trial %d, %s", trial, box.id())
                        .isEqualTo(overlapsSomething);
            }
            assertThat(separation.stillOverlapping()).as("trial %d", trial).isEmpty();
        }
    }

    @Test
    void sameInputInAnyOrderGivesTheSameSeparation() {
        List<LayoutBox> boxes = randomBoxes(new Random(11L), 30);
        List<LayoutBox> shuffled = new ArrayList<>(boxes);
        Collections.shuffle(shuffled, new Random(3L));

        assertThat(OverlapSeparation.separate(shuffled, NEVER_CANCELLED))
                .isEqualTo(OverlapSeparation.separate(boxes, NEVER_CANCELLED));
    }

    @Test
    void aMovableBoxBetweenTwoFixedOnesThatCannotFitIsReported() {
        LayoutBox leftWall = new LayoutBox(id("a"), new Bounds(0, 0, GATE_SIZE, GATE_SIZE), false, BODY_GAP);
        LayoutBox rightWall = new LayoutBox(id("c"), new Bounds(6, 0, GATE_SIZE, GATE_SIZE), false, BODY_GAP);
        LayoutBox squeezed = gate("b", 3, 0);

        Separation separation = OverlapSeparation.separate(List.of(leftWall, squeezed, rightWall), NEVER_CANCELLED);

        assertThat(separation.stillOverlapping()).containsExactly(id("b"));
        assertThat(separation.deltas()).doesNotContainKeys(id("a"), id("c"));
        Bounds squeezedAfter = after(squeezed, separation);
        assertThat(squeezedAfter.x()).as("never pushed through a wall")
                .isBetween(leftWall.bounds().x(), rightWall.bounds().maximumX() - GATE_SIZE);
    }

    @Test
    void cancelledThrows() {
        assertThatThrownBy(() -> OverlapSeparation.separate(List.of(gate("a", 0, 0), gate("b", 1, 0)), () -> true))
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void duplicateIdsAreRejected() {
        assertThatThrownBy(() -> OverlapSeparation.separate(List.of(gate("a", 0, 0), gate("a", 9, 0)),
                NEVER_CANCELLED)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<LayoutBox> randomBoxes(Random random, int count) {
        List<LayoutBox> boxes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            boxes.add(new LayoutBox(id(String.format("box%03d", index)),
                    new Bounds(random.nextInt(30), random.nextInt(30), 1 + random.nextInt(6), 1 + random.nextInt(6)),
                    true, BODY_GAP));
        }
        return boxes;
    }

    private static LayoutBox gate(String name, int x, int y) {
        return new LayoutBox(id(name), new Bounds(x, y, GATE_SIZE, GATE_SIZE), true, BODY_GAP);
    }

    private static ElementId id(String name) {
        return new ElementId(name);
    }

    private static Bounds after(LayoutBox box, Separation separation) {
        GridPoint delta = separation.deltas().getOrDefault(box.id(), GridPoint.ORIGIN);
        return box.bounds().translate(delta.x(), delta.y());
    }

    private static int totalMovement(Separation separation) {
        return separation.deltas().values().stream()
                .mapToInt(delta -> Math.abs(delta.x()) + Math.abs(delta.y())).sum();
    }

    private static boolean overlapsWithGap(Bounds first, Bounds second, int gap) {
        return overlapsOnX(first, second, gap) && overlapsOnY(first, second, gap);
    }

    private static boolean overlapsOnX(Bounds first, Bounds second, int gap) {
        return first.x() < second.maximumX() + gap && second.x() < first.maximumX() + gap;
    }

    private static boolean overlapsOnY(Bounds first, Bounds second, int gap) {
        return first.y() < second.maximumY() + gap && second.y() < first.maximumY() + gap;
    }

    private static int centreTimesTwoX(Bounds bounds) {
        return 2 * bounds.x() + bounds.width();
    }
}
