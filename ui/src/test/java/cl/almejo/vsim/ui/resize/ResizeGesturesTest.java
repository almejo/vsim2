package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.command.ResizeComponentCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dragging the «↔» tab of a selected Teclado changes its width (Task 6b): a Teclado at (10, 10), 16 units wide, its AV
 * pin at (26, 12) wired to (40, 12). The tab sits over the top-right corner, centred at (25.75, 9.25) grid units.
 */
class ResizeGesturesTest {

    private static final int AV = 3;
    private static final int IMAGE_SIZE = 600;

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final ResizeGestures gestures = new ResizeGestures();
    private final Component keyboard;
    private final Wire availableWire;

    ResizeGesturesTest() {
        tool.addDelegate(gestures);
        keyboard = place(context.openDocument(), StandardComponentTypes.KEYBOARD, 10, 10, Map.of());
        availableWire = new Wire(context.openDocument().document().newWireId(), new GridPoint(26, 12),
                new GridPoint(40, 12));
        context.openDocument().execute(new AddWireCommand(availableWire));
        context.openDocument().history().clear();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Component current() {
        return context.openDocument().document().component(keyboard.id()).orElseThrow();
    }

    private int width() {
        return (Integer) current().parameters().get(KeyboardTypes.PART_WIDTH);
    }

    private ComponentType typeOf(Component component) {
        return context.openDocument().registry().typeOf(component);
    }

    private Point2D grip() {
        return ResizeGrip.centre(current(), typeOf(current()));
    }

    private void select() {
        context.selection.select(keyboard.id());
    }

    private void dragGripBy(double units) {
        Point2D grip = grip();
        context.drag(tool, grip.getX(), grip.getY(), grip.getX() + units, grip.getY());
    }

    private Optional<CanvasCursor> cursorOverGrip() {
        Point2D grip = grip();
        return gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY()));
    }

    private boolean availableWireEndsAt(GridPoint point) {
        return context.openDocument().document().wires().stream()
                .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
    }

    // --- the tab

    @Test
    void theTabSitsOverTheTopRightCornerAsInTheMockup() {
        assertThat(grip()).isEqualTo(new Point2D.Double(25.75, 9.25));
    }

    @Test
    void theTabIsThereOnlyForAResizablePartSelectedAlone() {
        assertThat(cursorOverGrip()).isEmpty();

        select();
        assertThat(cursorOverGrip()).contains(CanvasCursor.RESIZE_EAST);

        Component buffer = place(context.openDocument(), StandardComponentTypes.BUFFER, 50, 50, Map.of());
        context.selection.add(buffer.id());
        assertThat(cursorOverGrip()).isEmpty();

        context.selection.select(buffer.id());
        assertThat(gestures.moved(context, TestToolContext.at(51, 49))).isEmpty();
    }

    @Test
    void anUnselectedTecladoIsNotResizedByADragOverItsCorner() {
        dragGripBy(8);

        assertThat(width()).isEqualTo(16);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    // --- the drag

    @Test
    void draggingTheTabWidensThePartAndAvAndDCarryTheirWires() {
        select();
        dragGripBy(8);

        assertThat(width()).isEqualTo(24);
        assertThat(typeOf(current()).size().width()).isEqualTo(24);
        assertThat(typeOf(current()).pinLocation(current(), AV)).isEqualTo(new GridPoint(34, 12));
        assertThat(availableWireEndsAt(new GridPoint(34, 12))).isTrue();
        assertThat(availableWireEndsAt(new GridPoint(26, 12))).isFalse();
        assertThat(context.openDocument().history().undoDescription()).contains("command.resizePart");
    }

    @Test
    void oneUndoRestoresTheWidthAndTheWires() {
        List<Wire> wiresBefore = context.openDocument().document().wires();
        select();
        dragGripBy(8);

        context.openDocument().history().undo();

        assertThat(width()).isEqualTo(16);
        assertThat(context.openDocument().document().wires()).isEqualTo(wiresBefore);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void theWidthStopsAtSixteenAndAtSixtyFour() {
        select();
        dragGripBy(-20);
        assertThat(width()).isEqualTo(16);
        assertThat(context.openDocument().history().canUndo()).isFalse();

        dragGripBy(200);
        assertThat(width()).isEqualTo(64);
    }

    @Test
    void aDragThatEndsAtTheSameWidthRecordsNothing() {
        select();
        dragGripBy(0.3);

        assertThat(width()).isEqualTo(16);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void escapeWhileDraggingCancels() {
        select();
        Point2D grip = grip();
        tool.mousePressed(context, TestToolContext.at(grip.getX(), grip.getY()));
        tool.mouseDragged(context, TestToolContext.at(grip.getX() + 8, grip.getY()));

        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        tool.mouseReleased(context, TestToolContext.at(grip.getX() + 8, grip.getY()));

        assertThat(width()).isEqualTo(16);
        assertThat(context.openDocument().history().canUndo()).isFalse();
        assertThat(context.selection.ids()).containsExactly(keyboard.id());
    }

    @Test
    void whileDraggingThePartIsShownAtItsNewWidthAndTheWiresThatMoveAreHidden() {
        select();
        Point2D grip = grip();
        tool.mousePressed(context, TestToolContext.at(grip.getX(), grip.getY()));
        tool.mouseDragged(context, TestToolContext.at(grip.getX() + 8, grip.getY()));

        Map<ElementId, Component> previews = tool.previewedParts(context);
        assertThat(previews.get(keyboard.id()).parameters()).containsEntry(KeyboardTypes.PART_WIDTH, 24);
        assertThat(tool.hiddenWires(context)).contains(availableWire.id());
        assertThat(width()).isEqualTo(16);
        assertThat(gestures.pressCursor()).isEqualTo(CanvasCursor.RESIZE_EAST);

        tool.mouseReleased(context, TestToolContext.at(grip.getX() + 8, grip.getY()));
        assertThat(tool.previewedParts(context)).isEmpty();
    }

    @Test
    void aDoubleClickOnTheTabGoesBackToTheMinimumInOneStep() {
        context.openDocument().execute(ResizeComponentCommand.of(context.openDocument().document(),
                context.openDocument().registry(), keyboard.id(), 30));
        context.openDocument().history().clear();
        select();
        Point2D grip = grip();

        tool.mousePressed(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 1, false));
        tool.mouseReleased(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 1, false));
        tool.mousePressed(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 2, false));

        assertThat(width()).isEqualTo(16);
        context.openDocument().history().undo();
        assertThat(width()).isEqualTo(30);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    // --- the tag

    @Test
    void theTagSaysTheWidthAndHowManyKeysShow() {
        ComponentType type = typeOf(current());
        assertThat(ResizeTag.text(type, Map.of(KeyboardTypes.PART_WIDTH, 24)))
                .isEqualTo(Messages.format("resize.keyboard.tag", 24, 13));
        assertThat(ResizeTag.text(type, Map.of())).isEqualTo(Messages.format("resize.keyboard.tag", 16, 8));
    }

    // --- painting

    private BufferedImage blank() {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        graphics.dispose();
        return image;
    }

    /** The tab's centre in document pixels. */
    private int gripPixelX() {
        return (int) Math.round(grip().getX() * GridPoint.PIXELS_PER_UNIT);
    }

    private int gripPixelY() {
        return (int) Math.round(grip().getY() * GridPoint.PIXELS_PER_UNIT);
    }

    @Test
    void theCanvasOverlayPaintsTheTabOnlyWhileThePartIsSelected() {
        BufferedImage unselected = blank();
        Graphics2D graphics = unselected.createGraphics();
        gestures.paintOverlay(graphics, context);
        graphics.dispose();
        assertThat(unselected.getRGB(gripPixelX(), gripPixelY())).isEqualTo(ColorScheme.LIGHT.background().getRGB());

        select();
        BufferedImage selected = blank();
        graphics = selected.createGraphics();
        gestures.paintOverlay(graphics, context);
        graphics.dispose();
        assertThat(selected.getRGB(gripPixelX(), gripPixelY())).isNotEqualTo(ColorScheme.LIGHT.background().getRGB());
    }

    @Test
    void anExportNeverShowsTheTabEvenWithThePartSelected() {
        CircuitRenderer renderer = new CircuitRenderer(context.openDocument().registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT);
        Netlist netlist = new NetlistCompiler(context.openDocument().registry())
                .compile(context.openDocument().document());
        SimulationSnapshot snapshot = new SimulationSnapshot(1, context.openDocument().document().revision(), 0, true,
                Map.of(), Map.of(keyboard.id().value(), new VisibleState.KeyboardState(1, List.of(), 0, 0)),
                List.of(), Optional.empty());
        BufferedImage image = blank();
        Graphics2D graphics = image.createGraphics();
        renderer.paint(graphics, new RenderState(context.openDocument().document(), netlist,
                context.openDocument().document().revision(), snapshot, Set.of(keyboard.id()), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), PaintPolicy.IMAGE));
        graphics.dispose();

        assertThat(image.getRGB(gripPixelX(), gripPixelY())).isEqualTo(ColorScheme.LIGHT.background().getRGB());
    }

    // --- the simulation

    @Test
    void theQueueSurvivesAWidthChange() throws Exception {
        awaitSimulation(context.openDocument());
        assertThat(context.openDocument().typeBytes(keyboard.id().value(), "hola".getBytes()).get()).isEqualTo(4);
        awaitSimulation(context.openDocument());

        select();
        dragGripBy(8);
        SimulationSnapshot snapshot = awaitSimulation(context.openDocument());

        assertThat(width()).isEqualTo(24);
        assertThat(snapshot.componentState(keyboard.id().value()))
                .isInstanceOfSatisfying(VisibleState.KeyboardState.class, state -> assertThat(state.size()).isEqualTo(4));
    }
}
