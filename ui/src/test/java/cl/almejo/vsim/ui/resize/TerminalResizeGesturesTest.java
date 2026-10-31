package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.ResizeComponentCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
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
 * Dragging the «⤡» tab of a selected Terminal changes its columns and rows (Task 7b): a Normal 80 × 24 Terminal at
 * (10, 10) is 82 × 52 units; its CLK pin at (12, 62) is wired down to (12, 70). The tab sits under the bottom-right
 * corner, centred at (91.75, 62.75) grid units.
 */
class TerminalResizeGesturesTest {

    private static final int CLOCK = 1;
    private static final int IMAGE_SIZE = 800;

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final ResizeGestures gestures = new ResizeGestures();
    private Component terminal;
    private Wire clockWire;

    TerminalResizeGesturesTest() {
        tool.addDelegate(gestures);
        useTerminal(Map.of());
    }

    /** Places the Terminal with {@code parameters} (and the CLK wire), replacing nothing: call once per test. */
    private void useTerminal(Map<String, Object> parameters) {
        terminal = place(context.openDocument(), StandardComponentTypes.TERMINAL, 10, 10, parameters);
        GridPoint clock = typeOf(terminal).pinLocation(terminal, CLOCK);
        clockWire = new Wire(context.openDocument().document().newWireId(), clock,
                new GridPoint(clock.x(), clock.y() + 8));
        context.openDocument().execute(new AddWireCommand(clockWire));
        context.openDocument().history().clear();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Component current() {
        return context.openDocument().document().component(terminal.id()).orElseThrow();
    }

    private int columns() {
        return (Integer) typeOf(current()).resolveParameters(current().parameters()).get(TerminalTypes.COLUMNS);
    }

    private int rows() {
        return (Integer) typeOf(current()).resolveParameters(current().parameters()).get(TerminalTypes.ROWS);
    }

    private ComponentType typeOf(Component component) {
        return context.openDocument().registry().typeOf(component);
    }

    private Point2D grip() {
        return ResizeGrip.centre(current(), typeOf(current()));
    }

    private void select() {
        context.selection.select(terminal.id());
    }

    private void dragGripBy(double deltaX, double deltaY) {
        Point2D grip = grip();
        context.drag(tool, grip.getX(), grip.getY(), grip.getX() + deltaX, grip.getY() + deltaY);
    }

    private boolean aWireEndsAt(GridPoint point) {
        return context.openDocument().document().wires().stream()
                .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
    }

    // --- the tab

    @Test
    void theTabSitsUnderTheBottomRightCorner() {
        assertThat(grip()).isEqualTo(new Point2D.Double(91.75, 62.75));
    }

    @Test
    void theTabIsThereOnlyWhileTheTerminalIsSelectedAndPointsDiagonally() {
        Point2D grip = grip();
        assertThat(gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY()))).isEmpty();

