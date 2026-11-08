package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.snap.PointResolution;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The corners, segments and junction points of a continuous wire path (decided point 1 of 2026-11-08). */
class ContinuousWirePathTest {

    private final TestToolContext context = TestToolContext.withNewDocument();

    @AfterEach
    void close() {
        context.close();
    }

    private static ContinuousWirePath pathFrom(int x, int y) {
        return new ContinuousWirePath(new PointResolution(new GridPoint(x, y), PointResolution.Reason.GRID), 1);
    }

    @Test
    void theCornersStartAtTheStartAndGrowWithEachFixedPoint() {
        ContinuousWirePath path = pathFrom(0, 0);
        assertThat(path.addCorner(context, new GridPoint(4, 0))).isFalse();
        assertThat(path.corners()).containsExactly(new GridPoint(0, 0), new GridPoint(4, 0));
        assertThat(path.dropLastCorner()).isTrue();
        assertThat(path.corners()).containsExactly(new GridPoint(0, 0));
        assertThat(path.dropLastCorner()).isFalse();
    }

    @Test
    void aCornerOnTheLastCornerIsIgnored() {
        ContinuousWirePath path = pathFrom(0, 0);
        path.addCorner(context, new GridPoint(4, 0));
        path.addCorner(context, new GridPoint(4, 0));
        assertThat(path.corners()).hasSize(2);
    }

    @Test
    void consecutiveCollinearSegmentsMergeAndAPathWithNoLengthHasNone() {
        ContinuousWirePath path = pathFrom(0, 0);
        assertThat(path.segments()).isEmpty();
        assertThat(path.commit(context)).isFalse();
        path.addCorner(context, new GridPoint(3, 0));
        path.addCorner(context, new GridPoint(7, 0));
        assertThat(path.segments()).containsExactly(new Segment(new GridPoint(0, 0), new GridPoint(7, 0)));
    }

    @Test
    void crossingAWireInteriorIsNoJunction() {
        addWire(context.openDocument(), 5, -5, 5, 5);
        ContinuousWirePath path = pathFrom(0, 0);
        path.previewTo(context, new GridPoint(10, 0));
        assertThat(path.junctionPoints(context)).isEmpty();
    }

    @Test
    void aWireEndLyingOnTheWayIsAJunction() {
        addWire(context.openDocument(), 5, 0, 5, 5);
        ContinuousWirePath path = pathFrom(0, 0);
        path.previewTo(context, new GridPoint(10, 0));
        assertThat(path.junctionPoints(context)).containsExactly(new GridPoint(5, 0));
    }

    @Test
    void thePointerOverAWireShowsTheJunctionItWillMake() {
        addWire(context.openDocument(), 10, -5, 10, 5);
        ContinuousWirePath path = pathFrom(0, 0);
        path.previewTo(context, new GridPoint(10, 0));
        assertThat(path.junctionPoints(context)).containsExactly(new GridPoint(10, 0));
    }

    @Test
    void aPinOnTheWayAndAPinAtTheStartAreJunctions() {
        Component buffer = place(context.openDocument(), StandardComponentTypes.BUFFER, 0, 0,
                Map.of(StandardComponentTypes.NEGATE, true));
        GridPoint input = context.openDocument().registry().typeOf(buffer).pinLocation(buffer, 0);
        GridPoint output = context.openDocument().registry().typeOf(buffer).pinLocation(buffer, 1);
        ContinuousWirePath fromTheInput = new ContinuousWirePath(
                new PointResolution(input, PointResolution.Reason.GRID), 1);
        fromTheInput.previewTo(context, new GridPoint(input.x() - 6, input.y()));
        assertThat(fromTheInput.junctionPoints(context)).containsExactly(input);

        ContinuousWirePath overTheOutput = pathFrom(output.x() - 1, output.y() + 4);
        overTheOutput.addCorner(context, new GridPoint(output.x() - 1, output.y()));
        overTheOutput.previewTo(context, new GridPoint(output.x() + 6, output.y()));
        assertThat(overTheOutput.junctionPoints(context)).contains(output);
    }
}
