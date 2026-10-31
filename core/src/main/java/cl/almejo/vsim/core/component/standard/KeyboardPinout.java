package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/**
 * The Teclado's pins: CLK, RE and CLR (inputs) on the bottom edge at x = 2, 4 and 6; AV and D (outputs) on the right
 * edge at y = 2 and 4. D is as wide as the data width.
 */
final class KeyboardPinout {

    static final int CLOCK = 0;
    static final int READ_ENABLE = 1;
    static final int CLEAR = 2;
    static final int AVAILABLE = 3;
    static final int DATA = 4;

    static final GridSize SIZE = new GridSize(16, 6);
    static final int CLOCK_COLUMN = 2;
    static final int READ_ENABLE_COLUMN = 4;
    static final int CLEAR_COLUMN = 6;
    static final int AVAILABLE_ROW = 2;
    static final int DATA_ROW = 4;

    private KeyboardPinout() {
    }

    static ComponentShape shape(int dataWidth) {
        return new ComponentShape(SIZE, List.of(
                PinSpec.input("CLK", CLOCK_COLUMN, SIZE.height()),
                PinSpec.input("RE", READ_ENABLE_COLUMN, SIZE.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, SIZE.height()),
                PinSpec.output("AV", SIZE.width(), AVAILABLE_ROW),
                PinSpec.output("D", SIZE.width(), DATA_ROW, dataWidth)));
    }
}
