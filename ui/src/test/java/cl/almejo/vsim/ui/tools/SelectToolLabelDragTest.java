package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.SetLabelOffsetCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** M6.8 front 2: a component's label is dragged with the select tool. */
class SelectToolLabelDragTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    private Component labelled(int x, int y, String label) {
        Component gate = place(context.openDocument(), StandardComponentTypes.NOT, x, y, Map.of());
        context.openDocument().execute(new SetLabelCommand(gate.id(), label));
        return current(gate);
    }

    private Component current(Component component) {
        return context.openDocument().document().component(component.id()).orElseThrow();
    }

    /** The centre of {@code component}'s drawn label, in grid units. */
    private Point2D labelCentre(Component component) {
        Component now = current(component);
        Rectangle2D bounds = LabelLayout.componentLabel(context.openDocument().registry().typeOf(now), now)
                .orElseThrow().bounds();
        return new Point2D.Double(bounds.getCenterX() / GridPoint.PIXELS_PER_UNIT,
                bounds.getCenterY() / GridPoint.PIXELS_PER_UNIT);
    }

    @Test
    void draggingALabelMovesOnlyTheLabelAsOneUndoStep() {
        Component gate = labelled(0, 10, "Compuerta U1");
        Point2D before = labelCentre(gate);
        context.drag(tool, before.getX(), before.getY(), before.getX() + 3, before.getY());
        Component moved = current(gate);
        assertThat(moved.position()).isEqualTo(gate.position());
        assertThat(moved.labelOffset()).isPresent();
        Point2D after = labelCentre(gate);
        assertThat(after.getX()).isCloseTo(before.getX() + 3, within(0.51));
        assertThat(after.getY()).isCloseTo(before.getY(), within(0.51));
        assertThat(after.getX() % 1).isCloseTo(0, within(0.001)); // the centre snaps to a grid point
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(context.openDocument().history().undoDescription()).contains("command.moveLabel");
        context.openDocument().history().undo();
        assertThat(current(gate)).isEqualTo(gate);
    }

    @Test
    void aMovedLabelCanBeDraggedAgain() {
        Component gate = labelled(0, 10, "U1");
        context.openDocument().execute(new SetLabelOffsetCommand(gate.id(), Optional.of(new LabelOffset(2, 8))));
        Point2D before = labelCentre(gate);
        context.drag(tool, before.getX(), before.getY(), before.getX() - 1, before.getY() + 2);
        assertThat(current(gate).labelOffset()).contains(new LabelOffset(1, 10));
    }

    @Test
    void clickingALabelWithoutDraggingSelectsTheComponent() {
        Component gate = labelled(0, 10, "Compuerta U1");
        Point2D centre = labelCentre(gate);
        context.drag(tool, centre.getX(), centre.getY(), centre.getX() + 0.2, centre.getY() + 0.1);
        assertThat(context.selection.ids()).containsExactly(gate.id());
        assertThat(current(gate).labelOffset()).isEmpty();
        assertThat(context.openDocument().history().undoDescription()).contains("command.label");
    }

    @Test
    void aLabelDragIsPreviewedAndIsNotAMoveOfTheSelection() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mouseDragged(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        assertThat(tool.labelDragPreview()).isPresent();
        assertThat(tool.movingFrom()).isEmpty();
        assertThat(tool.isIdle()).isFalse();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).isEmpty();
        tool.mouseReleased(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        assertThat(tool.labelDragPreview()).isEmpty();
        assertThat(tool.isIdle()).isTrue();
    }

    @Test
    void escapeCancelsALabelDrag() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mouseDragged(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        assertThat(current(gate).labelOffset()).isEmpty();
        assertThat(context.openDocument().history().undoDescription()).contains("command.label");
    }

    @Test
    void doubleClickOnAMovedLabelEditsIt() {
        Component gate = labelled(0, 10, "U1");
        context.openDocument().execute(new SetLabelOffsetCommand(gate.id(), Optional.of(new LabelOffset(12, 2))));
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mouseReleased(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mousePressed(context, TestToolContext.event(centre.getX(), centre.getY(), CanvasMouseEvent.PRIMARY, 2,
                false));
        tool.mouseReleased(context, TestToolContext.event(centre.getX(), centre.getY(), CanvasMouseEvent.PRIMARY, 2,
                false));
        assertThat(context.editedLabel).isEqualTo(current(gate));
        assertThat(context.configured).isNull();
    }

    @Test
    void aLabelDragAfterADoubleClickIsNotStarted() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mouseReleased(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mousePressed(context, TestToolContext.event(centre.getX(), centre.getY(), CanvasMouseEvent.PRIMARY, 2,
                false));
        tool.mouseDragged(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        tool.mouseReleased(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        assertThat(current(gate).labelOffset()).isEmpty();
    }

    private void doubleClick(double gridX, double gridY) {
        tool.mousePressed(context, TestToolContext.at(gridX, gridY));
        tool.mouseReleased(context, TestToolContext.at(gridX, gridY));
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
    }

    @Test
    void aDoubleClickOnALabelOverAWireEditsTheLabelAndKeepsTheComponentSelected() {
        Component gate = labelled(0, 10, "Compuerta U1");
        Point2D centre = labelCentre(gate);
        int wireY = (int) Math.round(centre.getY());
        Wire wire = new Wire(context.openDocument().document().newWireId(),
                new GridPoint((int) centre.getX() - 20, wireY), new GridPoint((int) centre.getX() + 20, wireY));
        context.openDocument().execute(new AddWireCommand(wire));
        // the precondition: the wire runs through the label box
        assertThat(context.hitTester().elementAt(context.openDocument().document(),
                new Point2D.Double(centre.getX(), wireY))).contains(wire);
        assertThat(LabelLayout.componentLabel(context.openDocument().registry().typeOf(gate), gate).orElseThrow()
                .bounds().contains(centre.getX() * GridPoint.PIXELS_PER_UNIT, wireY * GridPoint.PIXELS_PER_UNIT))
                .isTrue();

        doubleClick(centre.getX(), wireY);

        assertThat(context.editedLabel).isEqualTo(current(gate));
        assertThat(context.selection.ids()).containsExactly(gate.id());
    }

    /** Moves {@code gate}'s label centre onto the middle of {@code target}'s body; returns that centre. */
    private Point2D labelOnBodyOf(Component gate, Component target) {
        Bounds body = context.openDocument().registry().typeOf(target).bounds(target);
        double middleX = (body.x() + body.width() / 2.0) * GridPoint.PIXELS_PER_UNIT;
        double middleY = (body.y() + body.height() / 2.0) * GridPoint.PIXELS_PER_UNIT;
        context.openDocument().execute(new SetLabelOffsetCommand(gate.id(),
                Optional.of(LabelLayout.offsetFor(current(gate), middleX, middleY))));
        Point2D centre = labelCentre(gate);
        assertThat(context.hitTester().elementAt(context.openDocument().document(), centre))
                .contains(current(target));
        return centre;
    }

    @Test
    void theBodyOfAnotherComponentWinsOverALabelMovedOntoIt() {
        Component gate = labelled(0, 10, "U1");
        Component other = place(context.openDocument(), StandardComponentTypes.NOT, 20, 10, Map.of());
        Point2D centre = labelOnBodyOf(gate, other);
        Optional<LabelOffset> offset = current(gate).labelOffset();

        context.drag(tool, centre.getX(), centre.getY(), centre.getX() + 3, centre.getY());

        assertThat(current(other).position()).isEqualTo(other.position().plus(new GridPoint(3, 0)));
        assertThat(current(gate).labelOffset()).isEqualTo(offset);
    }

    @Test
    void aDoubleClickOnALabelMovedOntoItsOwnBodyConfiguresTheComponent() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelOnBodyOf(gate, gate);

        doubleClick(centre.getX(), centre.getY());

        assertThat(context.configured).isEqualTo(current(gate));
        assertThat(context.editedLabel).isNull();
    }

    @Test
    void aLabelReleasedAfterItsComponentWasRemovedSelectsNothing() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        context.openDocument().execute(new RemoveElementsCommand(List.of(gate.id())));
        tool.mouseReleased(context, TestToolContext.at(centre.getX(), centre.getY()));
        assertThat(context.selection.isEmpty()).isTrue();

        Component again = labelled(0, 10, "U2");
        Point2D againCentre = labelCentre(again);
        tool.mousePressed(context, TestToolContext.at(againCentre.getX(), againCentre.getY()));
        tool.mouseDragged(context, TestToolContext.at(againCentre.getX() + 4, againCentre.getY()));
        context.openDocument().execute(new RemoveElementsCommand(List.of(again.id())));
        tool.mouseReleased(context, TestToolContext.at(againCentre.getX() + 4, againCentre.getY()));
        assertThat(context.selection.isEmpty()).isTrue();
        assertThat(tool.isIdle()).isTrue();
    }

    @Test
    void modifierKeysPressedDuringALabelDragAreIgnored() {
        Component gate = labelled(0, 10, "U1");
        Point2D centre = labelCentre(gate);
        tool.mousePressed(context, TestToolContext.at(centre.getX(), centre.getY()));
        tool.mouseDragged(context, TestToolContext.at(centre.getX() + 4, centre.getY()));
        Optional<Component> preview = tool.labelDragPreview();
        assertThat(tool.modifiersChanged(context, true, true, true)).isFalse();
        assertThat(tool.labelDragPreview()).isEqualTo(preview);
        assertThat(tool.movingFrom()).isEmpty();
        tool.mouseReleased(context, TestToolContext.withKeys(centre.getX() + 4, centre.getY(), true, true, true));
        assertThat(current(gate).position()).isEqualTo(gate.position());
        assertThat(current(gate).labelOffset()).isPresent();
        assertThat(context.openDocument().history().undoDescription()).contains("command.moveLabel");
    }
}
