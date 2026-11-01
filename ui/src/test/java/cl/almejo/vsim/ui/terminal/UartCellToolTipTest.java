package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The cells of a UART 16550's integrated screen on the canvas, with the Terminal's tooltip (spec §8.4). */
class UartCellToolTipTest {

    private static final VisibleState.UartRegisters REGISTERS = new VisibleState.UartRegisters(-1, -1, 0, 1, 0, 0,
            0, 0x60, 0xB0, 0, 0, 0);

    private OpenDocument document;
    private ComponentType type;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        type = document.registry().require(StandardComponentTypes.UART_16550);
    }

    @AfterEach
    void close() {
        document.close();
    }

    private Component place(String screen) {
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(10, 20),
                Rotation.R0, Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20, UartTypes.SCREEN, screen),
                "uart0");
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    /** The middle of a Normal cell (8 × 16 px): the screen starts right of the register table, one unit below the bar. */
    private static Point2D middleOf(int row, int column) {
        double localX = UartTypes.SCREEN_LEFT_UNITS * 8 + column * 8 + 4;
        double localY = (UartTypes.TITLE_BAR_UNITS + UartTypes.MARGIN_UNITS) * 8 + row * 16 + 8;
        return new Point2D.Double(10 + localX / 8, 20 + localY / 8);
    }

    private SimulationSnapshot snapshotOf(Component uart, Optional<VisibleState.TerminalState> screen) {
        return new SimulationSnapshot(1, 1, 50, false, Map.of(),
                Map.of(uart.id().value(), new VisibleState.UartState(REGISTERS, 0, screen)), List.of(),
                Optional.empty());
    }

    @Test
    void theIntegratedScreenStartsRightOfTheRegisterTable() {
        Component uart = place(UartTypes.SCREEN_INTEGRATED);
        assertThat(TerminalScreenArea.cellAt(type, uart, middleOf(0, 0))).contains(new CellPosition(0, 0));
        assertThat(TerminalScreenArea.cellAt(type, uart, middleOf(3, 19))).contains(new CellPosition(3, 19));
        assertThat(TerminalScreenArea.cellAt(type, uart, new Point2D.Double(14, 24))).as("the register table")
                .isEmpty();
    }

    @Test
    void aUartWithPinsHasNoScreenCells() {
        Component uart = place(UartTypes.SCREEN_PINS);
        assertThat(TerminalScreenArea.cellAt(type, uart, middleOf(0, 0))).isEmpty();
    }

    @Test
    void aWrittenCellOfTheIntegratedScreenHasTheTerminalsTooltip() {
        Component uart = place(UartTypes.SCREEN_INTEGRATED);
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        screen.write('h', 40);
        screen.write('i', 50);
        SimulationSnapshot snapshot = snapshotOf(uart, Optional.of(new VisibleState.TerminalState(1,
                screen.version(), screen.frame(), false)));

        assertThat(TerminalCellToolTip.at(document.document(), document.registry(), snapshot, middleOf(0, 1)))
                .contains("'i'  U+0069  ·  bytes 69  ·  llegó en t = 50  ·  fila 1, col 2");
        assertThat(TerminalCellToolTip.at(document.document(), document.registry(), snapshot, middleOf(0, 2)))
                .as("a blank cell").isEmpty();
    }
}
