package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FloatingSelectionToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ComponentTypeRegistry registry = context.openDocument().registry();
    private final CircuitDocument document = context.openDocument().document();
    private final History history = context.openDocument().history();
    /** One NOT gate (4x4) labelled U1 at the fragment origin. */
    private final CircuitFragment gate = new CircuitFragment(
            List.of(registry.require(StandardComponentTypes.NOT).newComponent(new ElementId("c1"), GridPoint.ORIGIN,
                    Rotation.R0, Map.of(), "U1")),
            List.of(), List.of());

    @AfterEach
    void close() {
        context.close();
    }

    private FloatingSelectionTool floating(int x, int y) {
        FloatingSelectionTool tool = new FloatingSelectionTool(gate, new GridPoint(x, y));
        context.setTool(tool);
        return tool;
    }

    @Test
    void followsTheMouseAndIsOnlyAGhostUntilCommitted() {
        FloatingSelectionTool tool = floating(5, 5);
        tool.mouseMoved(context, TestToolContext.at(8.2, 3.1));
        assertThat(tool.position()).isEqualTo(new GridPoint(8, 3));
        assertThat(tool.fragment()).isSameAs(gate);
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).singleElement()
                .satisfies(ghost -> assertThat(((Component) ghost).position()).isEqualTo(new GridPoint(8, 3)));
        assertThat(document.isEmpty()).isTrue();
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void oneClickOnTheFirstPressDropsThePasteWhereItIs() {
        FloatingSelectionTool tool = floating(5, 5);
        tool.mouseMoved(context, TestToolContext.at(8.2, 3.1));
        context.click(tool, 8.2, 3.1);
        assertThat(document.components()).singleElement()
                .satisfies(component -> assertThat(component.position()).isEqualTo(new GridPoint(8, 3)));
        assertThat(context.selection.ids()).containsExactly(document.components().get(0).id());
        assertThat(history.undoDescription()).contains("command.paste");
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void aFirstPressThatMovesLessThanTheToleranceIsStillAClick() {
        FloatingSelectionTool tool = floating(8, 3);
        tool.mousePressed(context, TestToolContext.at(9, 4));
        tool.mouseDragged(context, TestToolContext.at(9.3, 4.2));
        tool.mouseReleased(context, TestToolContext.at(9.3, 4.2));
        assertThat(document.components()).singleElement()
                .satisfies(component -> assertThat(component.position()).isEqualTo(new GridPoint(8, 3)));
    }

    @Test
    void aDraggedFirstPressKeepsItFloatingAndAClickOutsideCommitsOneUndoStep() {
        FloatingSelectionTool tool = floating(8, 3);
        context.drag(tool, 9, 4, 12, 6);
        assertThat(tool.position()).isEqualTo(new GridPoint(11, 5));
        assertThat(document.isEmpty()).isTrue();
        tool.mouseMoved(context, TestToolContext.at(30, 30));
        assertThat(tool.position()).isEqualTo(new GridPoint(11, 5));

        context.drag(tool, 12, 6, 14, 6);
        assertThat(tool.position()).isEqualTo(new GridPoint(13, 5));
        context.click(tool, 13.5, 6.5);
        assertThat(document.isEmpty()).isTrue();

        context.click(tool, 30, 30);
        assertThat(document.components()).singleElement().satisfies(component -> {
            assertThat(component.position()).isEqualTo(new GridPoint(13, 5));
            assertThat(component.label()).isEqualTo("U1");
        });
        assertThat(context.selection.ids()).containsExactly(document.components().get(0).id());
        assertThat(history.undoDescription()).contains("command.paste");
        assertThat(context.tool).isSameAs(context.defaultTool);
        history.undo();
        assertThat(document.isEmpty()).isTrue();
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void enterCommitsAndEscapeCancels() {
        FloatingSelectionTool tool = floating(2, 2);
        assertThat(tool.keyPressed(context, KeyEvent.VK_ENTER, false)).isTrue();
        assertThat(document.components()).hasSize(1);
        FloatingSelectionTool second = floating(20, 2);
        assertThat(second.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        second.deactivated(context);
        assertThat(document.components()).hasSize(1);
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(second.keyPressed(context, KeyEvent.VK_A, false)).isFalse();
    }

    @Test
    void deactivationCommitsTheFloatingFragmentOnce() {
        FloatingSelectionTool tool = floating(4, 4);
        tool.deactivated(context);
        tool.deactivated(context);
        assertThat(document.components()).singleElement()
                .satisfies(component -> assertThat(component.position()).isEqualTo(new GridPoint(4, 4)));
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void emptyFragmentAddsNoUndoStep() {
        FloatingSelectionTool tool = new FloatingSelectionTool(
                new CircuitFragment(List.of(), List.of(), List.of()), GridPoint.ORIGIN);
        context.setTool(tool);
        tool.keyPressed(context, KeyEvent.VK_ENTER, false);
        assertThat(history.canUndo()).isFalse();
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void secondaryPressNeitherCommitsNorGrabs() {
        FloatingSelectionTool tool = floating(0, 0);
        tool.mousePressed(context, TestToolContext.event(30, 30, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(document.isEmpty()).isTrue();
        tool.mouseMoved(context, TestToolContext.at(6, 6));
        assertThat(tool.position()).isEqualTo(new GridPoint(6, 6));
    }

    @Test
    void activationClearsTheDocumentSelection() {
        context.selection.set(List.of(new ElementId("c99")));
        FloatingSelectionTool tool = floating(0, 0);
        tool.activated(context);
        assertThat(context.selection.isEmpty()).isTrue();
    }

    @Test
    void deleteAndBackspaceCancelWithoutTouchingTheDocument() {
        for (int keyCode : new int[] {KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE}) {
            FloatingSelectionTool tool = floating(0, 0);
            assertThat(tool.keyPressed(context, keyCode, false)).isTrue();
            tool.deactivated(context);
            assertThat(document.isEmpty()).isTrue();
            assertThat(history.canUndo()).isFalse();
            assertThat(context.tool).isSameAs(context.defaultTool);
        }
    }

    @Test
    void rotateKeysAreConsumedAndChangeNothing() {
        FloatingSelectionTool tool = floating(3, 3);
        assertThat(tool.keyPressed(context, KeyEvent.VK_R, false)).isTrue();
        assertThat(tool.keyPressed(context, KeyEvent.VK_R, true)).isTrue();
        assertThat(tool.position()).isEqualTo(new GridPoint(3, 3));
        assertThat(document.isEmpty()).isTrue();
        assertThat(history.canUndo()).isFalse();
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void aWireOnlyFragmentCanBeGrabbedWithinTheMargin() {
        CircuitFragment wireOnly = new CircuitFragment(List.of(),
                List.of(new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(10, 0))), List.of());
        FloatingSelectionTool tool = new FloatingSelectionTool(wireOnly, new GridPoint(5, 5));
        context.setTool(tool);
        context.drag(tool, 7, 5, 9, 5);
        assertThat(tool.position()).isEqualTo(new GridPoint(7, 5));
        double justInside = 7 + 10 + FloatingSelectionTool.GRAB_MARGIN - 0.1;
        context.drag(tool, justInside, 5.3, justInside + 2, 5.3);
        assertThat(tool.position()).isEqualTo(new GridPoint(9, 5));
        assertThat(document.isEmpty()).isTrue();
        context.click(tool, 40, 40);
        assertThat(document.wires()).hasSize(1);
    }

    @Test
    void enterAfterADraggedFirstPressCommitsAtTheDraggedPosition() {
        FloatingSelectionTool tool = floating(8, 3);
        context.drag(tool, 9, 4, 12, 6);
        assertThat(document.isEmpty()).isTrue();
        assertThat(tool.keyPressed(context, KeyEvent.VK_ENTER, false)).isTrue();
        assertThat(document.components()).singleElement()
                .satisfies(component -> assertThat(component.position()).isEqualTo(new GridPoint(11, 5)));
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void aFragmentWithItsOwnDefinitionFloatsAndCommitsInADocumentWithoutIt() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(inverter.id()),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(), List.of(inverter));
        FloatingSelectionTool tool = new FloatingSelectionTool(fragment, new GridPoint(10, 10));
        context.setTool(tool);

        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.mousePressed(context, TestToolContext.at(11, 11));
        tool.mouseDragged(context, TestToolContext.at(13, 13));
        tool.mouseReleased(context, TestToolContext.at(13, 13));
        tool.mousePressed(context, TestToolContext.at(40, 40));

        assertThat(document.definition(inverter.id())).contains(inverter);
        assertThat(document.components()).singleElement()
                .extracting(Component::typeId).isEqualTo("subcircuit:inverter");
        assertThat(context.ghosts).isNotEmpty();
    }
}
