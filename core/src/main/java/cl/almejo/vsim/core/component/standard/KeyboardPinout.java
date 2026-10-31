package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/**
 * The Teclado's pins: CLK, RE and CLR (inputs) on the bottom edge at x = 2, 4 and 6; AV and D (outputs) on the right
 * edge at y = 2 and 4, so they move with that edge when the part is made wider («Ancho»). D is as wide as the data
 * width.
 */
final class KeyboardPinout {

    static final int CLOCK = 0;
    static final int READ_ENABLE = 1;
    static final int CLEAR = 2;
    static final int AVAILABLE = 3;
    static final int DATA = 4;

    static final int HEIGHT = 6;
    static final int CLOCK_COLUMN = 2;
    static final int READ_ENABLE_COLUMN = 4;
    static final int CLEAR_COLUMN = 6;
    static final int AVAILABLE_ROW = 2;
    static final int DATA_ROW = 4;

    private KeyboardPinout() {
    }

    /** The shape of a Teclado {@code width} grid units wide whose D has {@code dataWidth} bits. */
    static ComponentShape shape(int width, int dataWidth) {
        GridSize size = new GridSize(width, HEIGHT);
        return new ComponentShape(size, List.of(
                PinSpec.input("CLK", CLOCK_COLUMN, size.height()),
                PinSpec.input("RE", READ_ENABLE_COLUMN, size.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, size.height()),
                PinSpec.output("AV", size.width(), AVAILABLE_ROW),
                PinSpec.output("D", size.width(), DATA_ROW, dataWidth)));
    }
}
