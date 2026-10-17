package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * M6.7: a drag of the select tool makes the attached wires follow (follow mode XOR the invert key, read while
 * dragging and at the release), previews them, and reports new contacts. A NOT gate at (10, 10) (input (10, 12),
 * output (14, 12)) drives an LED whose pin is at (30, 12) through one horizontal wire.
 */
class SelectToolFollowTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Component gate;
    private Component lamp;
    private Wire wire;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        gate = place(context.openDocument(), StandardComponentTypes.NOT, 10, 10, Map.of());
        lamp = place(context.openDocument(), StandardComponentTypes.LED, 29, 9, Map.of());
        wire = addWire(14, 12, 30, 12);
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

    private GridPoint positionOf(Component component) {
        return context.openDocument().document().component(component.id()).orElseThrow().position();
    }

    private Optional<Net> netOfPinAt(Component component, GridPoint point) {
        Component current = context.openDocument().document().component(component.id()).orElseThrow();
        ComponentType type = context.openDocument().registry().typeOf(current);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (type.pinLocation(current, pinIndex).equals(point)) {
                return context.openDocument().netlist().netForPin(new PinRef(current.id(), pinIndex));
            }
        }
        throw new AssertionError("no pin of " + component.id() + " at " + point);
    }

    private void assertLampDrivenByGate(GridPoint gateOutput) {
        Optional<Net> gateNet = netOfPinAt(gate, gateOutput);
        assertThat(gateNet).isPresent();
        assertThat(netOfPinAt(lamp, new GridPoint(30, 12))).isEqualTo(gateNet);
    }

    @Test
    void draggingAGateDragsItsWireAndTheLampStaysConnected() {
        context.drag(tool, 12, 12, 12, 9);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 7));
        // the LED pin is a fixed end, so the wire gets an L: it keeps leaving the LED horizontally
        assertThat(context.openDocument().document().wires()).extracting(Wire::start, Wire::end)
                .containsExactlyInAnyOrder(tuple(new GridPoint(14, 12), new GridPoint(30, 12)),
                        tuple(new GridPoint(14, 9), new GridPoint(14, 12)));
        assertLampDrivenByGate(new GridPoint(14, 9));
        context.openDocument().history().undo();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 10));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
    }

    @Test
    void aDraggedWireTheCleanUpMergesIntoAnEarlierWireLeavesTheSurvivorSelected() {
        Wire earlier = addWire(40, 20, 44, 20);
        Wire moved = addWire(44, 23, 49, 23);
        context.selection.set(java.util.List.of(gate.id(), moved.id()));
        context.drag(tool, 46, 23, 46, 20);
        assertThat(context.openDocument().document().wire(moved.id())).isEmpty();
        assertThat(context.openDocument().document().wire(earlier.id())).contains(
                new Wire(earlier.id(), new GridPoint(40, 20), new GridPoint(49, 20)));
        assertThat(context.selection.ids()).containsExactly(gate.id(), earlier.id());
    }

    @Test
    void shiftHeldDuringTheDragLeavesTheWireBehind() {
        context.dragHolding(tool, 12, 12, 12, 9, true, false);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 7));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
    }

    @Test
    void theKeysAtTheReleaseDecide() {
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.withKeys(12, 9, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(12, 9, false, false, false));
        assertThat(context.openDocument().document().wires()).hasSize(2);
        assertLampDrivenByGate(new GridPoint(14, 9));
    }

    @Test
    void shiftHeldFromThePressAddsTheElementAndMovesTheSelectionWithoutTheWires() {
        context.selection.select(lamp.id());
        tool.mousePressed(context, TestToolContext.withKeys(12, 12, true, false, false));
        tool.mouseDragged(context, TestToolContext.withKeys(12, 9, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(12, 9, true, false, false));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(lamp.id(), gate.id());
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 7));
        assertThat(positionOf(lamp)).isEqualTo(new GridPoint(29, 6));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
    }

    @Test
    void shiftClickStillTogglesTheSelectionAndMovesNothing() {
        context.selection.select(lamp.id());
        tool.mousePressed(context, TestToolContext.withKeys(12, 12, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(12, 12, true, false, false));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(lamp.id(), gate.id());
        tool.mousePressed(context, TestToolContext.withKeys(12, 12, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(12, 12, true, false, false));
        assertThat(context.selection.ids()).containsExactly(lamp.id());
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 10));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
    }

    @Test
    void withTheModeOffNoWireFollowsAndShiftMakesThemFollow() {
        context.preferences.setWiresFollow(false);
        context.drag(tool, 12, 12, 12, 9);
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
        context.openDocument().history().undo();

        context.dragHolding(tool, 12, 12, 12, 9, true, false);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 7));
        assertLampDrivenByGate(new GridPoint(14, 9));
    }

    @Test
    void withAltAsTheInvertKeyAltInvertsAndShiftDoesNot() {
        context.preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        context.dragHolding(tool, 12, 12, 12, 9, false, true);
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
        context.openDocument().history().undo();

        context.dragHolding(tool, 12, 12, 12, 9, true, false);
        assertLampDrivenByGate(new GridPoint(14, 9));
    }

    @Test
    void controlAtTheReleaseStillCopiesWithoutDraggingWires() {
        context.selection.select(gate.id());
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.withKeys(12, 20, false, true, false));
        tool.mouseReleased(context, TestToolContext.withKeys(12, 20, false, true, false));
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 10));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
        assertThat(context.openDocument().document().components()).hasSize(3);
    }

    @Test
    void whileControlIsHeldThePreviewOfTheCopyDragsNoWires() {
        context.selection.select(gate.id());
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.withKeys(14, 12, false, true, false));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).filteredOn(element -> element instanceof Wire).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();

        context.ghosts.clear();
        tool.mouseDragged(context, TestToolContext.withKeys(14, 12, false, false, false));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(tool.hiddenWires(context)).containsExactly(wire.id());
        tool.mouseReleased(context, TestToolContext.at(14, 12));
    }

    @Test
    void theDragPreviewShowsTheStretchedWireAndGoesAwayAfterTheDrop() {
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.at(14, 12));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).contains(new Wire(wire.id(), new GridPoint(16, 12), new GridPoint(30, 12)));
        assertThat(context.ghosts).filteredOn(element -> element instanceof Component)
                .extracting(DocumentElement::id).containsExactly(gate.id());
        // the canvas leaves the stretched wire out, so it is not drawn twice (solid here, translucent there)
        assertThat(tool.hiddenWires(context)).containsExactly(wire.id());

        context.ghosts.clear();
        tool.mouseDragged(context, TestToolContext.withKeys(14, 12, true, false, false));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).filteredOn(element -> element instanceof Wire).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();

        tool.mouseReleased(context, TestToolContext.at(14, 12));
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();
    }

    @Test
    void aNewContactIsARedDotDuringTheDragAndANoticeAfterTheDrop() {
        Wire crossing = addWire(10, 15, 10, 25);
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.at(12, 20));
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        tool.paintOverlay(graphics, context);
        graphics.dispose();
        Color center = new Color(image.getRGB(10 * GridPoint.PIXELS_PER_UNIT, 20 * GridPoint.PIXELS_PER_UNIT), true);
        assertThat(center).isEqualTo(ColorScheme.LIGHT.wireConflict());

        tool.mouseReleased(context, TestToolContext.at(12, 20));
        assertThat(context.openDocument().document().wires()).contains(crossing);
        assertThat(context.notices).containsExactly("El cable quedó tocando el pin «in» de «NOT»");
    }

    @Test
    void severalNewContactsAreCounted() {
        addWire(5, 20, 40, 20);
        context.drag(tool, 12, 12, 12, 20);
        assertThat(context.notices).containsExactly("El movimiento dejó 2 contactos nuevos");
    }

    @Test
    void theFirstMoveThatDragsWiresHintsHowToDisconnectOnce() {
        context.drag(tool, 12, 12, 12, 9);
        context.drag(tool, 12, 9, 12, 6);
        assertThat(context.notices).containsExactly("Los cables siguieron al mover. Mantén Mayús para desconectar");
    }

    // --- M6.8: the preview follows a modifier key pressed or released without moving the mouse

    @Test
    void shiftPressedAndReleasedMidDragTogglesThePreviewWithoutMovingTheMouse() {
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.at(14, 12));
        assertThat(tool.hiddenWires(context)).containsExactly(wire.id());

        assertThat(tool.modifiersChanged(context, true, false, false)).isTrue();
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).filteredOn(element -> element instanceof Wire).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();

        assertThat(tool.modifiersChanged(context, false, false, false)).isTrue();
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).contains(new Wire(wire.id(), new GridPoint(16, 12), new GridPoint(30, 12)));
        assertThat(tool.hiddenWires(context)).containsExactly(wire.id());
        tool.mouseReleased(context, TestToolContext.at(14, 12));
    }

    @Test
    void controlPressedMidDragShowsTheCopyPreview() {
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.at(14, 12));

        assertThat(tool.modifiersChanged(context, false, true, false)).isTrue();
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).filteredOn(element -> element instanceof Wire).isEmpty();
        assertThat(tool.hiddenWires(context)).isEmpty();
        tool.mouseReleased(context, TestToolContext.withKeys(14, 12, false, true, false));
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 10));
    }

    @Test
    void withoutADragAModifierChangeDoesNothing() {
        assertThat(tool.modifiersChanged(context, true, false, false)).isFalse();
        tool.mousePressed(context, TestToolContext.at(12, 12));
        assertThat(tool.modifiersChanged(context, true, false, false)).isFalse();
        assertThat(tool.hiddenWires(context)).isEmpty();
        tool.mouseReleased(context, TestToolContext.at(12, 12));
        assertThat(tool.modifiersChanged(context, false, false, false)).isFalse();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 10));
        assertThat(context.openDocument().document().wires()).containsExactly(wire);
    }
}
