package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** NOT gates are 4x4 with pins at (0, 2) and (4, 2); a switch is 4x2 with its pin at (4, 1). */
class SelectToolCursorTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    private Component not(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.NOT, x, y, Map.of());
    }

    private void hover(double x, double y) {
        tool.mouseMoved(context, TestToolContext.at(x, y));
    }

    @Test
    void aPinShowsTheCrosshairAndEmptyCanvasTheArrow() {
        not(0, 0);
        hover(0, 2);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.CROSSHAIR);
        hover(20, 20);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.DEFAULT);
    }

    @Test
    void anUnselectedGateKeepsTheArrowAndASelectedOneShowsMove() {
        Component gate = not(0, 0);
        hover(0, 2);
        hover(2, 2);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.DEFAULT);
        context.selection.select(gate.id());
        hover(2, 2);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
    }

    @Test
    void aSwitchShowsTheHand() {
        place(context.openDocument(), StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        hover(1, 1);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.HAND);
    }

    @Test
    void anUnselectedWireStartsAWireAndASelectedOneMoves() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(10, 0),
                new GridPoint(20, 0));
        context.openDocument().execute(new AddWireCommand(wire));
        hover(15, 0);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.CROSSHAIR);
        context.selection.select(wire.id());
        hover(15, 0);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
    }

    @Test
    void aPinOfAMultiSelectionGrabsTheGroupAndShowsMove() {
        Component first = not(0, 0);
        Component second = not(10, 0);
        context.selection.select(first.id());
        hover(0, 2);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.CROSSHAIR);
        context.selection.toggle(second.id());
        hover(0, 2.1);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
    }

    @Test
    void aPressThatMovesShowsMoveAndARubberBandShowsTheArrow() {
        not(0, 0);
        tool.mousePressed(context, TestToolContext.at(2, 2));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
        tool.mouseReleased(context, TestToolContext.at(2, 2));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
        tool.mousePressed(context, TestToolContext.at(20, 20));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.DEFAULT);
    }

    @Test
    void aPressOnAPinDrawsAWireWithTheCrosshair() {
        not(0, 0);
        hover(20, 20);
        tool.mousePressed(context, TestToolContext.at(4, 2));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.CROSSHAIR);
    }

    @Test
    void activationForgetsTheLastCursor() {
        not(0, 0);
        hover(0, 2);
        tool.deactivated(context);
        tool.activated(context); // the canvas shows cursor() = the arrow again
        hover(0, 2);
        assertThat(context.cursors).containsExactly(CanvasCursor.CROSSHAIR, CanvasCursor.CROSSHAIR);
    }
}
