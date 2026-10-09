package cl.almejo.vsim.ui.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;

class PanToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void draggingMovesTheViewportByTheScreenDelta() {
        PanTool tool = new PanTool();
        tool.mousePressed(context, TestToolContext.at(1, 1));
        tool.mouseDragged(context, TestToolContext.at(3, 2));
        tool.mouseDragged(context, TestToolContext.at(4, 2));
        tool.mouseReleased(context, TestToolContext.at(4, 2));
        assertThat(context.viewport.gridToScreen(0, 0)).isEqualTo(new Point2D.Double(24, 8));
    }
}
