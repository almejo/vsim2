package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.terminal.TerminalFontSize;

import java.util.ArrayList;
import java.util.List;

/**
 * The UART 16550's pins and box (spec §12, mockup «uart0»). The CPU bus comes first: A[3], D[8] and CS on the left
 * edge, Q[8] and IRQ on the right one, CLK, RD, WR and CLR on the bottom one. With «Pantalla: Pines TX y RX» the line
 * side follows: RX[8] and RXV on the left edge below CS, TX[8], TXV and RXR («receptor listo») on the right edge
 * below IRQ.
 *
 * <p>The box: a title bar, then a strip for the left pins' names, the register table and a strip for the right pins'
 * names; with the integrated screen, the screen sits between the table and the right strip, as big as a Terminal's of
 * the same rows, columns and «Tamaño de letra» (the sizes are {@link UartTypes}' constants). Only the pins on the
 * right and bottom edges move when the screen grows.
 */
final class UartPinout {

    static final int ADDRESS = 0;
    static final int DATA = 1;
    static final int CHIP_SELECT = 2;
    static final int READ = 3;
    static final int WRITE = 4;
    static final int CLOCK = 5;
    static final int CLEAR = 6;
    static final int OUTPUT = 7;
    static final int INTERRUPT = 8;
    static final int RECEIVE = 9;
    static final int RECEIVE_VALID = 10;
    static final int TRANSMIT = 11;
    static final int TRANSMIT_VALID = 12;
    static final int RECEIVER_READY = 13;

    /** A has three bits: the eight registers of a 16550. */
    static final int ADDRESS_WIDTH = 3;
    static final int BYTE_WIDTH = 8;

    private static final int TITLE_BAR_UNITS = UartTypes.TITLE_BAR_UNITS;
    private static final int SCREEN_LEFT_UNITS = UartTypes.SCREEN_LEFT_UNITS;
    private static final int RIGHT_PANEL_UNITS = UartTypes.RIGHT_PANEL_UNITS;
    private static final int MARGIN_UNITS = UartTypes.MARGIN_UNITS;
    /** The height without a screen: the title, the register rows and the bottom pins' names. */
    static final int MINIMUM_HEIGHT = 14;

    private static final int FIRST_ROW = 4;
    private static final int ROW_STEP = 2;
    private static final int CLOCK_COLUMN = 2;
    private static final int READ_COLUMN = 4;
    private static final int WRITE_COLUMN = 6;
    private static final int CLEAR_COLUMN = 8;

    private UartPinout() {
    }

    /** The UART's size, with a screen of {@code rows} × {@code columns} cells of {@code fontSize} when integrated. */
    static GridSize size(boolean integrated, int rows, int columns, TerminalFontSize fontSize) {
        if (!integrated) {
            return new GridSize(SCREEN_LEFT_UNITS + RIGHT_PANEL_UNITS, MINIMUM_HEIGHT);
        }
        int screenWidth = unitsFor(columns * fontSize.cellWidth());
        int screenHeight = unitsFor(rows * fontSize.cellHeight());
        return new GridSize(SCREEN_LEFT_UNITS + screenWidth + RIGHT_PANEL_UNITS,
                Math.max(MINIMUM_HEIGHT, TITLE_BAR_UNITS + screenHeight + 2 * MARGIN_UNITS));
    }

    static ComponentShape shape(GridSize size, boolean integrated) {
        List<PinSpec> pins = new ArrayList<>(List.of(
                PinSpec.input("A", 0, row(0), ADDRESS_WIDTH),
                PinSpec.input("D", 0, row(1), BYTE_WIDTH),
                PinSpec.input("CS", 0, row(2)),
                PinSpec.input("RD", READ_COLUMN, size.height()),
                PinSpec.input("WR", WRITE_COLUMN, size.height()),
                PinSpec.input("CLK", CLOCK_COLUMN, size.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, size.height()),
                PinSpec.output("Q", size.width(), row(0), BYTE_WIDTH),
                PinSpec.output("IRQ", size.width(), row(1))));
        if (!integrated) {
            pins.add(PinSpec.input("RX", 0, row(3), BYTE_WIDTH));
            pins.add(PinSpec.input("RXV", 0, row(4)));
            pins.add(PinSpec.output("TX", size.width(), row(2), BYTE_WIDTH));
            pins.add(PinSpec.output("TXV", size.width(), row(3)));
            pins.add(PinSpec.output("RXR", size.width(), row(4)));
        }
        return new ComponentShape(size, pins);
    }

    private static int row(int index) {
        return FIRST_ROW + index * ROW_STEP;
    }

    private static int unitsFor(int pixels) {
        return (pixels + GridPoint.PIXELS_PER_UNIT - 1) / GridPoint.PIXELS_PER_UNIT;
    }
}
