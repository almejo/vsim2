package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The «⤡» tab of a selected UART 16550 with its integrated screen changes the screen's columns and rows like a
 * Terminal's (Task 7b): a Normal 80 × 24 UART at (10, 10) is 101 × 52 units, its CLK pin at (12, 62) wired down to
 * (12, 70). A UART with TX and RX pins has no screen, so no tab.
 */
class UartResizeGesturesTest {

    private static final int CLOCK = 5;

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final ResizeGestures gestures = new ResizeGestures();
    private Component uart;

    UartResizeGesturesTest() {
        tool.addDelegate(gestures);
    }

    private void useUart(String screen) {
        uart = place(context.openDocument(), StandardComponentTypes.UART_16550, 10, 10,
                Map.of(UartTypes.SCREEN, screen));
        GridPoint clock = typeOf(uart).pinLocation(uart, CLOCK);
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                clock, new GridPoint(clock.x(), clock.y() + 8))));
        context.openDocument().history().clear();
        context.selection.select(uart.id());
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Component current() {
        return context.openDocument().document().component(uart.id()).orElseThrow();
    }

    private Object parameter(String name) {
        return typeOf(current()).resolveParameters(current().parameters()).get(name);
    }

    private ComponentType typeOf(Component component) {
        return context.openDocument().registry().typeOf(component);
    }

    private boolean aWireEndsAt(GridPoint point) {
        return context.openDocument().document().wires().stream()
                .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
    }

    @Test
    void theIntegratedUartShowsTheCornerTab() {
        useUart(UartTypes.SCREEN_INTEGRATED);
        Point2D grip = ResizeGrip.centre(current(), typeOf(current()));

        assertThat(gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY())))
                .contains(CanvasCursor.RESIZE_SOUTH_EAST);
    }

    @Test
    void aUartWithPinsHasNoTab() {
        useUart(UartTypes.SCREEN_PINS);
        Point2D grip = ResizeGrip.centre(current(), typeOf(current()));

        assertThat(gestures.moved(context, TestToolContext.at(grip.getX(), grip.getY()))).isEmpty();
        context.drag(tool, grip.getX(), grip.getY(), grip.getX() - 40, grip.getY() - 24);
        assertThat(parameter(TerminalTypes.COLUMNS)).isEqualTo(80);
    }

    @Test
    void draggingTheCornerChangesTheScreenAndTheWiresFollowInOneUndoStep() {
        useUart(UartTypes.SCREEN_INTEGRATED);
        Point2D grip = ResizeGrip.centre(current(), typeOf(current()));

        context.drag(tool, grip.getX(), grip.getY(), grip.getX() - 40, grip.getY() - 24);

        assertThat(parameter(TerminalTypes.COLUMNS)).isEqualTo(40);
        assertThat(parameter(TerminalTypes.ROWS)).isEqualTo(12);
        assertThat(typeOf(current()).pinLocation(current(), CLOCK)).isEqualTo(new GridPoint(12, 38));
        assertThat(aWireEndsAt(new GridPoint(12, 38))).isTrue();
        assertThat(context.openDocument().history().undoDescription()).contains("command.resizePart");

        context.openDocument().history().undo();
        assertThat(parameter(TerminalTypes.COLUMNS)).isEqualTo(80);
        assertThat(aWireEndsAt(new GridPoint(12, 62))).isTrue();
    }

    @Test
    void theTagSaysColumnsByRows() {
        useUart(UartTypes.SCREEN_INTEGRATED);
        Messages.initialize(Messages.SPANISH);

        assertThat(ResizeTag.text(typeOf(current()), Map.of(TerminalTypes.COLUMNS, 40, TerminalTypes.ROWS, 12)))
                .isEqualTo("40×12");
    }
}
