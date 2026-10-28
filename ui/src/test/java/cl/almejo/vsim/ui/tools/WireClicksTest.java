package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.WireStretch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6.8: a double click on a wire selects its stretch, a triple click its whole net of wires. The route used: an L of
 * three wires (10,0)-(20,0), (20,0)-(20,10), (20,10)-(30,10) ending at a junction with two more wires, (30,10)-(40,10)
 * and (30,10)-(30,20).
 */
class WireClicksTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Wire first;
    private Wire middle;
    private Wire last;
    private Wire onward;
    private Wire down;

    @BeforeEach
    void create() {
        first = addWire(context.openDocument(), 10, 0, 20, 0);
        middle = addWire(context.openDocument(), 20, 0, 20, 10);
        last = addWire(context.openDocument(), 20, 10, 30, 10);
        onward = addWire(context.openDocument(), 30, 10, 40, 10);
        down = addWire(context.openDocument(), 30, 10, 30, 20);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private CircuitDocument document() {
        return context.openDocument().document();
    }

    private static CanvasMouseEvent press(double gridX, double gridY, int clickCount, boolean shiftDown) {
        return TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, clickCount, shiftDown);
    }

    /** The presses and releases of a click repeated {@code clicks} times at the same place. */
    private void clicks(double gridX, double gridY, int clicks, boolean shiftDown) {
        for (int clickCount = 1; clickCount <= clicks; clickCount++) {
            tool.mousePressed(context, press(gridX, gridY, clickCount, shiftDown));
            tool.mouseReleased(context, press(gridX, gridY, clickCount, shiftDown));
        }
    }

    @Test
    void aDoubleClickOnAWireSelectsItsStretchAndLeavesTheDocumentAsItWas() {
        long revision = document().revision();
        List<Wire> wires = document().wires();
        clicks(20.2, 5, 2, false);
        assertThat(context.selection.ids()).containsExactly(first.id(), middle.id(), last.id());
        assertThat(document().revision()).isEqualTo(revision);
        assertThat(document().wires()).isEqualTo(wires);
    }

    @Test
    void aTripleClickSelectsTheWholeNetOfWires() {
        clicks(20.2, 5, 3, false);
        assertThat(context.selection.ids())
                .containsExactlyInAnyOrder(first.id(), middle.id(), last.id(), onward.id(), down.id());
    }

    @Test
    void aShiftDoubleClickAddsTheStretchToTheSelection() {
        Component gate = place(context.openDocument(), StandardComponentTypes.BUFFER, 50, 50, Map.of(StandardComponentTypes.NEGATE, true));
        context.selection.select(gate.id());
        clicks(20.2, 5, 2, true);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(gate.id(), first.id(), middle.id(), last.id());
    }

    @Test
    void aPlainDoubleClickReplacesTheSelection() {
        Component gate = place(context.openDocument(), StandardComponentTypes.BUFFER, 50, 50, Map.of(StandardComponentTypes.NEGATE, true));
        context.selection.select(gate.id());
        clicks(20.2, 5, 2, false);
        assertThat(context.selection.ids()).containsExactly(first.id(), middle.id(), last.id());
    }

    @Test
    void theStretchIsDeletedInOneUndoStepAndUndoRestoresIdsAndOrder() {
        List<Wire> wires = document().wires();
        List<Long> orders = wires.stream().map(wire -> document().orderOf(wire.id())).toList();
        clicks(20.2, 5, 2, false);
        context.openDocument().execute(new RemoveElementsCommand(context.selection.effectiveIds()));
        assertThat(document().wires()).containsExactly(onward, down);
        context.openDocument().history().undo();
        assertThat(document().wires()).isEqualTo(wires);
        assertThat(wires.stream().map(wire -> document().orderOf(wire.id())).toList()).isEqualTo(orders);
    }

    @Test
    void aDragRightAfterTheDoubleClickPressMovesTheStretchAndTheAttachedWiresFollow() {
        tool.mousePressed(context, press(20.2, 5, 1, false));
        tool.mouseReleased(context, press(20.2, 5, 1, false));
        tool.mousePressed(context, press(20.2, 5, 2, false));
        tool.mouseDragged(context, press(20.2, 8, 2, false));
        tool.mouseReleased(context, press(20.2, 8, 2, false));

        assertThat(document().wire(middle.id())).contains(middle.translate(0, 3));
        assertThat(document().wire(first.id())).contains(first.translate(0, 3));
        // the clean-up after the move may merge the last wire with the following one, now collinear with it
        assertThat(document().wire(last.id()).orElseThrow())
                .matches(wire -> wire.contains(new GridPoint(20, 13)) && wire.contains(new GridPoint(30, 13)));
        assertThat(document().wires()).noneMatch(wire -> wire.start().equals(wire.end()));
        // the junction's wires still reach the moved end of the stretch
        assertThat(WireStretch.connected(document(), context.openDocument().registry(),
                middle.id()).stream().map(id -> document().wire(id).orElseThrow()))
                .anyMatch(wire -> wire.contains(new GridPoint(40, 13)))
                .anyMatch(wire -> wire.contains(new GridPoint(30, 20)));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
    }

    @Test
    void aDoubleClickPressNeverStartsAWireDrawing() {
        tool.mousePressed(context, press(20.2, 5, 1, false));
        tool.mouseReleased(context, press(20.2, 5, 1, false));
        tool.mousePressed(context, press(20.2, 5, 2, false));
        assertThat(tool.movingFrom()).contains(new GridPoint(20, 5));
        tool.mouseReleased(context, press(20.2, 5, 2, false));
        assertThat(document().wires()).hasSize(5);
    }

    @Test
    void aDoubleClickOnAWireInsideALockedFrameSelectsOnlyTheStretch() {
        Component gate = place(context.openDocument(), StandardComponentTypes.BUFFER, 12, 4, Map.of(StandardComponentTypes.NEGATE, true));
        Frame frame = new Frame(document().newFrameId(), new GridPoint(5, -5), 40, 30, "Bloque", FrameTint.BLUE,
                true);
        context.openDocument().execute(new CreateFrameCommand(frame));
        clicks(20.2, 5, 2, false);
        assertThat(context.selection.ids()).containsExactly(first.id(), middle.id(), last.id())
                .doesNotContain(gate.id(), frame.id());
    }
}
