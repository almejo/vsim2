package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class SelectToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    private Component not(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.NOT, x, y, Map.of());
    }

    @Test
    void clickingAComponentSelectsIt() {
        Component gate = not(0, 0);
        context.click(tool, 2, 2);
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void clickingAWireSelectsIt() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(10, 0), new GridPoint(20, 0));
        context.openDocument().execute(new AddWireCommand(wire));
        context.click(tool, 15, 0.2);
        assertThat(context.selection.ids()).containsExactly(wire.id());
    }

    @Test
    void clickingEmptySpaceClearsTheSelection() {
        not(0, 0);
        context.click(tool, 2, 2);
        context.click(tool, 30, 30);
        assertThat(context.selection.isEmpty()).isTrue();
    }

    @Test
    void draggingMovesTheSelectionAsOneUndoStep() {
        Component gate = not(0, 0);
        context.drag(tool, 2, 2, 5, 3);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(3, 1));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void releasingWithoutMovingCreatesNoUndoStep() {
        not(0, 0);
        context.drag(tool, 2, 2, 2.3, 1.8);
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
    }

    @Test
    void dragPaintsGhostsOfTheSelectionAtTheNewPlace() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(6, 2));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).containsExactly(gate.translate(4, 0));
    }

    @Test
    void escapeCancelsADragInProgress() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(6, 2));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(6, 2));
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void releasingANonPrimaryButtonDoesNotEndTheDrag() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        tool.mouseReleased(context, TestToolContext.event(5, 3, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position()).isEqualTo(GridPoint.ORIGIN);
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(3, 1));
    }

    @Test
    void releasingWithAnEmptySelectionExecutesNothing() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        context.selection.clear();
        String before = context.openDocument().history().undoDescription().toString();
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(context.openDocument().history().undoDescription().toString()).isEqualTo(before);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position()).isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void escapeClearsTheSelection() {
        not(0, 0);
        context.click(tool, 2, 2);
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.selection.isEmpty()).isTrue();
    }

    @Test
    void clickingASwitchTogglesItWithoutTouchingTheHistory() {
        Component lever = place(context.openDocument(), StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ONE)));
        context.click(tool, 1, 1);
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ZERO)));
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
    }

    @Test
    void doubleClickConfiguresTheComponent() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseReleased(context, TestToolContext.at(2, 2));
        tool.mousePressed(context, TestToolContext.event(2, 2, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(2, 2, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.configured).isEqualTo(gate);
    }

    @Test
    void rightClickSelectsTheElementAndOpensTheContextMenu() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.event(2, 2, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(context.contextMenuTarget).contains(gate);
    }

    @Test
    void rightClickOnEmptySpaceOpensTheCanvasMenu() {
        not(0, 0);
        context.click(tool, 2, 2);
        tool.mousePressed(context, TestToolContext.event(30, 30, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.selection.isEmpty()).isTrue();
        assertThat(context.contextMenuTarget).isEqualTo(Optional.empty());
    }
}
