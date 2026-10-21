package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.wiring.SelectionMoves;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contacts shown while dragging (alignment guides spec §3.7): every gesture that moves or places parts shows a dot
 * where the move would join two nets that are not joined today. A NOT gate at (10, 10) (input (10, 12), output
 * (14, 12)) drives an LED (pin (30, 12)) through one wire; a vertical wire of another net runs from (10, 15) to
 * (10, 25).
 */
class DragContactsTest {

    private static final GridPoint ON_THE_CROSSING_WIRE = new GridPoint(10, 20);

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Component gate;
    private Component lamp;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        gate = place(context.openDocument(), StandardComponentTypes.NOT, 10, 10, Map.of());
        lamp = place(context.openDocument(), StandardComponentTypes.LED, 29, 9, Map.of());
        addWire(14, 12, 30, 12);
        addWire(10, 15, 10, 25);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void addWire(int startX, int startY, int endX, int endY) {
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                new GridPoint(startX, startY), new GridPoint(endX, endY))));
    }

    private static List<GridPoint> points(List<NewContact> contacts) {
        return contacts.stream().map(NewContact::point).toList();
    }

    private List<NewContact> dragGateTo(double x, double y, boolean follow) {
        context.preferences.setWiresFollow(follow);
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.at(x, y));
        return tool.contacts(context);
    }

    private static Color colourAt(BufferedImage image, GridPoint point) {
        return new Color(image.getRGB(point.x() * GridPoint.PIXELS_PER_UNIT, point.y() * GridPoint.PIXELS_PER_UNIT),
                true);
    }

    private BufferedImage paintOverlay(Tool painted) {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        painted.paintOverlay(graphics, context);
        graphics.dispose();
        return image;
    }

    @Test
    void withTheWiresLeftBehindAPinDraggedOntoAnotherNetsWireShowsOneNamedDot() {
        List<NewContact> contacts = dragGateTo(12, 20, false);

        assertThat(contacts).containsExactly(new NewContact(ON_THE_CROSSING_WIRE, Optional.of(gate.id()),
                Optional.of("in")));
        assertThat(colourAt(paintOverlay(tool), ON_THE_CROSSING_WIRE)).isEqualTo(ColorScheme.LIGHT.wireConflict());
    }

    @Test
    void aPinDraggedOntoItsOwnNetsWireShowsNothing() {
        context.preferences.setWiresFollow(false);
        context.selection.select(lamp.id());
        tool.mousePressed(context, TestToolContext.at(30, 10));
        tool.mouseDragged(context, TestToolContext.at(26, 10)); // the LED pin onto the interior of its own wire

        assertThat(tool.contacts(context)).isEmpty();
    }

    @Test
    void followOnAndFollowOffShowTheSameContactsOfTheMovedParts() {
        List<NewContact> leftBehind = dragGateTo(12, 20, false);
        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false); // the drag is dropped: nothing moved
        List<NewContact> following = dragGateTo(12, 20, true);

        assertThat(following).isEqualTo(leftBehind);
    }

    @Test
    void whenTheWiresFollowTheContactsTheirNewPiecesMakeAreShownToo() {
        // wire ends of other nets on both routes the output wire could take: it runs down from (14, 12) over one
        addWire(22, 20, 22, 30);
        addWire(5, 16, 14, 16);
        List<NewContact> leftBehind = dragGateTo(12, 20, false);
        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        List<NewContact> following = dragGateTo(12, 20, true);
        List<NewContact> followPlan = SelectionMoves.preview(context.openDocument(), Set.of(gate.id()), 0, 8)
                .newContacts();

        assertThat(points(followPlan)).contains(new GridPoint(14, 16));
        assertThat(points(leftBehind)).doesNotContain(new GridPoint(14, 16));
        assertThat(points(following)).containsAll(points(leftBehind)).containsAll(points(followPlan))
                .doesNotHaveDuplicates().isSorted();
    }

    @Test
    void aCtrlCopyTouchesItsOriginalWhileTheSameMoveDoesNot() {
        context.preferences.setWiresFollow(false);
        context.selection.select(gate.id());
        tool.mousePressed(context, TestToolContext.at(12, 12));
        tool.mouseDragged(context, TestToolContext.withKeys(16, 12, false, true, false));
        assertThat(points(tool.contacts(context))).containsExactly(new GridPoint(14, 12), new GridPoint(18, 12));

        tool.mouseDragged(context, TestToolContext.withKeys(16, 12, false, false, false));
        assertThat(points(tool.contacts(context))).containsExactly(new GridPoint(14, 12));
    }

    @Test
    void theDotFollowsASchemeChangeDuringTheDrag() {
        dragGateTo(12, 20, false);
        assertThat(colourAt(paintOverlay(tool), ON_THE_CROSSING_WIRE)).isEqualTo(ColorScheme.LIGHT.wireConflict());

        context.scheme = ColorSchemes.DARK;
        assertThat(colourAt(paintOverlay(tool), ON_THE_CROSSING_WIRE)).isEqualTo(ColorSchemes.DARK.wireConflict());
    }

    @Test
    void theReleaseCommitsExactlyTheMoveWithNoExtraStepOrNotice() {
        dragGateTo(12, 20, false);
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.mouseReleased(context, TestToolContext.at(12, 20));

        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(10, 18));
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(10, 10));
        assertThat(context.openDocument().document().wires()).hasSize(2);
        assertThat(context.notices).isEmpty();
        assertThat(tool.contacts(context)).isEmpty();
    }

    @Test
    void aFloatingPasteShowsTheContactsOfItsGhost() {
        CircuitFragment fragment = new CircuitFragment(List.of(context.openDocument().registry()
                .require(StandardComponentTypes.NOT)
                .newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "")), List.of(),
                List.of());
        FloatingSelectionTool floating = new FloatingSelectionTool(fragment, new GridPoint(40, 40));
        context.setTool(floating);
        assertThat(floating.contacts(context)).isEmpty();

        floating.mouseMoved(context, TestToolContext.at(10, 18));
        assertThat(floating.contacts(context)).containsExactly(new NewContact(ON_THE_CROSSING_WIRE,
                Optional.of(new ElementId("c1")), Optional.of("in")));
        assertThat(colourAt(paintOverlay(floating), ON_THE_CROSSING_WIRE))
                .isEqualTo(ColorScheme.LIGHT.wireConflict());
    }

    @Test
    void placingAComponentShowsTheContactsOfItsGhost() {
        PlaceComponentTool placing = new PlaceComponentTool(context.openDocument().registry()
                .require(StandardComponentTypes.NOT));
        placing.mouseMoved(context, TestToolContext.at(10, 18));

        assertThat(points(placing.contacts(context))).containsExactly(ON_THE_CROSSING_WIRE);
        assertThat(colourAt(paintOverlay(placing), ON_THE_CROSSING_WIRE))
                .isEqualTo(ColorScheme.LIGHT.wireConflict());

        placing.mouseMoved(context, TestToolContext.at(40, 40));
        assertThat(placing.contacts(context)).isEmpty();
    }
}
