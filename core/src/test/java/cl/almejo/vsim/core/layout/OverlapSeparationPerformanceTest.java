package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Separating stays fast on big inputs: before, every round compared all pairs of boxes and solved each axis as one
 * problem, so 3000 densely overlapping boxes took over a second and a 2000-part circuit with a few overlaps ~60 ms
 * (today about 0.3 s and 15 ms on the author's machine). The bound is very generous, some sixty times today's time,
 * so that a slow or busy CI runner (Windows, macOS) never fails it: it catches a runaway (a loop that no longer ends
 * soon, a growth far worse than the old one), not a return to the old growth itself.
 */
class OverlapSeparationPerformanceTest {

    private static final Duration GENEROUS = Duration.ofSeconds(20);
    private static final int BODY_GAP = 1;
    private static final int GRID_COLUMNS = 50;
    private static final int GRID_PITCH = 10;
    private static final int GATE_SIZE = 4;
    /** One part in this many sits on its left neighbour (50 overlapping pairs in 2000 parts). */
    private static final int ONE_MISPLACED_IN = 40;

    @Test
    void aBigCircuitWithAFewOverlapsIsSeparatedQuickly() {
        List<LayoutBox> boxes = gridWithAFewMisplaced(2000);

        Separation separation = timedSeparation(boxes);

        assertThat(separation.stillOverlapping()).isEmpty();
        assertThat(separation.deltas()).isNotEmpty().hasSizeLessThanOrEqualTo(4 * 2000 / ONE_MISPLACED_IN);
    }

    @Test
    void threeThousandDenselyOverlappingBoxesAreSeparatedQuickly() {
        Separation separation = timedSeparation(dense(3000, new Random(1)));

        assertThat(separation.deltas().size()).isGreaterThan(2000);
    }

    @Test
    void threeThousandDenselyOverlappingBoxesAmongFixedOnesAreSeparatedQuickly() {
        Random random = new Random(2);
        List<LayoutBox> boxes = new ArrayList<>();
        for (LayoutBox box : dense(3000, random)) {
            boolean fixed = random.nextInt(10) == 0;
            boxes.add(new LayoutBox(box.id(), box.bounds(), !fixed, box.gap()));
        }

        Separation separation = timedSeparation(boxes);

        assertThat(separation.deltas().size()).isGreaterThan(2000);
    }

    private static Separation timedSeparation(List<LayoutBox> boxes) {
        long start = System.nanoTime();
        Separation separation = OverlapSeparation.separate(boxes, () -> false);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).as("OverlapSeparation.separate on %d boxes", boxes.size()).isLessThan(GENEROUS);
        return separation;
    }

    /** Gates on a grid with room between them; every {@value #ONE_MISPLACED_IN}th one is dropped on its neighbour. */
    private static List<LayoutBox> gridWithAFewMisplaced(int count) {
        List<LayoutBox> boxes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int cell = index % ONE_MISPLACED_IN == ONE_MISPLACED_IN - 1 ? index - 1 : index;
            int shift = cell == index ? 0 : 2;
            int x = (cell % GRID_COLUMNS) * GRID_PITCH + shift;
            int y = (cell / GRID_COLUMNS) * GRID_PITCH + shift;
            boxes.add(new LayoutBox(new ElementId(String.format("gate%04d", index)),
                    new Bounds(x, y, GATE_SIZE, GATE_SIZE), true, BODY_GAP));
        }
        return boxes;
    }

    /** Boxes of 2 to 5 units covering about two thirds of a square: nearly every box overlaps several others. */
    private static List<LayoutBox> dense(int count, Random random) {
        int side = (int) Math.sqrt(count * 30.0);
        List<LayoutBox> boxes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            boxes.add(new LayoutBox(new ElementId(String.format("box%04d", index)), new Bounds(random.nextInt(side),
                    random.nextInt(side), 2 + random.nextInt(4), 2 + random.nextInt(4)), true, BODY_GAP));
        }
        return boxes;
    }
}
