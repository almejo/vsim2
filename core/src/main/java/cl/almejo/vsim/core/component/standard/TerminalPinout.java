package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;

/**
 * The Terminal's pins (decision 11): D, as wide as «Ancho del dato», on the left edge; CLK, WE and CLR on the bottom
 * edge; with an external character ROM (spec §8.8) also CODE (8 bits out), ROW (3 bits out) and PIX (8 bits in) on the
 * right edge.
 */
final class TerminalPinout {

    static final int DATA = 0;
    static final int CLOCK = 1;
    static final int WRITE_ENABLE = 2;
    static final int CLEAR = 3;
    /** The code whose glyph is being read, to the ROM: only with «Generador de caracteres: Memoria externa». */
    static final int CODE = 4;
    /** The glyph row being read, 0 (top) to 7. */
    static final int ROW = 5;
    /** The ROM's answer: the eight pixels of that row, bit 7 the leftmost. */
    static final int PIX = 6;
    static final int CODE_WIDTH = 8;
    static final int ROW_WIDTH = 3;
    static final int PIX_WIDTH = 8;
    /** D's row on the left edge, in grid units. */
    static final int DATA_ROW = 3;
    /** CLK, WE and CLR sit on the bottom edge at x = 2, 4 and 6, two units apart so their names fit. */
    static final int CLOCK_COLUMN = 2;
    static final int WRITE_ENABLE_COLUMN = 4;
    static final int CLEAR_COLUMN = 6;
    /** CODE, ROW and PIX sit on the right edge at y = 3, 5 and 7, two units apart like the bottom ones. */
    static final int CODE_ROW = 3;
    static final int ROW_ROW = 5;
    static final int PIX_ROW = 7;

    private TerminalPinout() {
    }

    static ComponentShape shape(GridSize size, int dataWidth, boolean characterRom) {
        List<PinSpec> pins = new ArrayList<>(List.of(
                PinSpec.input("D", 0, DATA_ROW, dataWidth),
                PinSpec.input("CLK", CLOCK_COLUMN, size.height()),
                PinSpec.input("WE", WRITE_ENABLE_COLUMN, size.height()),
                PinSpec.input("CLR", CLEAR_COLUMN, size.height())));
        if (characterRom) {
            pins.add(PinSpec.output("CODE", size.width(), CODE_ROW, CODE_WIDTH));
            pins.add(PinSpec.output("ROW", size.width(), ROW_ROW, ROW_WIDTH));
            pins.add(PinSpec.input("PIX", size.width(), PIX_ROW, PIX_WIDTH));
        }
        return new ComponentShape(size, pins);
    }
}