        select();
        assertThat(gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY())))
                .contains(CanvasCursor.RESIZE_SOUTH_EAST);
    }

    @Test
    void aTurnedTerminalShowsTheCursorOfItsTurnedCorner() {
        ComponentType type = context.openDocument().registry().require(StandardComponentTypes.TERMINAL);
        Component turned = type.newComponent(context.openDocument().document().newComponentId(),
                new GridPoint(200, 10), Rotation.R90, Map.of(), "");
        context.openDocument().execute(new AddElementsCommand(List.of(turned)));
        context.selection.select(turned.id());
        Point2D grip = ResizeGrip.centre(turned, typeOf(turned));

        assertThat(gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY())))
                .contains(CanvasCursor.RESIZE_SOUTH_WEST);
    }

    // --- the drag

    @Test
    void draggingTheCornerChangesColumnsAndRowsAndTheBottomPinsCarryTheirWires() {
        select();
        dragGripBy(-40, -24);

        assertThat(columns()).isEqualTo(40);
        assertThat(rows()).isEqualTo(12);
        assertThat(typeOf(current()).size().height()).isEqualTo(28);
        assertThat(typeOf(current()).pinLocation(current(), CLOCK)).isEqualTo(new GridPoint(12, 38));
        assertThat(aWireEndsAt(new GridPoint(12, 38))).isTrue();
        assertThat(aWireEndsAt(new GridPoint(12, 70))).isTrue();
        assertThat(aWireEndsAt(new GridPoint(12, 62))).isFalse();
        assertThat(context.openDocument().history().undoDescription()).contains("command.resizePart");

        dragGripBy(40, 24);
        assertThat(columns()).isEqualTo(80);
        assertThat(rows()).isEqualTo(24);
        assertThat(aWireEndsAt(new GridPoint(12, 62))).isTrue();
    }

    @Test
    void theDragSnapsToTheCellsOfASmallFont() {
        context.openDocument().execute(new RemoveElementsCommand(
                Set.of(terminal.id(), clockWire.id())));
        useTerminal(Map.of(TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_SMALL));
        select();

        dragGripBy(-30, -18);

        assertThat(columns()).isEqualTo(40);
        assertThat(rows()).isEqualTo(12);
    }

    @Test
    void theDragSnapsToTheCellsOfALargeFont() {
        context.openDocument().execute(new RemoveElementsCommand(
                Set.of(terminal.id(), clockWire.id())));
        useTerminal(Map.of(TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_LARGE));
        select();

        dragGripBy(-50, -30);

        assertThat(columns()).isEqualTo(40);
        assertThat(rows()).isEqualTo(12);
    }

    @Test
    void columnsAndRowsStayInsideTheirRanges() {
        select();
        dragGripBy(-500, -500);
        assertThat(columns()).isEqualTo(1);
        assertThat(rows()).isEqualTo(1);

        dragGripBy(1000, 1000);
        assertThat(columns()).isEqualTo(160);
        assertThat(rows()).isEqualTo(60);
    }

    @Test
    void oneUndoRestoresColumnsRowsAndTheWires() {
        List<Wire> wiresBefore = context.openDocument().document().wires();
        select();
        dragGripBy(-40, -24);

        context.openDocument().history().undo();

        assertThat(columns()).isEqualTo(80);
        assertThat(rows()).isEqualTo(24);
        assertThat(context.openDocument().document().wires()).isEqualTo(wiresBefore);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aDragWithinHalfACellRecordsNothing() {
        select();
        dragGripBy(0.4, 0.9);

        assertThat(columns()).isEqualTo(80);
        assertThat(rows()).isEqualTo(24);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void escapeWhileDraggingCancels() {
        select();
        Point2D grip = grip();
        tool.mousePressed(context, TestToolContext.at(grip.getX(), grip.getY()));
        tool.mouseDragged(context, TestToolContext.at(grip.getX() - 40, grip.getY() - 24));

        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        tool.mouseReleased(context, TestToolContext.at(grip.getX() - 40, grip.getY() - 24));

        assertThat(columns()).isEqualTo(80);
        assertThat(rows()).isEqualTo(24);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void whileDraggingThePartIsShownAtItsNewSizeWithTheDiagonalCursor() {
        select();
        Point2D grip = grip();
        tool.mousePressed(context, TestToolContext.at(grip.getX(), grip.getY()));
        tool.mouseDragged(context, TestToolContext.at(grip.getX() - 40, grip.getY() - 24));

        Map<ElementId, Component> previews = tool.previewedParts(context);
        assertThat(previews.get(terminal.id()).parameters()).containsEntry(TerminalTypes.COLUMNS, 40)
                .containsEntry(TerminalTypes.ROWS, 12);
        assertThat(tool.hiddenWires(context)).contains(clockWire.id());
        assertThat(gestures.pressCursor()).isEqualTo(CanvasCursor.RESIZE_SOUTH_EAST);
        assertThat(columns()).isEqualTo(80);

        tool.mouseReleased(context, TestToolContext.at(grip.getX() - 40, grip.getY() - 24));
        assertThat(tool.previewedParts(context)).isEmpty();
    }

    @Test
    void aDoubleClickOnTheTabGoesBackToEightyByTwentyFourInOneStep() {
        context.openDocument().execute(ResizeComponentCommand.of(context.openDocument().document(),
                context.openDocument().registry(), terminal.id(),
                Map.of(TerminalTypes.COLUMNS, 40, TerminalTypes.ROWS, 12)));
        context.openDocument().history().clear();
        select();
        Point2D grip = grip();

        tool.mousePressed(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 1, false));
        tool.mouseReleased(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 1, false));
        tool.mousePressed(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(grip.getX(), grip.getY(), CanvasMouseEvent.PRIMARY, 2, false));

        assertThat(columns()).isEqualTo(80);
        assertThat(rows()).isEqualTo(24);
        context.openDocument().history().undo();
        assertThat(columns()).isEqualTo(40);
        assertThat(rows()).isEqualTo(12);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    // --- the tag

    @Test
    void theTagSaysColumnsByRows() {
        ComponentType type = typeOf(current());
        assertThat(ResizeTag.text(type, Map.of(TerminalTypes.COLUMNS, 40, TerminalTypes.ROWS, 12)))
                .isEqualTo(Messages.format("resize.terminal.tag", 40, 12));
        Messages.initialize(Messages.SPANISH);
        assertThat(ResizeTag.text(type, Map.of())).isEqualTo("80×24");
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
                Map.of(), Map.of(), List.of(), Optional.empty());
        BufferedImage image = blank();
        Graphics2D graphics = image.createGraphics();
        renderer.paint(graphics, new RenderState(context.openDocument().document(), netlist,
                context.openDocument().document().revision(), snapshot, Set.of(terminal.id()), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), PaintPolicy.IMAGE));
        graphics.dispose();

        assertThat(image.getRGB(gripPixelX(), gripPixelY())).isEqualTo(ColorScheme.LIGHT.background().getRGB());
    }

    // --- the simulation

    @Test
    void theTextSurvivesAResize() {
        context.openDocument().execute(new RemoveElementsCommand(
                Set.of(terminal.id(), clockWire.id())));
        ComponentType type = context.openDocument().registry().require(StandardComponentTypes.TERMINAL);
        useTerminal(ParameterProfiles.apply(type, Map.of(), TerminalTypes.PROFILE_LOGISIM));
        // A push button whose output, at (12, 70), ends the CLK wire; D is left open, so each edge writes «?».
        Component button = place(context.openDocument(), StandardComponentTypes.PUSH_BUTTON, 8, 69, Map.of());
        awaitSimulation(context.openDocument());
        for (int press = 0; press < 2; press++) {
            context.openDocument().pressButton(button.id());
            awaitSimulation(context.openDocument());
            context.openDocument().releaseButton(button.id());
            awaitSimulation(context.openDocument());
        }
        assertThat(screenRow()).startsWith("??");

        select();
        dragGripBy(-40, -24);
        awaitSimulation(context.openDocument());

        assertThat(columns()).isEqualTo(40);
        assertThat(screenRow()).startsWith("??");
    }

    private String screenRow() {
        VisibleState state = context.openDocument().latestSnapshot().componentState(terminal.id().value());
        assertThat(state).isInstanceOf(VisibleState.TerminalState.class);
        return ((VisibleState.TerminalState) state).frame().rowText(0);
    }
}
