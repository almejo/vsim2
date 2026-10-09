package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeAutoscrollTest {

    @Test
    void nothingScrollsAwayFromTheEdges() {
        assertThat(EdgeAutoscroll.step(400, 300, new Point(200, 150))).isEqualTo(new Point(0, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(24, 24))).isEqualTo(new Point(0, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(376, 276))).isEqualTo(new Point(0, 0));
    }

    @Test
    void theViewScrollsTowardsTheNearEdgeFasterTheDeeperThePointer() {
        assertThat(EdgeAutoscroll.step(400, 300, new Point(23, 150))).isEqualTo(new Point(2, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(0, 150))).isEqualTo(new Point(10, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(377, 150))).isEqualTo(new Point(-2, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(200, 0))).isEqualTo(new Point(0, 10));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(200, 299))).isEqualTo(new Point(0, -9));
    }

    @Test
    void pastTheEdgeTheStepIsCapped() {
        assertThat(EdgeAutoscroll.step(400, 300, new Point(-100, 150))).isEqualTo(new Point(16, 0));
        assertThat(EdgeAutoscroll.step(400, 300, new Point(500, -100))).isEqualTo(new Point(-16, 16));
    }
}
