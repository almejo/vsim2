package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.SetLabelOffsetCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
}
