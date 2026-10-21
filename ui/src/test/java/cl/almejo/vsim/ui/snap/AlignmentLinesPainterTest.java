package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.SnapKind;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapTarget;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The alignment lines: from the moved box to the farthest match, one unit past, 1 px at any zoom, with end ticks. */
class AlignmentLinesPainterTest {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 100;

    private final TestToolContext context = TestToolContext.withNewDocument();

    @AfterEach
    void close() {
        context.close();
    }

    /** Paints at 400 % with the grid origin at (10, 10) screen pixels, as the canvas does (document transform on). */
    private BufferedImage paint(List<SnapMatch> matches, Bounds movedBox) {
        context.viewport.zoomAt(0, 0, 4);
        context.viewport.panBy(10, 10);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.transform(context.viewport.documentTransform());
        AlignmentLinesPainter.paint(graphics, context, Optional.of(movedBox), matches);
        graphics.dispose();
        return image;
    }

    private static boolean painted(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) == ColorScheme.LIGHT.alignmentGuide().getRGB();
    }

    @Test
    void aTopEdgeMatchRunsFromTheTargetToTheMovedBoxOneUnitPastBothOneLogicalPixelThick() {
        SnapTarget target = new SnapTarget(SnapKind.PART, Axis.Y, 0, Optional.of(new ElementId("c1")),
                Optional.of(new Bounds(2, 0, 4, 4)));
        BufferedImage image = paint(List.of(new SnapMatch(Axis.Y, 0, List.of(target))), new Bounds(20, 0, 4, 4));
        // from x = 1 (2 − 1) to 25 (24 + 1) grid units: 42 .. 810 screen pixels, on row 10
        assertThat(painted(image, 400, 10)).isTrue();
        assertThat(painted(image, 400, 9)).isFalse();
        assertThat(painted(image, 400, 11)).isFalse();
        assertThat(painted(image, 43, 10)).isTrue();
        assertThat(painted(image, 30, 10)).isFalse();
        assertThat(painted(image, 820, 10)).isFalse();
        // the ticks across both ends
        assertThat(painted(image, 42, 10 - AlignmentLinesPainter.TICK_HALF_LENGTH)).isTrue();
        assertThat(painted(image, 810, 10 + AlignmentLinesPainter.TICK_HALF_LENGTH)).isTrue();
        assertThat(painted(image, 42, 10 - AlignmentLinesPainter.TICK_HALF_LENGTH - 1)).isFalse();
    }

    @Test
    void aGuideMatchAddsNoLengthAndNothingIsPaintedWithoutMatches() {
        SnapTarget guide = new SnapTarget(SnapKind.GUIDE, Axis.X, 20, Optional.empty(), Optional.empty());
        BufferedImage image = paint(List.of(new SnapMatch(Axis.X, 20, List.of(guide))), new Bounds(20, 0, 4, 1));
        // x = 20 → 10 + 640 = 650; only the moved box, −1 .. 2 units → −22 .. 74 px
        assertThat(painted(image, 650, 70)).isTrue();
        assertThat(painted(image, 650, 80)).isFalse();

        BufferedImage empty = paint(List.of(), new Bounds(20, 5, 4, 4));
        for (int x = 0; x < WIDTH; x += 7) {
            for (int y = 0; y < HEIGHT; y++) {
                assertThat(empty.getRGB(x, y)).isZero();
            }
        }
    }
}
