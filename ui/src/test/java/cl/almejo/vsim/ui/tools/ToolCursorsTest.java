package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCursorsTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ComponentTypeRegistry registry = context.openDocument().registry();

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void theWireToolShowsTheCrosshair() {
        assertThat(new WireTool().cursor().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void thePanToolShowsMove() {
        assertThat(new PanTool().cursor().getType()).isEqualTo(Cursor.MOVE_CURSOR);
    }

    @Test
    void placingAComponentShowsTheCrosshair() {
        PlaceComponentTool tool = new PlaceComponentTool(registry.require(StandardComponentTypes.NOT));
        assertThat(tool.cursor().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void aFloatingPasteShowsMoveOverTheFragmentAndTheArrowElsewhere() {
        CircuitFragment gate = new CircuitFragment(
                List.of(registry.require(StandardComponentTypes.NOT).newComponent(new ElementId("c1"),
                        GridPoint.ORIGIN, Rotation.R0, Map.of(), "")),
                List.of(), List.of());
        FloatingSelectionTool tool = new FloatingSelectionTool(gate, new GridPoint(5, 5));
        tool.mouseMoved(context, TestToolContext.at(8, 3));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
        tool.mousePressed(context, TestToolContext.at(8, 3));
        tool.mouseDragged(context, TestToolContext.at(12, 3));
        tool.mouseReleased(context, TestToolContext.at(12, 3));
        assertThat(context.openDocument().document().isEmpty()).isTrue(); // still floating at (12, 3)
        tool.mouseMoved(context, TestToolContext.at(30, 30));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.DEFAULT);
        tool.mouseMoved(context, TestToolContext.at(13, 4));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
    }
}
