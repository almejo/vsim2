package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceDefinitionToolTest {

    private final DefinitionBundle doubleInverter = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));

    @Test
    void placingBringsEveryDefinitionInOneUndoStep() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            PlaceDefinitionTool tool = new PlaceDefinitionTool(doubleInverter);
            context.setTool(tool);
            CircuitDocument document = context.openDocument().document();

            tool.mouseMoved(context, TestToolContext.at(10, 10));
            tool.paintOverlay(TestToolContext.scratchGraphics(), context);
            assertThat(context.ghosts).singleElement()
                    .extracting(element -> ((Component) element).typeId()).isEqualTo("subcircuit:double-inverter");

            tool.mousePressed(context, TestToolContext.at(10, 10));

            assertThat(document.definitions())
                    .containsExactly(ExampleDefinitions.inverter(), ExampleDefinitions.doubleInverter());
            Component instance = document.components().get(0);
            assertThat(document.components()).hasSize(1);
            assertThat(instance.position()).isEqualTo(new GridPoint(10, 10));
            assertThat(context.selection.ids()).containsExactly(instance.id());
            assertThat(context.tool).isSameAs(context.defaultTool);

            context.openDocument().history().undo();
            assertThat(document.definitions()).isEmpty();
            assertThat(document.components()).isEmpty();
        }
    }

    @Test
    void shiftKeepsPlacingAndRKeysRotate() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            PlaceDefinitionTool tool = new PlaceDefinitionTool(doubleInverter);
            context.setTool(tool);
            assertThat(tool.keyPressed(context, KeyEvent.VK_R, false)).isTrue();

            tool.mousePressed(context, TestToolContext.event(0, 0, CanvasMouseEvent.PRIMARY, 1, true));
            assertThat(context.tool).isSameAs(tool);
            tool.mousePressed(context, TestToolContext.at(10, 0));

            CircuitDocument document = context.openDocument().document();
            assertThat(document.components()).extracting(Component::rotation)
                    .containsExactly(Rotation.R90, Rotation.R90);
            assertThat(document.definitions()).hasSize(2);
            assertThat(context.tool).isSameAs(context.defaultTool);

            context.setTool(tool);
            assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
            assertThat(context.tool).isSameAs(context.defaultTool);
        }
    }

    @Test
    void aRightClickReturnsToTheDefaultToolWithoutPlacing() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            PlaceDefinitionTool tool = new PlaceDefinitionTool(doubleInverter);
            context.setTool(tool);

            tool.mousePressed(context, TestToolContext.event(4, 4, CanvasMouseEvent.SECONDARY, 1, false));

            assertThat(context.tool).isSameAs(context.defaultTool);
            assertThat(context.openDocument().document().definitions()).isEmpty();
        }
    }
}
