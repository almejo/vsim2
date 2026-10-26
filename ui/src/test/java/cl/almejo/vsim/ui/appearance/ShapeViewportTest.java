package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The shape editor's zoom follows the main editor's {@link Viewport}: its range, its step and where it zooms. */
class ShapeViewportTest {

    @Test
    void theZoomStopsAtTheMainEditorsRange() {
        ShapeCanvas canvas = canvas();

        canvas.setZoom(10);
        assertThat(canvas.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);

        canvas.setZoom(0.01);
        assertThat(canvas.zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
    }

    @Test
    void controlAndTheWheelZoomByTheMainEditorsStepKeepingThePointUnderTheCursor() {
        ShapeCanvas canvas = canvas();
        Point2D before = designPoint(canvas, 200, 150);

        canvas.dispatchEvent(wheel(canvas, InputEvent.CTRL_DOWN_MASK, 200, 150, -1));

        assertThat(canvas.zoom()).isCloseTo(Viewport.ZOOM_STEP, within(1e-9));
        assertThat(designPoint(canvas, 200, 150).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(designPoint(canvas, 200, 150).getY()).isCloseTo(before.getY(), within(1e-9));
    }

    @Test
    void metaAndTheWheelZoomOutAroundTheCursorToo() {
        ShapeCanvas canvas = canvas();
        Point2D before = designPoint(canvas, 120, 90);

        canvas.dispatchEvent(wheel(canvas, InputEvent.META_DOWN_MASK, 120, 90, 2));

        assertThat(canvas.zoom()).isCloseTo(1 / (Viewport.ZOOM_STEP * Viewport.ZOOM_STEP), within(1e-9));
        assertThat(designPoint(canvas, 120, 90).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(designPoint(canvas, 120, 90).getY()).isCloseTo(before.getY(), within(1e-9));
    }

    @Test
    void thePlainWheelStillPansUpAndDownWithoutZooming() {
        ShapeCanvas canvas = canvas();
        double top = canvas.boxOnScreen().getMinY();

        canvas.dispatchEvent(wheel(canvas, 0, 200, 150, 1));

        assertThat(canvas.zoom()).isEqualTo(1);
        assertThat(canvas.boxOnScreen().getMinY()).isEqualTo(top - 24);
    }

    @Test
    void theToolbarZoomKeepsTheMiddleOfTheViewInPlace() {
        ShapeCanvas canvas = canvas();
        Point2D before = designPoint(canvas, 200, 150);

        canvas.zoomAroundCenter(canvas.zoom() * Viewport.ZOOM_STEP);
        assertThat(canvas.zoom()).isCloseTo(Viewport.ZOOM_STEP, within(1e-9));
        assertThat(designPoint(canvas, 200, 150).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(designPoint(canvas, 200, 150).getY()).isCloseTo(before.getY(), within(1e-9));

        canvas.zoomAroundCenter(10);
        assertThat(canvas.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        assertThat(designPoint(canvas, 200, 150).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(designPoint(canvas, 200, 150).getY()).isCloseTo(before.getY(), within(1e-9));
    }

    /** Where screen point ({@code x}, {@code y}) falls in the box, in grid units from its corner. */
    private static Point2D designPoint(ShapeCanvas canvas, double x, double y) {
        Rectangle2D box = canvas.boxOnScreen();
        double pixelsPerUnit = 8 * canvas.zoom();
        return new Point2D.Double((x - box.getMinX()) / pixelsPerUnit, (y - box.getMinY()) / pixelsPerUnit);
    }

    private static MouseWheelEvent wheel(ShapeCanvas canvas, int modifiers, int x, int y, int rotation) {
        return new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, modifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
    }

    private static ShapeCanvas canvas() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }
}
