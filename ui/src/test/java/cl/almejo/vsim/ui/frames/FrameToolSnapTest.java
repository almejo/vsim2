package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ajustar for the frame tool's corners (alignment-guides spec §4.4): guides and grid lines, never parts. */
class FrameToolSnapTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final FrameTool tool = new FrameTool();

    @BeforeEach
    void create() {
        context.snapper = new DefaultSnapper(context.preferences, Dimension::new);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void guides(Guide... guides) {
        context.openDocument().document().setGuides(List.of(guides));
    }

    private Frame onlyFrame() {
        assertThat(context.openDocument().document().frames()).hasSize(1);
        return context.openDocument().document().frames().get(0);
    }

    @Test
    void bothCornersGoToTheGuidesWithinReach() {
        guides(new Guide(Orientation.VERTICAL, 11), new Guide(Orientation.HORIZONTAL, 49));
        tool.mousePressed(context, TestToolContext.at(10, 20));
        tool.mouseDragged(context, TestToolContext.at(40, 50));
        assertThat(tool.snappedGuides()).containsExactlyInAnyOrder(new Guide(Orientation.VERTICAL, 11),
                new Guide(Orientation.HORIZONTAL, 49));
        tool.mouseReleased(context, TestToolContext.at(40, 50));

        Frame frame = onlyFrame();
        assertThat(frame.topLeft().x()).isEqualTo(11);
        assertThat(frame.topLeft().y()).isEqualTo(20);
        assertThat(frame.width()).isEqualTo(29);
        assertThat(frame.height()).isEqualTo(29);
    }

    @Test
    void aGuideIsOnlyNamedWhenTheMinimumSizeReallyReachesIt() {
        int farEdge = 10 + Frame.MINIMUM_WIDTH;
        guides(new Guide(Orientation.VERTICAL, 14));
        tool.mousePressed(context, TestToolContext.at(10, 10));
        tool.mouseDragged(context, TestToolContext.at(13, 40)); // the corner snaps to 14, the minimum pushes it on
        assertThat(farEdge).isGreaterThan(14);
        assertThat(tool.snappedGuides()).isEmpty();

        guides(new Guide(Orientation.VERTICAL, farEdge));
        tool.mouseDragged(context, TestToolContext.at(farEdge - 1, 40));
        assertThat(tool.snappedGuides()).containsExactly(new Guide(Orientation.VERTICAL, farEdge));
        tool.mouseReleased(context, TestToolContext.at(farEdge - 1, 40));
        assertThat(onlyFrame().width()).isEqualTo(Frame.MINIMUM_WIDTH);
    }

    @Test
    void aReleaseRightAfterARefreshCommitsTheRefreshedCorner() {
        tool.mousePressed(context, TestToolContext.at(10, 20));
        tool.mouseDragged(context, TestToolContext.at(40, 50));
        guides(new Guide(Orientation.VERTICAL, 41));
        assertThat(tool.refreshSnap(context)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(40, 50));
        assertThat(onlyFrame().width()).isEqualTo(31);
    }

    @Test
    void withAjustarOffTheFrameIsTheDraggedRectangle() {
        context.preferences.setSnapToAlignments(false);
        guides(new Guide(Orientation.VERTICAL, 11), new Guide(Orientation.HORIZONTAL, 49));
        context.drag(tool, 10, 20, 40, 50);
        Frame frame = onlyFrame();
        assertThat(frame.topLeft().x()).isEqualTo(10);
        assertThat(frame.width()).isEqualTo(30);
        assertThat(frame.height()).isEqualTo(30);
        assertThat(tool.snappedGuides()).isEmpty();
    }

    @Test
    void gridLinesPullTheCornersWhenOn() {
        context.preferences.setSnapToGridLines(true);
        context.drag(tool, 11, 21, 41, 51);
        Frame frame = onlyFrame();
        assertThat(frame.topLeft().x() % 2).isZero();
        assertThat(frame.topLeft().y() % 2).isZero();
        assertThat(frame.width() % 2).isZero();
        assertThat(frame.height() % 2).isZero();
    }
}
