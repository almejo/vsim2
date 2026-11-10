package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

/** Where a bubble goes beside its target: its own side first, then the opposite, then the others, else centred. */
class BubblePlacementTest {

    private static final Rectangle FRAME = new Rectangle(0, 30, 1200, 770); // under a 30 px menu bar
    private static final Dimension BUBBLE = new Dimension(260, 150);
    private static final int GAP = 12;
    private static final int MARGIN = 8;

    private static BubblePlacement.Placement place(Rectangle target, Side preferred, Rectangle area) {
        return BubblePlacement.place(target, BUBBLE, area, preferred, GAP, MARGIN);
    }

    @Test
    void aToolbarButtonGetsItsBubbleBelowCentredOnIt() {
        Rectangle button = new Rectangle(300, 40, 30, 30);

        BubblePlacement.Placement placement = place(button, Side.BELOW, FRAME);

        assertThat(placement.side()).contains(Side.BELOW);
        assertThat(placement.bounds().y).isEqualTo(button.y + button.height + GAP);
        assertThat(placement.bounds().getCenterX()).isEqualTo(button.getCenterX());
    }

    @Test
    void nearTheRightEdgeTheBubbleMovesInsideAndStaysBelow() {
        Rectangle button = new Rectangle(1170, 40, 26, 30);

        BubblePlacement.Placement placement = place(button, Side.BELOW, FRAME);

        assertThat(placement.side()).contains(Side.BELOW);
        assertThat(placement.bounds().x + placement.bounds().width).isEqualTo(FRAME.x + FRAME.width - MARGIN);
    }

    @Test
    void theStatusBarGetsItsBubbleAbove() {
        Rectangle statusBar = new Rectangle(0, 776, 1200, 24);

        BubblePlacement.Placement placement = place(statusBar, Side.ABOVE, FRAME);

        assertThat(placement.side()).contains(Side.ABOVE);
        assertThat(placement.bounds().y + placement.bounds().height).isEqualTo(statusBar.y - GAP);
    }

    @Test
    void theSideStripsGetTheirBubbleOnTheirInnerSide() {
        Rectangle leftStrip = new Rectangle(0, 80, 200, 680);
        Rectangle rightStrip = new Rectangle(1170, 80, 30, 680);

        assertThat(place(leftStrip, Side.RIGHT, FRAME).side()).contains(Side.RIGHT);
        assertThat(place(leftStrip, Side.RIGHT, FRAME).bounds().x).isEqualTo(200 + GAP);
        assertThat(place(rightStrip, Side.LEFT, FRAME).side()).contains(Side.LEFT);
        assertThat(place(rightStrip, Side.LEFT, FRAME).bounds().x).isEqualTo(1170 - GAP - BUBBLE.width);
    }

    @Test
    void whenItsSideDoesNotFitTheBubbleFlipsToTheOpposite() {
        Rectangle lowButton = new Rectangle(300, 700, 30, 30); // no room below in the 800 px frame

        assertThat(place(lowButton, Side.BELOW, FRAME).side()).contains(Side.ABOVE);
    }

    @Test
    void whenNeitherFitsItTriesTheOthers() {
        Rectangle tall = new Rectangle(0, 40, 300, 760); // the whole height: neither above nor below

        assertThat(place(tall, Side.BELOW, FRAME).side()).contains(Side.RIGHT);
    }

    @Test
    void whenNothingFitsItIsCentredWithoutArrow() {
        Rectangle tiny = new Rectangle(0, 30, 300, 200);
        Rectangle everything = new Rectangle(0, 30, 300, 200);

        BubblePlacement.Placement placement = place(everything, Side.BELOW, tiny);

        assertThat(placement.side()).isEmpty();
        assertThat(tiny.contains(placement.bounds())).isTrue();
        assertThat(placement.bounds().getCenterX()).isEqualTo(tiny.getCenterX());
    }

    @Test
    void aBubbleTallerThanTheFrameStartsAtItsTop() {
        Rectangle tiny = new Rectangle(0, 30, 300, 100);

        BubblePlacement.Placement placement = place(tiny, Side.BELOW, tiny);

        assertThat(placement.bounds().y).isEqualTo(tiny.y + MARGIN);
        assertThat(placement.bounds().width).isLessThanOrEqualTo(tiny.width - 2 * MARGIN);
    }

    @Test
    void theArrowPointsAtTheTargetsCentreButStaysOnTheBody() {
        Rectangle body = new Rectangle(100, 100, 260, 150);

        assertThat(BubblePlacement.arrowOffset(body, new Rectangle(200, 40, 20, 20), Side.BELOW, 10))
                .isEqualTo(110);
        assertThat(BubblePlacement.arrowOffset(body, new Rectangle(1000, 40, 20, 20), Side.BELOW, 10))
                .isEqualTo(250);
        assertThat(BubblePlacement.arrowOffset(body, new Rectangle(0, 0, 10, 10), Side.RIGHT, 10))
                .isEqualTo(10);
    }
}
