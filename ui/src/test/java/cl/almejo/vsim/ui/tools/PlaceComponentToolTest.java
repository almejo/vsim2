package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceComponentToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ComponentType clock = context.openDocument().registry().require(StandardComponentTypes.CLOCK);
    private final PlaceComponentTool tool = new PlaceComponentTool(clock);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void aClickPlacesOneComponentAndReturnsToTheDefaultTool() {
        context.tool = tool;
        tool.mousePressed(context, TestToolContext.at(3.4, 4.6));
        assertThat(context.openDocument().document().components()).extracting(Component::position)
                .containsExactly(new GridPoint(3, 5));
        Component placed = context.openDocument().document().components().get(0);
        assertThat(placed.parameters()).isEqualTo(clock.resolveParameters(Map.of()));
        assertThat(context.selection.ids()).containsExactly(placed.id());
        assertThat(context.tool).isSameAs(context.defaultTool);
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().components()).isEmpty();
    }

    @Test
    void shiftClickPlacesAndKeepsPlacing() {
        context.tool = tool;
        tool.mousePressed(context, TestToolContext.event(1, 1, CanvasMouseEvent.PRIMARY, 1, true));
        tool.mousePressed(context, TestToolContext.event(9, 1, CanvasMouseEvent.PRIMARY, 1, true));
        assertThat(context.openDocument().document().components()).hasSize(2);
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void rKeysRotateThePreviewAndTheNextPlacement() {
        assertThat(tool.keyPressed(context, KeyEvent.VK_R, false)).isTrue();
        assertThat(tool.rotation()).isEqualTo(Rotation.R90);
        tool.keyPressed(context, KeyEvent.VK_R, true);
        tool.keyPressed(context, KeyEvent.VK_R, true);
        assertThat(tool.rotation()).isEqualTo(Rotation.R270);
        tool.mousePressed(context, TestToolContext.at(0, 0));
        assertThat(context.openDocument().document().components().get(0).rotation()).isEqualTo(Rotation.R270);
    }

    @Test
    void ghostFollowsTheMouse() {
        tool.mouseMoved(context, TestToolContext.at(7, 8));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).singleElement().isInstanceOfSatisfying(Component.class,
                ghost -> assertThat(ghost.position()).isEqualTo(new GridPoint(7, 8)));
    }

    @Test
    void escapeOrRightClickReturnToTheDefaultTool() {
        context.tool = tool;
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.tool).isSameAs(context.defaultTool);
        context.tool = tool;
        tool.mousePressed(context, TestToolContext.event(0, 0, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(context.openDocument().document().components()).isEmpty();
    }
}
