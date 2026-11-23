package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusLabelPlacementTest {

    private static final double WIDTH = 40;
    private static final double ASCENT = 10;
    private static final double DESCENT = 3;
    private static final double GAP = 4;

    @Test
    void aTagOverNoPieceStaysWhereItWas() {
        Point2D spot = BusLabelPlacement.clearOf(new Point2D.Double(100, 50), WIDTH, ASCENT, DESCENT, GAP,
                List.of(new Rectangle2D.Double(0, 0, 20, 100)));
        assertThat(spot).isEqualTo(new Point2D.Double(100, 50));
    }

    @Test
    void aTagCentredOnAShortWireNextToABodyMovesOutsideItToTheSideOfTheWire() {
        // the body ends at x=100 and the tag, centred on a wire that runs from 100 to 120, starts at 90
        Rectangle2D body = new Rectangle2D.Double(0, 0, 100, 100);
        Point2D spot = BusLabelPlacement.clearOf(new Point2D.Double(90, 50), WIDTH, ASCENT, DESCENT, GAP,
                List.of(body));
        assertThat(spot.getX()).isEqualTo(100 + GAP);
        assertThat(spot.getY()).isEqualTo(50);
    }

    @Test
    void aTagOverTheLeftEdgeOfABodyMovesToItsLeft() {
        Rectangle2D body = new Rectangle2D.Double(100, 0, 100, 100);
        Point2D spot = BusLabelPlacement.clearOf(new Point2D.Double(90, 50), WIDTH, ASCENT, DESCENT, GAP,
                List.of(body));
        assertThat(spot.getX()).isEqualTo(100 - GAP - WIDTH);
    }
}
