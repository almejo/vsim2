package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/** The Terminal's pins (inputs only): D, 7 bits, on the left edge; CLK, WE and CLR on the bottom edge. */
final class TerminalPinout {

    static final int DATA_WIDTH = 7;
    /** D's row on the left edge, in grid units. */
    static final int DATA_ROW = 3;
    /** CLK, WE and CLR sit on the bottom edge at x = 1, 2 and 3. */
    static final int CLOCK_COLUMN = 1;
    static final int WRITE_ENABLE_COLUMN = 2;
    static final int CLEAR_COLUMN = 3;

    private TerminalPinout() {
    }

    static ComponentShape shape(GridSize size) {
        return new ComponentShape(size, List.of(
                PinSpec.input("D", 0, DATA_ROW, DATA_WIDTH),
                PinSpec.input("CLK", CLOCK_COLUMN, size.height()),
                PinSpec.input("WE", WRITE_ENABLE_COLUMN, size.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, size.height())));
    }
}
