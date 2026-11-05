package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Where a component tab's view starts when entering animates (spec §4.4): the body where the instance was. */
class EnteringViewTest {

    /** Screen offset (in pixels per grid unit) of a grid point from the pane's centre. */
    private static double offset(double gridCoordinate, double center, double zoom) {
        return (gridCoordinate - center) * zoom;
    }

    @Test
    void theBodyStartsOnScreenWhereAndAsBigAsTheInstanceWas() {
        ViewState parent = new ViewState(2, 50, 40);
        Bounds instance = new Bounds(60, 30, 4, 4); // centre (62, 32)
        Bounds body = new Bounds(0, 0, 40, 20);     // centre (20, 10)

        ViewState start = EnteringView.start(parent, instance, body);

        assertThat(start.zoom()).isCloseTo(2 * 4.0 / 40, within(1e-9));
        assertThat(offset(20, start.centerX(), start.zoom())).isCloseTo(offset(62, 50, 2), within(1e-9));
        assertThat(offset(10, start.centerY(), start.zoom())).isCloseTo(offset(32, 40, 2), within(1e-9));
    }

    @Test
    void anEmptyBodyStartsAtTheParentsZoomOnTheInstance() {
        ViewState parent = new ViewState(1.5, 0, 0);
        ViewState start = EnteringView.start(parent, new Bounds(10, 10, 4, 4), new Bounds(0, 0, 0, 0));
        assertThat(start.zoom()).isEqualTo(1.5);
    }
}
