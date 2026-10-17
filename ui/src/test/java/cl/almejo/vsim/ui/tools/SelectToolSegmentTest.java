package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * M6.7 §8: dragging a selected single wire with the segment key (Alt; Shift when Alt is the invert key) moves it
 * sideways only, and the wires joined to its ends stretch. A route of three wires: {@code left} (40, 0)-(40, 10),
 * {@code segment} (40, 10)-(50, 10), {@code right} (50, 10)-(50, 20).
 */
class SelectToolSegmentTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Wire left;
    private Wire segment;
    private Wire right;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        left = addWire(40, 0, 40, 10);
        segment = addWire(40, 10, 50, 10);
        right = addWire(50, 10, 50, 20);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Wire addWire(int startX, int startY, int endX, int endY) {
        Wire added = new Wire(context.openDocument().document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        context.openDocument().execute(new AddWireCommand(added));
        return added;
    }

    private List<Wire> wires() {
        return context.openDocument().document().wires();
    }

    private Wire current(Wire wire) {
        return context.openDocument().document().wire(wire.id()).orElseThrow();
    }

    private static Wire wireAt(Wire wire, int startX, int startY, int endX, int endY) {
        return new Wire(wire.id(), new GridPoint(startX, startY), new GridPoint(endX, endY));
    }

    @Test
    void aSegmentTheCleanUpMergesIntoAnEarlierWireLeavesTheSurvivorSelected() {
        Wire earlier = addWire(55, 13, 60, 13);
        Wire moved = addWire(60, 10, 65, 10);
        context.selection.select(moved.id());
        context.dragHolding(tool, 62, 10, 62, 13, false, true);
        assertThat(context.openDocument().document().wire(moved.id())).isEmpty();
        assertThat(current(earlier)).isEqualTo(wireAt(earlier, 55, 13, 65, 13));
        assertThat(context.selection.ids()).containsExactly(earlier.id());
    }

    @Test
    void aHorizontalWireDraggedWithAltMovesOnlyUpOrDownAndItsCornerNeighboursStretch() {
        context.selection.select(segment.id());
        context.dragHolding(tool, 45, 10, 47, 13, false, true);
        assertThat(wires()).containsExactly(wireAt(left, 40, 0, 40, 13), wireAt(segment, 40, 13, 50, 13),
                wireAt(right, 50, 13, 50, 20));
        assertThat(context.notices).isEmpty();

        context.openDocument().history().undo();
        assertThat(wires()).containsExactly(left, segment, right);
        context.openDocument().history().redo();
        assertThat(current(segment)).isEqualTo(wireAt(segment, 40, 13, 50, 13));
    }

    @Test
    void theCursorShowsTheDirectionTheSegmentSlides() {
        context.selection.select(segment.id());
        tool.mouseMoved(context, TestToolContext.withKeys(45, 10, false, false, false));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
        tool.mouseMoved(context, TestToolContext.withKeys(45, 10, false, false, true));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.SLIDE_VERTICAL);
        tool.mousePressed(context, TestToolContext.at(45, 10));
        tool.mouseDragged(context, TestToolContext.withKeys(47, 13, false, false, true));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.SLIDE_VERTICAL);
        tool.mouseDragged(context, TestToolContext.withKeys(47, 13, false, false, false));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.MOVE);
        tool.mouseReleased(context, TestToolContext.withKeys(47, 13, false, false, false));
        context.openDocument().history().undo();
        context.selection.select(left.id());
        tool.mouseMoved(context, TestToolContext.withKeys(40, 5, false, false, true));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.SLIDE_HORIZONTAL);
    }

    @Test
    void aVerticalWireDraggedWithAltMovesOnlySideways() {
        context.selection.select(left.id());
        context.dragHolding(tool, 40, 5, 37, 9, false, true);
        assertThat(current(left)).isEqualTo(wireAt(left, 37, 0, 37, 10));
        // the corner at (40, 10) is kept: the segment stretches to the moved end
        assertThat(wires()).extracting(Wire::start, Wire::end)
                .contains(tuple(new GridPoint(37, 10), new GridPoint(50, 10)));
    }

    @Test
    void anEndOnAComponentPinGetsABridgeAndThePinStaysConnected() {
        Component gate = place(context.openDocument(), StandardComponentTypes.NOT, 10, 10, Map.of());
        Component lamp = place(context.openDocument(), StandardComponentTypes.LED, 29, 9, Map.of());
        Wire driven = addWire(14, 12, 30, 12);
        context.selection.select(driven.id());
        context.dragHolding(tool, 20, 12, 23, 15, false, true);
        assertThat(current(driven)).isEqualTo(wireAt(driven, 14, 15, 30, 15));
        assertThat(pinNet(gate, new GridPoint(14, 12))).isPresent()
                .isEqualTo(pinNet(lamp, new GridPoint(30, 12)));

        context.openDocument().history().undo();
        assertThat(wires()).containsExactly(left, segment, right, driven);
    }

    @Test
    void withoutTheKeyTheWireMovesAsAnyMove() {
        context.selection.select(segment.id());
        context.drag(tool, 45, 10, 47, 13);
        List<Wire> dragged = wires();
        assertThat(current(segment)).isEqualTo(wireAt(segment, 42, 13, 52, 13));
        context.openDocument().history().undo();
        context.openDocument().execute(MoveWithWiresCommand.of(context.openDocument().document(),
                context.openDocument().registry(), Set.of(segment.id()), 2, 3));
        assertThat(wires()).isEqualTo(dragged);
    }

    @Test
    void theSegmentDragAlwaysKeepsTheConnectionsEvenWithTheModeOff() {
        context.preferences.setWiresFollow(false);
        context.selection.select(segment.id());
        context.dragHolding(tool, 45, 10, 45, 13, false, true);
        assertThat(wires()).containsExactly(wireAt(left, 40, 0, 40, 13), wireAt(segment, 40, 13, 50, 13),
                wireAt(right, 50, 13, 50, 20));
    }

    @Test
    void withAltAsTheInvertKeyShiftIsTheSegmentKeyAndAltIsNot() {
        context.preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        context.selection.select(segment.id());
        context.dragHolding(tool, 45, 10, 47, 13, true, false);
        assertThat(current(segment)).isEqualTo(wireAt(segment, 40, 13, 50, 13));
        assertThat(current(left)).isEqualTo(wireAt(left, 40, 0, 40, 13));
        context.openDocument().history().undo();

        // Alt now only inverts the mode: the wire moves both ways and leaves its neighbours behind
        context.dragHolding(tool, 45, 10, 47, 13, false, true);
        assertThat(wires()).containsExactly(left, wireAt(segment, 42, 13, 52, 13), right);
    }

    @Test
    void theKeysAtTheReleaseDecide() {
        context.selection.select(segment.id());
        tool.mousePressed(context, TestToolContext.at(45, 10));
        tool.mouseDragged(context, TestToolContext.withKeys(47, 13, false, false, true));
        tool.mouseReleased(context, TestToolContext.withKeys(47, 13, false, false, false));
        assertThat(current(segment)).isEqualTo(wireAt(segment, 42, 13, 52, 13));
    }

    @Test
    void onlyASingleSelectedWireIsASegment() {
        context.selection.set(Set.of(segment.id(), right.id()));
        context.dragHolding(tool, 45, 10, 47, 13, false, true);
        assertThat(current(segment)).isEqualTo(wireAt(segment, 42, 13, 52, 13));
        assertThat(current(right)).isEqualTo(wireAt(right, 52, 13, 52, 23));
    }

    @Test
    void altOnAnUnselectedWireStillStartsANewWire() {
        context.dragHolding(tool, 45, 10, 45, 16, false, true);
        assertThat(wires()).hasSize(4).contains(left, segment, right);
    }

    @Test
    void aLockedFrameStopsTheSegmentAtItsBorder() {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(35, 5), 20, 10,
                "Bloque", FrameTint.BLUE, true);
        context.openDocument().execute(new CreateFrameCommand(frame));
        context.selection.select(segment.id());
        context.dragHolding(tool, 45, 10, 45, 30, false, true);
        assertThat(current(segment)).isEqualTo(wireAt(segment, 40, 15, 50, 15));
    }

    @Test
    void thePreviewShowsTheSidewaysMoveDuringTheDragAndGoesAwayAfter() {
        context.selection.select(segment.id());
        tool.mousePressed(context, TestToolContext.at(45, 10));
        tool.mouseDragged(context, TestToolContext.withKeys(47, 13, false, false, true));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).contains(wireAt(segment, 40, 13, 50, 13), wireAt(left, 40, 0, 40, 13))
                .doesNotContain(wireAt(segment, 42, 13, 52, 13));
        assertThat(tool.hiddenWires(context)).contains(left.id(), right.id());

        tool.mouseReleased(context, TestToolContext.withKeys(47, 13, false, false, true));
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();
    }

    private Optional<Net> pinNet(Component component, GridPoint point) {
        Component placed = context.openDocument().document().component(component.id()).orElseThrow();
        ComponentType type = context.openDocument().registry().typeOf(placed);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (type.pinLocation(placed, pinIndex).equals(point)) {
                return context.openDocument().netlist().netForPin(new PinRef(placed.id(), pinIndex));
            }
        }
        throw new AssertionError("no pin of " + component.id() + " at " + point);
    }
}
