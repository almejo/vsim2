package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ViewportTest {

    private final Viewport viewport = new Viewport();

    @Test
    void startsAtZoomOneWithGridOriginAtScreenOrigin() {
        assertThat(viewport.zoom()).isEqualTo(1.0);
        assertThat(viewport.pixelsPerUnit()).isEqualTo(8.0);
        assertThat(viewport.gridToScreen(2, 3)).isEqualTo(new Point2D.Double(16, 24));
    }

    @Test
    void screenToGridIsTheInverseOfGridToScreen() {
        viewport.panBy(37, -11);
        viewport.zoomAt(100, 100, 2.0);
        Point2D screen = viewport.gridToScreen(5.5, -3.25);
        Point2D grid = viewport.screenToGrid(screen.getX(), screen.getY());
        assertThat(grid.getX()).isCloseTo(5.5, within(1e-9));
        assertThat(grid.getY()).isCloseTo(-3.25, within(1e-9));
    }

    @Test
    void snapRoundsToTheNearestGridPointIncludingNegatives() {
        assertThat(viewport.snap(11, 13)).isEqualTo(new GridPoint(1, 2));
        assertThat(viewport.snap(-5, -3)).isEqualTo(new GridPoint(-1, 0));
    }

    @Test
    void zoomAtKeepsThePointUnderTheCursorFixed() {
        Point2D before = viewport.screenToGrid(240, 160);
        viewport.zoomAt(240, 160, 2.5);
        Point2D after = viewport.screenToGrid(240, 160);
        assertThat(viewport.zoom()).isEqualTo(2.5);
        assertThat(after.getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(after.getY()).isCloseTo(before.getY(), within(1e-9));
    }

    @Test
    void zoomIsClampedBetweenAQuarterAndFourTimes() {
        viewport.zoomAt(0, 0, 0.0001);
        assertThat(viewport.zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
    }

    @Test
    void zoomInAndOutStepByAQuarter() {
        viewport.zoomIn(0, 0);
        assertThat(viewport.zoom()).isCloseTo(1.25, within(1e-9));
        viewport.zoomOut(0, 0);
        viewport.zoomOut(0, 0);
        assertThat(viewport.zoom()).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void centerPutsTheMiddleOfTheBoundsInTheMiddleOfTheScreen() {
        viewport.zoomAt(0, 0, 2.0);
        viewport.center(new Bounds(10, 20, 4, 6), 800, 600);
        Point2D middle = viewport.gridToScreen(12, 23);
        assertThat(middle.getX()).isCloseTo(400, within(1e-9));
        assertThat(middle.getY()).isCloseTo(300, within(1e-9));
        assertThat(viewport.zoom()).isEqualTo(2.0);
    }

    @Test
    void documentTransformMatchesGridToScreen() {
        viewport.panBy(15, 25);
        viewport.zoomAt(50, 50, 1.5);
        Point2D viaTransform = viewport.documentTransform().transform(new Point2D.Double(3 * 8, 7 * 8), null);
        Point2D viaGrid = viewport.gridToScreen(3, 7);
        assertThat(viaTransform.getX()).isCloseTo(viaGrid.getX(), within(1e-9));
        assertThat(viaTransform.getY()).isCloseTo(viaGrid.getY(), within(1e-9));
    }

    @Test
    void zoomAtIgnoresValuesThatAreNotNumbers() {
        viewport.zoomAt(10, 10, 2.0);
        Point2D before = viewport.gridToScreen(3, 4);
        viewport.zoomAt(10, 10, Double.NaN);
        viewport.zoomAt(Double.NaN, 10, 2.0);
        viewport.zoomAt(10, Double.POSITIVE_INFINITY, 2.0);
        viewport.zoomAt(10, 10, Double.NEGATIVE_INFINITY);
        assertThat(viewport.zoom()).isEqualTo(2.0);
        assertThat(viewport.gridToScreen(3, 4)).isEqualTo(before);
    }

    @Test
    void showAtPutsTheCenterInTheMiddleOfTheScreenAndClampsTheZoom() {
        viewport.showAt(2.0, 10.5, -4.0, 800, 600);
        assertThat(viewport.zoom()).isEqualTo(2.0);
        assertThat(viewport.screenToGrid(400, 300)).isEqualTo(new Point2D.Double(10.5, -4.0));
        viewport.showAt(100.0, 0, 0, 800, 600);
        assertThat(viewport.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        viewport.showAt(0.01, 0, 0, 800, 600);
        assertThat(viewport.zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
    }
}
