package cl.almejo.vsim.ui.ai.present;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the callout goes beside its change, inside the pane, at each edge of the pane (spec §5, Review Focus 4). */
class StepCalloutPlacementTest {

    private static final int PANE_WIDTH = 400;
    private static final int PANE_HEIGHT = 300;
    private static final Dimension SIZE = new Dimension(200, 80);

    private static StepCallout.Placement place(double x, double y, double width, double height) {
        return StepCallout.place(new Rectangle2D.Double(x, y, width, height), SIZE, PANE_WIDTH, PANE_HEIGHT);
    }

    private static void assertInsideThePane(StepCallout.Placement placement) {
        Rectangle box = placement.box();
        assertThat(box.x).isGreaterThanOrEqualTo(StepCallout.PANE_PADDING);
        assertThat(box.y).isGreaterThanOrEqualTo(StepCallout.PANE_PADDING);
        assertThat(box.x + box.width).isLessThanOrEqualTo(PANE_WIDTH - StepCallout.PANE_PADDING);
        assertThat(box.y + box.height).isLessThanOrEqualTo(PANE_HEIGHT - StepCallout.PANE_PADDING);
        assertThat(box.getSize()).isEqualTo(SIZE);
    }

    @Test
    void besideTheChangeOnItsRightWhenThereIsRoom() {
        StepCallout.Placement placement = place(40, 140, 20, 20);

        assertThat(placement.side()).isEqualTo(StepCallout.Side.RIGHT);
        assertThat(placement.box().x).isEqualTo(60 + StepCallout.GAP);
        assertThat(placement.dot().getX()).isEqualTo(60);
        assertThat(placement.dot().getY()).isEqualTo(150);
        assertThat(placement.lineStart().getX()).isEqualTo(placement.box().x);
        assertInsideThePane(placement);
    }

    @Test
    void nearTheRightEdgeItFlipsToTheLeft() {
        StepCallout.Placement placement = place(350, 140, 20, 20);

        assertThat(placement.side()).isEqualTo(StepCallout.Side.LEFT);
        assertThat(placement.box().x + placement.box().width).isEqualTo(350 - StepCallout.GAP);
        assertThat(placement.dot().getX()).isEqualTo(350);
        assertInsideThePane(placement);
    }

    @Test
    void nearTheTopEdgeItStaysBesideButBelowThePadding() {
        StepCallout.Placement placement = place(40, 0, 20, 10);

        assertThat(placement.side()).isEqualTo(StepCallout.Side.RIGHT);
        assertThat(placement.box().y).isEqualTo(StepCallout.PANE_PADDING);
        assertInsideThePane(placement);
    }

    @Test
    void nearTheBottomEdgeItStaysBesideButAboveThePadding() {
        StepCallout.Placement placement = place(40, 285, 20, 15);

        assertThat(placement.side()).isEqualTo(StepCallout.Side.RIGHT);
        assertThat(placement.box().y + placement.box().height).isEqualTo(PANE_HEIGHT - StepCallout.PANE_PADDING);
        assertInsideThePane(placement);
    }

    @Test
    void aChangeAsWideAsThePaneGetsItBelowOrAbove() {
        StepCallout.Placement below = place(0, 40, 400, 20);
        StepCallout.Placement above = place(0, 250, 400, 30);

        assertThat(below.side()).isEqualTo(StepCallout.Side.BELOW);
        assertThat(below.box().y).isEqualTo(60 + StepCallout.GAP);
        assertThat(below.dot().getY()).isEqualTo(60);
        assertInsideThePane(below);
        assertThat(above.side()).isEqualTo(StepCallout.Side.ABOVE);
        assertThat(above.box().y + above.box().height).isEqualTo(250 - StepCallout.GAP);
        assertInsideThePane(above);
    }

    @Test
    void aChangeThatFillsThePaneGetsItInACornerPointingAtTheChange() {
        StepCallout.Placement placement = place(-100, -100, 600, 500);

        assertThat(placement.side()).isEqualTo(StepCallout.Side.CORNER);
        assertInsideThePane(placement);
    }

    @Test
    void theDotStaysInsideThePaneWhenTheChangeStandsOutOfIt() {
        StepCallout.Placement placement = place(-50, 100, 80, 400);

        assertThat(placement.dot().getX()).isBetween(0.0, (double) PANE_WIDTH);
        assertThat(placement.dot().getY()).isBetween(0.0, (double) PANE_HEIGHT);
        assertInsideThePane(placement);
    }
}
