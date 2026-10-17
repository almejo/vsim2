package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
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
    void doubleClickOnAConstantEditsItsValueInsteadOfConfiguring() {
        Component constant = place(context.openDocument(), StandardComponentTypes.CONSTANT, 0, 0, Map.of());
        tool.mousePressed(context, TestToolContext.at(2, 1));
        tool.mouseReleased(context, TestToolContext.at(2, 1));
        tool.mousePressed(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.valueEdited).isEqualTo(constant);
        assertThat(context.configured).isNull();
        assertThat(context.selection.ids()).containsExactly(constant.id());
    }

    @Test
    void doubleClickOnAComponentLabelEditsTheLabelInsteadOfConfiguring() {
        Component gate = not(0, 10);
        context.openDocument().execute(new SetLabelCommand(gate.id(), "Compuerta U1"));
        Component labelled = context.openDocument().document().component(gate.id()).orElseThrow();
        Rectangle2D labelBounds = LabelLayout.componentLabel(context.openDocument().registry().typeOf(labelled),
                labelled).orElseThrow().bounds();
        double gridX = labelBounds.getCenterX() / GridPoint.PIXELS_PER_UNIT;
        double gridY = labelBounds.getCenterY() / GridPoint.PIXELS_PER_UNIT;
        tool.mousePressed(context, TestToolContext.at(gridX, gridY));
        tool.mouseReleased(context, TestToolContext.at(gridX, gridY));
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.editedLabel).isEqualTo(labelled);
        assertThat(context.configured).isNull();
        assertThat(context.selection.ids()).containsExactly(gate.id());
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

    @Test
    void dragFromAPinDrawsAWireAsOneUndoStep() {
        place(context.openDocument(), StandardComponentTypes.NOT, 0, 0, Map.of());
        context.drag(tool, 4.2, 2.1, 9, 2);
        assertThat(context.openDocument().document().wires()).singleElement()
                .satisfies(wire -> {
                    assertThat(wire.start()).isEqualTo(new GridPoint(4, 2));
                    assertThat(wire.end()).isEqualTo(new GridPoint(9, 2));
                });
        assertThat(context.openDocument().history().undoDescription()).contains("command.wire");
        assertThat(context.openDocument().document().components().get(0).position()).isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void tinyDragFromAPinIsAClick() {
        Component gate = not(0, 0);
        context.drag(tool, 4.1, 2, 4.3, 2.2);
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
    }

    @Test
    void pressOnTheBodyAwayFromPinsStillMoves() {
        Component gate = not(0, 0);
        context.drag(tool, 2, 1, 5, 1);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(3, 0));
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void dragFromAnUnselectedWireStartsANewWireThere() {
        Wire existing = new Wire(context.openDocument().document().newWireId(), new GridPoint(0, 0),
                new GridPoint(10, 0));
        context.openDocument().execute(new AddWireCommand(existing));
        context.drag(tool, 5.2, 0.1, 5, 6);
        assertThat(context.openDocument().document().wires()).hasSize(2)
                .anySatisfy(wire -> {
                    assertThat(wire.start()).isEqualTo(new GridPoint(5, 0));
                    assertThat(wire.end()).isEqualTo(new GridPoint(5, 6));
                });
    }

    @Test
    void dragOfASelectedWireMovesIt() {
        Wire existing = new Wire(context.openDocument().document().newWireId(), new GridPoint(0, 0),
                new GridPoint(10, 0));
        context.openDocument().execute(new AddWireCommand(existing));
        context.click(tool, 5, 0.1);
        assertThat(context.selection.ids()).containsExactly(existing.id());
        context.drag(tool, 5, 0.1, 5, 3);
        assertThat(context.openDocument().document().wires()).singleElement()
                .satisfies(wire -> assertThat(wire.start()).isEqualTo(new GridPoint(0, 3)));
    }

    @Test
    void escapeCancelsAWireInProgress() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(4, 2));
        tool.mouseDragged(context, TestToolContext.at(9, 2));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(9, 2));
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void hoveringNearAPinHighlightsIt() {
        not(0, 0);
        tool.mouseMoved(context, TestToolContext.at(3.8, 2.3));
        assertThat(tool.hoverPin()).isEqualTo(new GridPoint(4, 2));
        tool.mouseMoved(context, TestToolContext.at(2, 2));
        assertThat(tool.hoverPin()).isNull();
    }

    @Test
    void wirePreviewIsPaintedWhileDragging() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(4, 2));
        tool.mouseDragged(context, TestToolContext.at(8, 5));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).hasSize(2).allMatch(Wire.class::isInstance);
    }

    @Test
    void pinJitterBetweenPressAndReleaseDrawsNoWire() {
        Component gate = not(4, 0);
        tool.mousePressed(context, TestToolContext.at(4.45, 2));
        tool.mouseDragged(context, TestToolContext.at(4.55, 2));
        tool.mouseReleased(context, TestToolContext.at(4.55, 2));
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void clickingAWiredPinSelectsTheComponentNotTheWire() {
        Component gate = not(0, 0);
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                new GridPoint(4, 2), new GridPoint(9, 2))));
        context.click(tool, 4.3, 2);
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void clickingAWiredSwitchNextToItsOutputPinTogglesIt() {
        Component lever = place(context.openDocument(), StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        GridPoint output = context.openDocument().registry().require(lever.typeId()).pinLocation(lever, 0);
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                output, new GridPoint(output.x() + 5, output.y()))));
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ONE)));
        context.click(tool, output.x() + 0.2, output.y());
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ZERO)));
        assertThat(context.selection.ids()).containsExactly(lever.id());
    }

    @Test
    void deactivatingClearsTheHoverRingAndAnyPressInProgress() {
        not(0, 0);
        tool.mouseMoved(context, TestToolContext.at(3.8, 2.3));
        tool.mousePressed(context, TestToolContext.at(4, 2));
        tool.mouseDragged(context, TestToolContext.at(9, 2));
        tool.deactivated(context);
        assertThat(tool.hoverPin()).isNull();
        tool.mouseReleased(context, TestToolContext.at(9, 2));
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void releaseRecomputesTheHoverRing() {
        not(0, 0);
        tool.mouseMoved(context, TestToolContext.at(3.8, 2.3));
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(2.2, 2));
        tool.mouseReleased(context, TestToolContext.at(2.2, 2));
        assertThat(tool.hoverPin()).isNull();
    }

    @Test
    void releaseNextToAPinShowsTheRing() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(2.2, 2));
        tool.mouseReleased(context, TestToolContext.at(4.2, 2));
        assertThat(tool.hoverPin()).isEqualTo(new GridPoint(4, 2));
    }

    @Test
    void leavingTheCanvasClearsTheHoverRing() {
        not(0, 0);
        tool.mouseMoved(context, TestToolContext.at(3.8, 2.3));
        assertThat(tool.hoverPin()).isNotNull();
        tool.mouseExited(context);
        assertThat(tool.hoverPin()).isNull();
    }

    @Test
    void aFirstPressWithClickCountTwoAfterActivationDoesNotConfigure() {
        Component gate = not(0, 0);
        tool.activated(context);
        tool.mousePressed(context, TestToolContext.event(2, 2, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(2, 2, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.configured).isNull();
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void shiftClickAddsAndRemovesElements() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.click(tool, 2, 2);
        context.clickWith(tool, 12, 2, true, false);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        context.clickWith(tool, 2, 2, true, false);
        assertThat(context.selection.ids()).containsExactly(second.id());
    }

    @Test
    void controlClickTogglesToo() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.clickWith(tool, 2, 2, false, true);
        context.clickWith(tool, 12, 2, false, true);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        context.clickWith(tool, 12, 2, false, true);
        assertThat(context.selection.ids()).containsExactly(first.id());
    }

    @Test
    void shiftClickOnAPinTogglesItsComponentWithoutDrawing() {
        Component gate = not(0, 0);
        context.clickWith(tool, 4.1, 2, true, false);
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void plainClickOnAnUnselectedElementReplacesTheSelection() {
        not(0, 0);
        not(10, 0);
        Component third = not(20, 0);
        context.clickWith(tool, 2, 2, true, false);
        context.clickWith(tool, 12, 2, true, false);
        context.click(tool, 22, 2);
        assertThat(context.selection.ids()).containsExactly(third.id());
    }

    @Test
    void plainClickOnAnElementOfAMultiSelectionNarrowsTheSelectionToIt() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.selection.set(List.of(first.id(), second.id()));
        tool.mousePressed(context, TestToolContext.at(2, 2));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        tool.mouseReleased(context, TestToolContext.at(2, 2));
        assertThat(context.selection.ids()).containsExactly(first.id());
        context.selection.set(List.of(first.id(), second.id()));
        context.click(tool, 14.1, 2);
        assertThat(context.selection.ids()).containsExactly(second.id());
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void draggingAnElementOfAMultiSelectionMovesTheWholeGroupAsOneStep() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        context.openDocument().execute(new AddWireCommand(wire));
        context.selection.set(List.of(first.id(), second.id(), wire.id()));
        context.drag(tool, 2, 2, 5, 3);
        var document = context.openDocument().document();
        assertThat(document.component(first.id()).orElseThrow().position()).isEqualTo(new GridPoint(3, 1));
        assertThat(document.component(second.id()).orElseThrow().position()).isEqualTo(new GridPoint(13, 1));
        assertThat(document.wire(wire.id()).orElseThrow().start()).isEqualTo(new GridPoint(3, 9));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
        context.openDocument().history().undo();
        assertThat(document.component(first.id()).orElseThrow().position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(document.component(second.id()).orElseThrow().position()).isEqualTo(new GridPoint(10, 0));
        assertThat(document.wire(wire.id()).orElseThrow().start()).isEqualTo(new GridPoint(0, 8));
    }

    @Test
    void pressOnAPinOfASelectedComponentInAMultiSelectionMovesTheGroup() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.selection.set(List.of(first.id(), second.id()));
        context.drag(tool, 4.1, 2, 7.1, 2);
        var document = context.openDocument().document();
        assertThat(document.wires()).isEmpty();
        assertThat(document.component(first.id()).orElseThrow().position()).isEqualTo(new GridPoint(3, 0));
        assertThat(document.component(second.id()).orElseThrow().position()).isEqualTo(new GridPoint(13, 0));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
    }

    @Test
    void pinOfAComponentOutsideTheSelectionStillStartsAWire() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        not(20, 0);
        context.selection.set(List.of(first.id(), second.id()));
        context.drag(tool, 24.1, 2, 28, 2);
        assertThat(context.openDocument().document().wires()).singleElement().satisfies(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(24, 2));
            assertThat(wire.end()).isEqualTo(new GridPoint(28, 2));
        });
        assertThat(context.openDocument().document().component(first.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void pinOfTheOnlySelectedComponentStillStartsAWire() {
        Component gate = not(0, 0);
        context.click(tool, 2, 2);
        context.drag(tool, 4.1, 2, 9, 2);
        assertThat(context.openDocument().document().wires()).hasSize(1);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void controlPressDoesNotDragButShiftPressMovesTheSelection() {
        Component gate = not(0, 0);
        context.click(tool, 2, 2);
        context.dragWith(tool, 2, 2, 5, 3, false, true);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
        assertThat(context.selection.isEmpty()).isTrue();
        context.click(tool, 2, 2);
        context.dragWith(tool, 2, 2, 5, 3, true, false);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(3, 1));
        assertThat(context.selection.ids()).containsExactly(gate.id());
        context.openDocument().history().undo();
        context.selection.clear();
        context.dragWith(tool, 4.1, 2, 9, 2, false, true);
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
    }

    private Wire wireFrom(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        context.openDocument().execute(new AddWireCommand(wire));
        return wire;
    }

    private TextLabel labelAt(int x, int y) {
        TextLabel label = new TextLabel(context.openDocument().document().newLabelId(), new GridPoint(x, y), "nota");
        context.openDocument().execute(new AddElementsCommand(List.of(label)));
        return label;
    }

    @Test
    void bandSelectsEverythingFullyInsideWithoutAnUndoStep() {
        Component inside = not(0, 0);
        not(10, 0);
        Wire wire = wireFrom(0, 8, 6, 8);
        TextLabel note = labelAt(2, 10);
        String before = context.openDocument().history().undoDescription().toString();
        context.drag(tool, -1, -1, 7, 11);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(inside.id(), wire.id(), note.id());
        assertThat(context.openDocument().history().undoDescription().toString()).isEqualTo(before);
    }

    @Test
    void partlyCoveredElementsAreLeftOut() {
        Component inside = not(0, 0);
        not(10, 0);
        wireFrom(0, 8, 20, 8);
        context.drag(tool, -1, -1, 12, 9);
        assertThat(context.selection.ids()).containsExactly(inside.id());
    }

    @Test
    void bandWorksInAnyDirection() {
        Component gate = not(0, 0);
        context.drag(tool, 5, 5, -1, -1);
        assertThat(context.selection.ids()).containsExactly(gate.id());
        context.selection.clear();
        context.drag(tool, 5, -1, -1, 5);
        assertThat(context.selection.ids()).containsExactly(gate.id());
        context.selection.clear();
        context.drag(tool, -1, 5, 5, -1);
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void plainBandReplacesAndShiftOrControlBandAdds() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.click(tool, 12, 2);
        context.drag(tool, -1, -1, 5, 5);
        assertThat(context.selection.ids()).containsExactly(first.id());
        context.click(tool, 12, 2);
        context.dragWith(tool, -1, -1, 5, 5, true, false);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        context.selection.select(second.id());
        context.dragWith(tool, -1, -1, 5, 5, false, true);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void controlShiftBandTakesTheEnclosedElementsOutOfTheSelection() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        Component third = not(20, 0);
        context.selection.set(java.util.Set.of(first.id(), second.id(), third.id()));
        context.dragWith(tool, -1, -1, 15, 5, true, true);
        assertThat(context.selection.ids()).containsExactly(third.id());
    }

    @Test
    void controlShiftBandLeavesPartlyCoveredAndUnselectedElementsAlone() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        not(20, 0);
        context.selection.set(java.util.Set.of(first.id(), second.id()));
        context.dragWith(tool, -1, -1, 12, 5, true, true);
        assertThat(context.selection.ids()).containsExactly(second.id());
        context.dragWith(tool, 19, -1, 25, 5, true, true);
        assertThat(context.selection.ids()).containsExactly(second.id());
        context.clickWith(tool, 30, 30, true, true);
        assertThat(context.selection.ids()).containsExactly(second.id());
    }

    @Test
    void theSubtractBandIsPaintedDifferentlyFromTheAdditiveOne() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.withModifiers(-1, -1, true, false));
        tool.mouseDragged(context, TestToolContext.withModifiers(5, 3, true, false));
        java.awt.image.BufferedImage adding = paintBandWithPhase(0);
        tool.mouseReleased(context, TestToolContext.withModifiers(5, 3, true, false));
        tool.mousePressed(context, TestToolContext.withModifiers(-1, -1, true, true));
        tool.mouseDragged(context, TestToolContext.withModifiers(5, 3, true, true));
        java.awt.image.BufferedImage removing = paintBandWithPhase(0);
        tool.mouseReleased(context, TestToolContext.withModifiers(5, 3, true, true));
        boolean different = false;
        for (int y = 0; y < adding.getHeight() && !different; y++) {
            for (int x = 0; x < adding.getWidth() && !different; x++) {
                different = adding.getRGB(x, y) != removing.getRGB(x, y);
            }
        }
        assertThat(different).isTrue();
    }

    @Test
    void shiftClickOnEmptySpaceKeepsTheSelection() {
        Component gate = not(0, 0);
        context.click(tool, 2, 2);
        context.clickWith(tool, 30, 30, true, false);
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void escapeCancelsTheBand() {
        Component gate = not(0, 0);
        not(10, 0);
        context.click(tool, 2, 2);
        tool.mousePressed(context, TestToolContext.at(-1, -1));
        tool.mouseDragged(context, TestToolContext.at(15, 5));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(tool.rubberBand()).isNull();
        tool.mouseReleased(context, TestToolContext.at(15, 5));
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    @Test
    void theBandIsPaintedWhileDragging() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(-1, -1));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        assertThat(tool.rubberBand()).isEqualTo(new Rectangle2D.Double(-1, -1, 6, 4));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(tool.rubberBand()).isNull();
    }

    @Test
    void theBandIsPaintedWithThePhaseFromTheContext() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(-1, -1));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        assertThat(tool.animatesOutline()).isTrue();
        java.awt.image.BufferedImage first = paintBandWithPhase(0);
        java.awt.image.BufferedImage second = paintBandWithPhase(3);
        boolean different = false;
        for (int y = 0; y < first.getHeight() && !different; y++) {
            for (int x = 0; x < first.getWidth() && !different; x++) {
                different = first.getRGB(x, y) != second.getRGB(x, y);
            }
        }
        assertThat(different).isTrue();
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(tool.animatesOutline()).isFalse();
    }

    private java.awt.image.BufferedImage paintBandWithPhase(double phase) {
        context.selectionDashPhase = phase;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(200, 150,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.translate(40, 40);
        tool.paintOverlay(graphics, context);
        graphics.dispose();
        return image;
    }

    @Test
    void doubleClickOnAFreeTextEditsIt() {
        TextLabel label = labelAt(10, 10);
        tool.mousePressed(context, TestToolContext.at(10.3, 10.5));
        tool.mouseReleased(context, TestToolContext.at(10.3, 10.5));
        tool.mousePressed(context, TestToolContext.event(10.3, 10.5, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(10.3, 10.5, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.editedLabel).isEqualTo(label);
        assertThat(context.selection.ids()).containsExactly(label.id());
        assertThat(context.configured).isNull();
    }

    @Test
    void draggingAFreeTextMovesIt() {
        TextLabel label = labelAt(10, 10);
        context.drag(tool, 10.3, 10.5, 13.3, 12.5);
        assertThat(context.openDocument().document().label(label.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(13, 12));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
    }

    @Test
    void movingFromIsKnownOnlyWhileTheSelectionMayBeMoved() {
        not(0, 0);
        assertThat(tool.movingFrom()).isEmpty();
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        assertThat(tool.movingFrom()).contains(new GridPoint(2, 2));
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(tool.movingFrom()).isEmpty();
    }

    @Test
    void drawingAWireFromAPinIsNotAMove() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(4, 2));
        tool.mouseDragged(context, TestToolContext.at(8, 2));
        assertThat(tool.movingFrom()).isEmpty();
    }

    @Test
    void cancelMoveAbandonsTheDragWithoutAnUndoStep() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        tool.cancelMove(context);
        tool.mouseReleased(context, TestToolContext.at(5, 3));
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
        assertThat(context.openDocument().history().undoDescription()).contains("command.add");
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(tool.movingFrom()).isEmpty();
    }

    @Test
    void movingFromIsEmptyWhileARubberBandIsDragged() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(-1, -1));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        assertThat(tool.movingFrom()).isEmpty();
        tool.mouseReleased(context, TestToolContext.at(5, 3));
    }

    @Test
    void pressOnAFreeTextOverAPinMovesTheTextInsteadOfStartingAWire() {
        not(0, 0); // output pin at (4, 2)
        TextLabel label = labelAt(4, 2);
        assertThat(context.hitTester().elementAt(context.openDocument().document(), new Point2D.Double(4.2, 2.4)))
                .contains(label);
        context.drag(tool, 4.2, 2.4, 8.2, 6.4);
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(context.openDocument().document().label(label.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(8, 6));
        assertThat(context.selection.ids()).containsExactly(label.id());
    }

    @Test
    void autoscrollsOnlyWhileMovingTheSelectionOrDraggingABand() {
        not(0, 0);
        assertThat(tool.autoscrolls()).isFalse();
        tool.mousePressed(context, TestToolContext.at(2, 2));
        assertThat(tool.autoscrolls()).isTrue();
        tool.mouseReleased(context, TestToolContext.at(2, 2));
        assertThat(tool.autoscrolls()).isFalse();
        tool.mousePressed(context, TestToolContext.at(30, 30));
        tool.mouseDragged(context, TestToolContext.at(34, 34));
        assertThat(tool.autoscrolls()).isTrue();
        tool.mouseReleased(context, TestToolContext.at(34, 34));
        tool.mousePressed(context, TestToolContext.at(0, 2)); // the NOT input pin: draws a wire
        assertThat(tool.autoscrolls()).isFalse();
    }

    @Test
    void controlHeldAtTheReleaseCopiesTheSelectionAsOneUndoStep() {
        Component gate = not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseDragged(context, TestToolContext.at(5, 3));
        tool.mouseReleased(context, TestToolContext.withModifiers(5, 3, false, true));

        List<Component> components = context.openDocument().document().components();
        assertThat(components).hasSize(2);
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(GridPoint.ORIGIN);
        Component copy = components.get(1);
        assertThat(copy.position()).isEqualTo(new GridPoint(3, 1));
        assertThat(context.selection.ids()).containsExactly(copy.id());
        assertThat(context.openDocument().history().undoDescription()).contains("command.paste");
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().components()).containsExactly(gate);
    }
}
