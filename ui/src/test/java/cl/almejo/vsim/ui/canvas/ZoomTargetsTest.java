package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ZoomTargetsTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double EXACT = 1e-9;

    private static Viewport viewportShowing(ViewState state) {
        Viewport viewport = new Viewport();
        viewport.showAt(state.zoom(), state.centerX(), state.centerY(), WIDTH, HEIGHT);
        return viewport;
    }

    /** Where {@code state} would put grid point ({@code gridX}, {@code gridY}) on a pane of the test's size. */
    private static Point2D onScreen(ViewState state, double gridX, double gridY) {
        return viewportShowing(state).gridToScreen(gridX, gridY);
    }

    @Test
    void aWideRectangleFillsThePaneFromSideToSideCentredWhereItWas() {
        Viewport viewport = viewportShowing(new ViewState(1, 0, 0));
        Rectangle2D framed = new Rectangle2D.Double(100, 250, 200, 50);

        ViewState target = ZoomTargets.region(framed, viewport, WIDTH, HEIGHT);

        assertThat(target.zoom()).isCloseTo(4.0, within(EXACT)); // 800 / 200, and 600 / 50 = 12 would overflow
        Point2D framedCentre = viewport.screenToGrid(200, 275);
        assertThat(target.centerX()).isCloseTo(framedCentre.getX(), within(EXACT));
        assertThat(target.centerY()).isCloseTo(framedCentre.getY(), within(EXACT));
        Point2D left = viewport.screenToGrid(100, 250);
        assertThat(onScreen(target, left.getX(), framedCentre.getY()).getX()).isCloseTo(0, within(EXACT));
    }

    @Test
    void aTallRectangleFitsByItsHeightWithItsWidthInside() {
        Viewport viewport = viewportShowing(new ViewState(0.5, 10, 10));
        Rectangle2D framed = new Rectangle2D.Double(300, 100, 100, 300);

        ViewState target = ZoomTargets.region(framed, viewport, WIDTH, HEIGHT);

        assertThat(target.zoom()).isCloseTo(0.5 * 600 / 300.0, within(EXACT));
        Point2D top = viewport.screenToGrid(350, 100);
        Point2D bottom = viewport.screenToGrid(350, 400);
        assertThat(onScreen(target, top.getX(), top.getY()).getY()).isCloseTo(0, within(EXACT));
        assertThat(onScreen(target, bottom.getX(), bottom.getY()).getY()).isCloseTo(HEIGHT, within(EXACT));
        Point2D leftEdge = viewport.screenToGrid(300, 250);
        assertThat(onScreen(target, leftEdge.getX(), leftEdge.getY()).getX()).isBetween(0.0, WIDTH / 2.0);
    }

    @Test
    void aThinRectangleFitsByItsLongSide() {
        Viewport viewport = viewportShowing(new ViewState(0.25, 0, 0));
        ViewState target = ZoomTargets.region(new Rectangle2D.Double(100, 300, 400, 6), viewport, WIDTH, HEIGHT);
        assertThat(target.zoom()).isCloseTo(0.25 * WIDTH / 400.0, within(EXACT));
    }

    @Test
    void aTinyRectangleStopsAtTheLargestZoomStillCentredOnIt() {
        Viewport viewport = viewportShowing(new ViewState(1, 0, 0));
        ViewState target = ZoomTargets.region(new Rectangle2D.Double(500, 100, 8, 6), viewport, WIDTH, HEIGHT);
        assertThat(target.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        Point2D framedCentre = viewport.screenToGrid(504, 103);
        assertThat(target.centerX()).isCloseTo(framedCentre.getX(), within(EXACT));
        assertThat(target.centerY()).isCloseTo(framedCentre.getY(), within(EXACT));
    }

    @Test
    void aRectangleLargerThanThePaneZoomsOutButNotPastTheSmallestZoom() {
        Viewport viewport = viewportShowing(new ViewState(0.3, 0, 0));
        ViewState target = ZoomTargets.region(new Rectangle2D.Double(-1600, -1200, 3200, 2400), viewport, WIDTH,
                HEIGHT);
        assertThat(target.zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
    }

    @Test
    void fittingLeavesTheGivenMarginOnTheTighterSide() {
        ViewState target = ZoomTargets.fitting(new Bounds(10, 20, 50, 10), WIDTH, HEIGHT, 0.9, 1);

        assertThat(target.zoom()).isCloseTo(WIDTH * 0.9 / (50 * 8.0), within(EXACT));
        assertThat(target.centerX()).isEqualTo(35);
        assertThat(target.centerY()).isEqualTo(25);
    }

    @Test
    void fittingAFlatCircuitUsesOnlyItsOtherSide() {
        assertThat(ZoomTargets.fitting(new Bounds(0, 0, 0, 30), WIDTH, HEIGHT, 0.9, 1).zoom())
                .isCloseTo(HEIGHT * 0.9 / (30 * 8.0), within(EXACT));
        assertThat(ZoomTargets.fitting(new Bounds(0, 0, 60, 0), WIDTH, HEIGHT, 0.9, 1).zoom())
                .isCloseTo(WIDTH * 0.9 / (60 * 8.0), within(EXACT));
    }

    @Test
    void fittingALonePointOnlyCentresItAtTheCurrentZoom() {
        assertThat(ZoomTargets.fitting(new Bounds(7, 9, 0, 0), WIDTH, HEIGHT, 0.9, 1.5))
                .isEqualTo(new ViewState(1.5, 7, 9));
    }

    @Test
    void fittingIsClampedToTheZoomLimits() {
        assertThat(ZoomTargets.fitting(new Bounds(0, 0, 10_000, 10_000), WIDTH, HEIGHT, 0.9, 1).zoom())
                .isEqualTo(Viewport.MINIMUM_ZOOM);
        assertThat(ZoomTargets.fitting(new Bounds(0, 0, 2, 1), WIDTH, HEIGHT, 0.9, 1).zoom())
                .isEqualTo(Viewport.MAXIMUM_ZOOM);
    }

    @Test
    void atZoomKeepsTheCentreAndClampsTheZoom() {
        ViewState current = new ViewState(2, 13, -4);
        assertThat(ZoomTargets.atZoom(current, 1)).isEqualTo(new ViewState(1, 13, -4));
        assertThat(ZoomTargets.atZoom(current, 10)).isEqualTo(new ViewState(Viewport.MAXIMUM_ZOOM, 13, -4));
        assertThat(ZoomTargets.atZoom(current, 0.01)).isEqualTo(new ViewState(Viewport.MINIMUM_ZOOM, 13, -4));
    }
}
