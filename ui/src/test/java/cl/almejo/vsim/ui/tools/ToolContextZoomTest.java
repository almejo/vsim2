package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;

/** The view primitives a tool gets from its context (zoom spec §4.4), through their default implementation. */
class ToolContextZoomTest {

    @Test
    void aToolZoomsTheViewOfItsOwnPaneCentredOnTheTarget() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            context.paneSize = new Dimension(400, 300);

            context.zoomTo(new ViewState(2, 10, 5));

            assertThat(context.lastZoomTarget()).isEqualTo(new ViewState(2, 10, 5));
            assertThat(context.viewport().zoom()).isEqualTo(2.0);
            assertThat(context.viewport().gridToScreen(10, 5)).isEqualTo(new Point2D.Double(200, 150));
        }
    }

    @Test
    void theZoomStaysWithinTheViewportLimits() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            context.zoomTo(new ViewState(40, 0, 0));
            assertThat(context.viewport().zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        }
    }
}
