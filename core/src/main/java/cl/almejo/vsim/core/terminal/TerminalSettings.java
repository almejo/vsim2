package cl.almejo.vsim.core.terminal;

import java.util.Objects;

/**
 * Everything that decides how a Terminal turns bytes into a screen (spec §8, the «Pantalla», «Datos» and
 * «Interpretación» groups). Look properties (colours, font, cursor) are not here: they never touch the model.
 *
 * @param mode            Logisim, VT100-xterm or Teletipo
 * @param rows            «Filas», 1–60
 * @param columns         «Columnas», 1–160
 * @param scrollbackLines «Historial»: how many rows scrolled off the top are kept, 0–10 000
 * @param wrap            «Ajuste de línea»: a character past the last column goes to the next row instead of being
 *                        dropped
 * @param dataWidth       «Ancho del dato», 7 or 8 bits; 7-bit data ignores the eighth bit
 * @param encoding        «Codificación» of bytes 128–255 (only read with 8-bit data)
 * @param lineFeedReturns «LF también vuelve al inicio» (ECMA-48 LNM)
 * @param backspaceErases «Retroceso borra»
 * @param formFeedClears  «Ctrl-L limpia»; otherwise FF is a line feed
 * @param tabWidth        «Tabulación»: a tab stop every this many columns, 1–16
 */
public record TerminalSettings(TerminalMode mode, int rows, int columns, int scrollbackLines, boolean wrap,
                               int dataWidth, TerminalEncoding encoding, boolean lineFeedReturns,
                               boolean backspaceErases, boolean formFeedClears, int tabWidth) {

    public static final int MINIMUM_ROWS = 1;
    /** Larger than Logisim's 48 so a projector-sized screen fits (spec §8). */
    public static final int MAXIMUM_ROWS = 60;
    public static final int MINIMUM_COLUMNS = 1;
    /** Larger than Logisim's 120 (spec §8). */
    public static final int MAXIMUM_COLUMNS = 160;
    /** Spec §16, decision 8. */
    public static final int MAXIMUM_SCROLLBACK_LINES = 10_000;
    public static final int SEVEN_BITS = 7;
    public static final int EIGHT_BITS = 8;
    public static final int MINIMUM_TAB_WIDTH = 1;
    public static final int MAXIMUM_TAB_WIDTH = 16;
    /** The classic tab stop every eight columns, the default of every profile. */
    public static final int DEFAULT_TAB_WIDTH = 8;
    /** The VT100-xterm profile's «Historial». */
    public static final int VT_SCROLLBACK_LINES = 1000;

    public TerminalSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(encoding, "encoding");
        requireWithin("rows", rows, MINIMUM_ROWS, MAXIMUM_ROWS);
        requireWithin("columns", columns, MINIMUM_COLUMNS, MAXIMUM_COLUMNS);
        requireWithin("scrollbackLines", scrollbackLines, 0, MAXIMUM_SCROLLBACK_LINES);
        if (dataWidth != SEVEN_BITS && dataWidth != EIGHT_BITS) {
            throw new IllegalArgumentException("dataWidth must be 7 or 8: " + dataWidth);
        }
        requireWithin("tabWidth", tabWidth, MINIMUM_TAB_WIDTH, MAXIMUM_TAB_WIDTH);
    }

    /** The «Logisim (LRV32IM)» profile of spec §7 at the given size. */
    public static TerminalSettings logisim(int rows, int columns) {
        return new TerminalSettings(TerminalMode.LOGISIM, rows, columns, 0, true, SEVEN_BITS, TerminalEncoding.UTF8,
                true, true, true, DEFAULT_TAB_WIDTH);
    }

    /** The «VT100-xterm» profile of spec §7 (the one new parts get) at the given size. */
    public static TerminalSettings vt(int rows, int columns) {
        return new TerminalSettings(TerminalMode.VT, rows, columns, VT_SCROLLBACK_LINES, true, EIGHT_BITS,
                TerminalEncoding.UTF8, true, false, true, DEFAULT_TAB_WIDTH);
    }

    /** The «Teletipo» profile of spec §7 at the given size. */
    public static TerminalSettings teletype(int rows, int columns) {
        return new TerminalSettings(TerminalMode.TELETYPE, rows, columns, MAXIMUM_SCROLLBACK_LINES, true, SEVEN_BITS,
                TerminalEncoding.UTF8, true, false, false, DEFAULT_TAB_WIDTH);
    }

    /** The mask applied to every byte before anything else reads it. */
    int dataMask() {
        return (1 << dataWidth) - 1;
    }

    private static void requireWithin(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in " + minimum + ".." + maximum + ": " + value);
        }
    }
}
