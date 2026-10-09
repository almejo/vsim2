package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WireRoutingTest {

    private static final GridPoint START = new GridPoint(1, 1);

    @Test
    void horizontalFirstMakesAnL() {
        assertThat(WireRouting.route(START, new GridPoint(5, 4), true)).containsExactly(
                new Segment(START, new GridPoint(5, 1)), new Segment(new GridPoint(5, 1), new GridPoint(5, 4)));
    }

    @Test
    void verticalFirstMakesTheOtherL() {
        assertThat(WireRouting.route(START, new GridPoint(5, 4), false)).containsExactly(
                new Segment(START, new GridPoint(1, 4)), new Segment(new GridPoint(1, 4), new GridPoint(5, 4)));
    }

    @Test
    void straightLinesHaveOneSegmentAndNoMovementHasNone() {
        assertThat(WireRouting.route(START, new GridPoint(1, 9), true))
                .containsExactly(new Segment(START, new GridPoint(1, 9)));
        assertThat(WireRouting.route(START, START, true)).isEmpty();
    }
}
