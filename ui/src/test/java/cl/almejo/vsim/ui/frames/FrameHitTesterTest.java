package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FrameHitTesterTest {

    /** 8 screen pixels per grid unit (100 %): the grab tolerance is half a unit. */
    private static final double FULL_SIZE = 8;

    private final CircuitDocument document = new CircuitDocument();

    private Frame frame(int x, int y, int width, int height, boolean locked) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, "", FrameTint.NONE,
                locked);
        document.add(frame);
        return frame;
    }

    private Optional<FramePart> partAt(double x, double y, double pixelsPerGrid) {
        return FrameHitTester.hit(document, new Point2D.Double(x, y), pixelsPerGrid).map(FrameHit::part);
    }

    @Test
    void anUnlockedFrameHasATitleBarEdgesAndCornersButNoInterior() {
        frame(0, 0, 20, 12, false);
        assertThat(partAt(10, 1, FULL_SIZE)).contains(FramePart.TITLE_BAR);
        assertThat(partAt(10, -0.4, FULL_SIZE)).contains(FramePart.EDGE_NORTH);
        assertThat(partAt(10, 12.4, FULL_SIZE)).contains(FramePart.EDGE_SOUTH);
        assertThat(partAt(-0.3, 6, FULL_SIZE)).contains(FramePart.EDGE_WEST);
        assertThat(partAt(20.3, 6, FULL_SIZE)).contains(FramePart.EDGE_EAST);
        assertThat(partAt(0, 0, FULL_SIZE)).contains(FramePart.CORNER_NORTH_WEST);
        assertThat(partAt(20, 0, FULL_SIZE)).contains(FramePart.CORNER_NORTH_EAST);
        assertThat(partAt(0, 12, FULL_SIZE)).contains(FramePart.CORNER_SOUTH_WEST);
        assertThat(partAt(20, 12, FULL_SIZE)).contains(FramePart.CORNER_SOUTH_EAST);
        assertThat(partAt(10, 6, FULL_SIZE)).isEmpty();
        assertThat(partAt(10, 13, FULL_SIZE)).isEmpty();
    }

    @Test
    void aLockedFrameHasOnlyItsTitleBar() {
        frame(0, 0, 20, 12, true);
        assertThat(partAt(10, 1, FULL_SIZE)).contains(FramePart.TITLE_BAR);
        assertThat(partAt(10, -0.4, FULL_SIZE)).contains(FramePart.TITLE_BAR);
        assertThat(partAt(0, 0, FULL_SIZE)).contains(FramePart.TITLE_BAR);
        assertThat(partAt(10, 12, FULL_SIZE)).isEmpty();
        assertThat(partAt(20, 6, FULL_SIZE)).isEmpty();
        assertThat(partAt(10, 6, FULL_SIZE)).isEmpty();
    }

    @Test
    void theToleranceIsInScreenPixelsButNeverSwallowsTheTitleBar() {
        frame(0, 0, 20, 12, false);
        assertThat(partAt(-0.9, 6, FULL_SIZE)).isEmpty();
        assertThat(partAt(-0.9, 6, 2)).contains(FramePart.EDGE_WEST); // zoomed out to 25 %: two units around
        assertThat(partAt(10, 1, 2)).contains(FramePart.TITLE_BAR);
        assertThat(partAt(10, 6, 2)).isEmpty();
    }

    @Test
    void theSmallestFrameWinsWhereFramesOverlap() {
        frame(0, 0, 40, 30, false);
        Frame inner = frame(5, 5, 20, 15, false);
        Optional<FrameHit> hit = FrameHitTester.hit(document, new Point2D.Double(10, 6), FULL_SIZE);
        assertThat(hit).contains(new FrameHit(inner, FramePart.TITLE_BAR));
        assertThat(FrameHitTester.frameUnder(document, new Point2D.Double(10, 10), FULL_SIZE)).contains(inner);
        assertThat(FrameHitTester.frameUnder(document, new Point2D.Double(30, 25), FULL_SIZE))
                .map(Frame::width).contains(40);
        assertThat(FrameHitTester.frameUnder(document, new Point2D.Double(50, 25), FULL_SIZE)).isEmpty();
    }
}
