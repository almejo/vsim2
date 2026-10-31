package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/**
 * The Terminal's pins (inputs only, decision 11): D, as wide as «Ancho del dato», on the left edge; CLK, WE and CLR on
 * the bottom edge.
 */
final class TerminalPinout {

    static final int DATA = 0;
    static final int CLOCK = 1;
    static final int WRITE_ENABLE = 2;
    static final int CLEAR = 3;
    /** D's row on the left edge, in grid units. */
    static final int DATA_ROW = 3;
    /** CLK, WE and CLR sit on the bottom edge at x = 2, 4 and 6, two units apart so their names fit. */
    static final int CLOCK_COLUMN = 2;
    static final int WRITE_ENABLE_COLUMN = 4;
    static final int CLEAR_COLUMN = 6;

    private TerminalPinout() {
    }

    static ComponentShape shape(GridSize size, int dataWidth) {
        return new ComponentShape(size, List.of(
                PinSpec.input("D", 0, DATA_ROW, dataWidth),
                PinSpec.input("CLK", CLOCK_COLUMN, size.height()),
                PinSpec.input("WE", WRITE_ENABLE_COLUMN, size.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, size.height())));
    }
}
