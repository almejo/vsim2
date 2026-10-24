package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ViewTravelTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private static List<ViewState> frames(ViewTravel travel) {
        List<ViewState> frames = new ArrayList<>();
        for (long elapsed = 0; elapsed < travel.durationMilliseconds(); elapsed += 16) {
            frames.add(travel.at(elapsed));
        }
        frames.add(travel.at(travel.durationMilliseconds()));
        return frames;
    }

    private static double distance(ViewState first, ViewState second) {
        return Math.hypot(first.centerX() - second.centerX(), first.centerY() - second.centerY());
    }

    @Test
    void aNearTargetIsAnEasedPanOfThreeHundredMillisecondsAtTheSameZoom() {
        ViewState start = new ViewState(1, 0, 0);
        ViewState target = new ViewState(1, 30, 10);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);

        assertThat(travel.flies()).isFalse();
        assertThat(travel.durationMilliseconds()).isEqualTo(300);
        assertThat(travel.at(0)).isEqualTo(start);
        assertThat(travel.at(300)).isEqualTo(target);
        assertThat(travel.at(1_000)).isEqualTo(target);
        // Ease-in-out: slow at both ends, half way at half time.
        assertThat(travel.at(150).centerX()).isCloseTo(15, within(1e-9));
        assertThat(travel.at(30).centerX()).isLessThan(30 * 0.1 / 2);
        assertThat(travel.at(270).centerX()).isGreaterThan(30 - 30 * 0.1 / 2);
        assertThat(frames(travel)).allSatisfy(frame -> assertThat(frame.zoom()).isEqualTo(1.0));
    }

    @Test
    void theViewNeverOvershootsNorComesBack() {
        for (ViewTravel travel : List.of(
                ViewTravel.between(new ViewState(1, 0, 0), new ViewState(1, 60, -40), WIDTH, HEIGHT, false),
                ViewTravel.between(new ViewState(1, 0, 0), new ViewState(1, 600, -400), WIDTH, HEIGHT, false),
                ViewTravel.between(new ViewState(1, 0, 0), new ViewState(1, 60, -40), WIDTH, HEIGHT, true))) {
            ViewState target = travel.target();
            double previous = Double.MAX_VALUE;
            for (ViewState frame : frames(travel)) {
                double remaining = distance(frame, target);
                assertThat(remaining).isLessThanOrEqualTo(previous + 1e-9);
                previous = remaining;
            }
            assertThat(previous).isZero();
        }
    }

    @Test
    void aFarTargetFliesOutUntilBothPlacesShowAndBackToTheUsersZoom() {
        ViewState start = new ViewState(1, 0, 0);
        ViewState target = new ViewState(1, 200, 0);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);

        assertThat(travel.flies()).isTrue();
        assertThat(travel.durationMilliseconds()).isBetween(500L, 700L);
        List<ViewState> frames = frames(travel);
        assertThat(frames.get(frames.size() - 1)).isEqualTo(target);

        ViewState middle = travel.at(travel.durationMilliseconds() / 2);
        double halfWidth = WIDTH / (8.0 * middle.zoom()) / 2;
        assertThat(middle.zoom()).isLessThan(0.5);
        assertThat(Math.abs(middle.centerX() - start.centerX())).isLessThanOrEqualTo(halfWidth);
        assertThat(Math.abs(target.centerX() - middle.centerX())).isLessThanOrEqualTo(halfWidth);

        // Out then in, once: the zoom falls to a single lowest point and rises again, without wobbling.
        int lowest = 0;
        for (int index = 1; index < frames.size(); index++) {
            if (frames.get(index).zoom() < frames.get(lowest).zoom()) {
                lowest = index;
            }
        }
        for (int index = 1; index <= lowest; index++) {
            assertThat(frames.get(index).zoom()).isLessThanOrEqualTo(frames.get(index - 1).zoom() + 1e-12);
        }
        for (int index = lowest + 1; index < frames.size(); index++) {
            assertThat(frames.get(index).zoom()).isGreaterThanOrEqualTo(frames.get(index - 1).zoom() - 1e-12);
        }
    }

    @Test
    void theFlightLandsOnTheTargetZoomWhenItDiffers() {
        ViewTravel travel = ViewTravel.between(new ViewState(2, 0, 0), new ViewState(0.5, 300, 300), WIDTH, HEIGHT,
                false);
        assertThat(travel.flies()).isTrue();
        assertThat(travel.at(travel.durationMilliseconds())).isEqualTo(new ViewState(0.5, 300, 300));
    }

    @Test
    void aNearTravelChangingTheZoomGoesThereSmoothly() {
        ViewTravel travel = ViewTravel.between(new ViewState(2, 0, 0), new ViewState(1, 10, 0), WIDTH, HEIGHT, false);
        assertThat(travel.flies()).isFalse();
        double previous = 2;
        for (ViewState frame : frames(travel)) {
            assertThat(frame.zoom()).isBetween(1.0, previous);
            previous = frame.zoom();
        }
        assertThat(previous).isEqualTo(1.0);
    }

    @Test
    void aTravelThatTakesOverAMovingViewStartsAtSpeed() {
        ViewState start = new ViewState(1, 0, 0);
        ViewState target = new ViewState(1, 40, 0);
        ViewTravel fromRest = ViewTravel.between(start, target, WIDTH, HEIGHT, false);
        ViewTravel takingOver = ViewTravel.between(start, target, WIDTH, HEIGHT, true);
        // Ease-out: the first frame already moves as fast as the eased pan does at its fastest.
        assertThat(takingOver.at(16).centerX()).isGreaterThan(fromRest.at(16).centerX() * 10);
        assertThat(takingOver.at(16).centerX()).isLessThan(40 * 0.2);
        assertThat(takingOver.at(takingOver.durationMilliseconds())).isEqualTo(target);
    }

    /** Where grid point ({@code gridX}, {@code gridY}) shows in {@code view}, in pixels from the pane's middle. */
    private static double[] fromTheMiddle(ViewState view, double gridX, double gridY) {
        double pixelsPerUnit = 8 * view.zoom();
        return new double[]{(gridX - view.centerX()) * pixelsPerUnit, (gridY - view.centerY()) * pixelsPerUnit};
    }

    @Test
    void zoomingIntoAPlaceOnScreenKeepsOnePointStillSoTheViewDoesNotSlideSideways() {
        // The user framed the part 200 px right and 100 px below the middle, a quarter of the pane wide: zoom ×4.
        ViewState start = new ViewState(1, 0, 0);
        ViewState target = new ViewState(4, 200 / 8.0, 100 / 8.0);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);

        assertThat(travel.flies()).isFalse();
        assertThat(travel.durationMilliseconds()).isEqualTo(ViewTravel.PAN_MILLISECONDS);
        // The still point: A = (c1·k1 − c0·k0) / (k1 − k0), per axis.
        double anchorX = (target.centerX() * 32 - start.centerX() * 8) / (32 - 8);
        double anchorY = (target.centerY() * 32 - start.centerY() * 8) / (32 - 8);
        double[] atStart = fromTheMiddle(start, anchorX, anchorY);
        for (long elapsed : new long[]{0, 75, 150, 225, 300}) {
            double[] now = fromTheMiddle(travel.at(elapsed), anchorX, anchorY);
            assertThat(now[0]).as("x at " + elapsed + " ms").isCloseTo(atStart[0], within(1e-6));
            assertThat(now[1]).as("y at " + elapsed + " ms").isCloseTo(atStart[1], within(1e-6));
        }
        assertThat(travel.at(300)).isEqualTo(target);
    }

    @Test
    void zoomingOutOfAPlaceKeepsItsPointStillToo() {
        ViewState start = new ViewState(2, 10, 5);
        ViewState target = new ViewState(1, 20, 5);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);
        double anchorX = (target.centerX() * 8 - start.centerX() * 16) / (8 - 16);
        double[] atStart = fromTheMiddle(start, anchorX, 5);
        double[] halfWay = fromTheMiddle(travel.at(150), anchorX, 5);
        assertThat(halfWay[0]).isCloseTo(atStart[0], within(1e-6));
        assertThat(travel.at(300)).isEqualTo(target);
    }

    @Test
    void theSameCentreAtAnotherZoomIsAPureZoomAboutTheMiddle() {
        ViewState start = new ViewState(2.5, 12, -7);
        ViewState target = new ViewState(1, 12, -7);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);
        double previous = start.zoom();
        for (ViewState frame : frames(travel)) {
            assertThat(frame.centerX()).isCloseTo(12, within(1e-9));
            assertThat(frame.centerY()).isCloseTo(-7, within(1e-9));
            assertThat(frame.zoom()).isBetween(1.0, previous);
            previous = frame.zoom();
        }
        assertThat(travel.durationMilliseconds()).isEqualTo(ViewTravel.PAN_MILLISECONDS);
    }

    @Test
    void aStillPointOffThePaneFallsBackToThePan() {
        // ×1.25 toward a place 300 px right: the still point would be 1,500 px right, off the pane.
        ViewState start = new ViewState(1, 0, 0);
        ViewState target = new ViewState(1.25, 300 / 8.0, 0);
        ViewTravel travel = ViewTravel.between(start, target, WIDTH, HEIGHT, false);
        // The straight pan: the centre moves evenly with the eased time.
        assertThat(travel.at(150).centerX()).isCloseTo(300 / 8.0 / 2, within(1e-9));
        assertThat(travel.at(300)).isEqualTo(target);
    }

    @Test
    void goingNowhereTakesNoTime() {
        ViewState here = new ViewState(1, 5, 5);
        ViewTravel travel = ViewTravel.between(here, here, WIDTH, HEIGHT, false);
        assertThat(travel.durationMilliseconds()).isZero();
        assertThat(travel.at(0)).isEqualTo(here);
    }
}
