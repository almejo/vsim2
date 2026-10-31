package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TerminalSettings;

import java.util.List;
import java.util.Map;

/**
 * The «UART 16550» part (spec §12): the standard serial port of a PC and of QEMU's RISC‑V {@code virt} board, with its
 * eight registers on a CPU bus. Its line goes to a screen and a keyboard of its own («Pantalla: integrada», a
 * Terminal and a Teclado inside it, with their properties under the «Pantalla» and «Teclado» groups and the Terminal
 * window) or to pins («Pines TX y RX») that a Terminal and a Teclado of the circuit can be wired to. See
 * {@link Uart16550Behavior} for the registers and {@link UartPinout} for the pins.
 *
 * <p>The screen's and keyboard's parameters keep the Terminal's and the Teclado's names, so they read, save and show
 * the same; data is always 8 bits, so «Ancho del dato» is not among them and one «Codificación» serves both.
 */
public final class UartTypes {

    /** «Separación de registros»: 1 byte (the classic 16550, QEMU {@code virt}) or 4; only the table shows it. */
    public static final String REGISTER_STRIDE = "register-stride";
    /** «FIFO»: 1 byte (a 16450) or 16. */
    public static final String FIFO = "fifo";
    /** «Pantalla»: integrated, or TX and RX pins. */
    public static final String SCREEN = "screen";

    public static final String SCREEN_INTEGRATED = "integrated";
    public static final String SCREEN_PINS = "pins";
    public static final List<String> STRIDES = List.of("1", "4");
    public static final List<String> FIFO_DEPTHS = List.of("1", "16");

    /** The title bar's height, in grid units (the Terminal's). */
    public static final int TITLE_BAR_UNITS = 2;
    /** Room for the names of the pins on the left edge, in grid units. */
    public static final int LEFT_PANEL_UNITS = 3;
    /** The width of the register table, in grid units. */
    public static final int REGISTER_PANEL_UNITS = 14;
    /** Where the integrated screen starts, in grid units from the left edge: right after the register table. */
    public static final int SCREEN_LEFT_UNITS = LEFT_PANEL_UNITS + REGISTER_PANEL_UNITS;
    /** Room for the names of the pins on the right edge, in grid units. */
    public static final int RIGHT_PANEL_UNITS = 4;
    /** The margin above and below the integrated screen, in grid units. */
    public static final int MARGIN_UNITS = 1;

    private static final String GROUP_PIECE = "piece";
    private static final String GROUP_SCREEN = "screen";
    private static final String GROUP_KEYBOARD = "keyboard";

    private static final List<ParameterSpec> PARAMETERS = List.of(
            ParameterSpec.choice(REGISTER_STRIDE, "1", STRIDES).asDisplayOnly().inGroup(GROUP_PIECE),
            ParameterSpec.choice(FIFO, "16", FIFO_DEPTHS).inGroup(GROUP_PIECE),
            ParameterSpec.choice(SCREEN, SCREEN_INTEGRATED, List.of(SCREEN_INTEGRATED, SCREEN_PINS))
                    .inGroup(GROUP_PIECE),
            StandardParameters.EDGE.inGroup(GROUP_PIECE),
            screen(ParameterSpec.integer(TerminalTypes.ROWS, TerminalTypes.DEFAULT_ROWS,
                    TerminalSettings.MINIMUM_ROWS, TerminalTypes.MAXIMUM_ROWS)),
            screen(ParameterSpec.integer(TerminalTypes.COLUMNS, TerminalTypes.DEFAULT_COLUMNS,
                    TerminalSettings.MINIMUM_COLUMNS, TerminalTypes.MAXIMUM_COLUMNS)),
            screen(ParameterSpec.integer(TerminalTypes.SCROLLBACK, TerminalSettings.VT_SCROLLBACK_LINES, 0,
                    TerminalSettings.MAXIMUM_SCROLLBACK_LINES)),
            screen(ParameterSpec.bool(TerminalTypes.WRAP, true)),
            screen(ParameterSpec.choice(TerminalTypes.MODE, TerminalTypes.MODE_VT, List.of(TerminalTypes.MODE_LOGISIM,
                    TerminalTypes.MODE_VT, TerminalTypes.MODE_TELETYPE))),
            screen(ParameterSpec.choice(KeyEncoding.ENCODING, KeyEncoding.UTF8_VALUE,
                    List.of(KeyEncoding.UTF8_VALUE, KeyEncoding.LATIN1_VALUE))),
            screen(ParameterSpec.bool(TerminalTypes.LINE_FEED_RETURNS, true)),
            screen(ParameterSpec.bool(TerminalTypes.BACKSPACE_ERASES, false)),
            screen(ParameterSpec.bool(TerminalTypes.FORM_FEED_CLEARS, true)),
            screen(ParameterSpec.integer(TerminalTypes.TAB_WIDTH, TerminalSettings.DEFAULT_TAB_WIDTH,
                    TerminalSettings.MINIMUM_TAB_WIDTH, TerminalSettings.MAXIMUM_TAB_WIDTH)),
            screen(ParameterSpec.choice(TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_NORMAL,
                    List.of(TerminalTypes.FONT_SIZE_SMALL, TerminalTypes.FONT_SIZE_NORMAL,
                            TerminalTypes.FONT_SIZE_LARGE))),
            look(ParameterSpec.choice(TerminalTypes.SCREEN_COLORS, "white", TerminalTypes.SCREEN_COLOR_CHOICES)),
            look(ParameterSpec.choice(TerminalTypes.ANSI_PALETTE, "xterm", TerminalTypes.ANSI_PALETTE_CHOICES)),
            look(ParameterSpec.choice(TerminalTypes.EFFECT, "none", TerminalTypes.EFFECT_CHOICES)),
            look(ParameterSpec.choice(TerminalTypes.FONT, TerminalTypes.FONT_PLEX, TerminalTypes.FONT_CHOICES)),
            look(ParameterSpec.choice(TerminalTypes.CURSOR, "bar", TerminalTypes.CURSOR_CHOICES)),
            look(ParameterSpec.bool(TerminalTypes.BLINK, true)),
            look(ParameterSpec.bool(TerminalTypes.MARK_LAST, true)),
            keyboard(ParameterSpec.integer(KeyboardTypes.CAPACITY, KeyboardTypes.DEFAULT_CAPACITY, 1,
                    KeyboardTypes.MAXIMUM_CAPACITY)),
            keyboard(ParameterSpec.choice(KeyEncoding.ENTER_SENDS, KeyEncoding.LF_VALUE,
                    List.of(KeyEncoding.LF_VALUE, KeyEncoding.CR_VALUE, KeyEncoding.CR_LF_VALUE))),
            keyboard(ParameterSpec.choice(KeyEncoding.BACKSPACE_SENDS, KeyEncoding.BS_VALUE,
                    List.of(KeyEncoding.BS_VALUE, KeyEncoding.DEL_VALUE))),
            keyboard(ParameterSpec.bool(KeyEncoding.ARROWS_AS_SEQUENCES, false)),
            keyboard(ParameterSpec.text(KeyboardTypes.INITIAL_TEXT, "").asMultiline()));

    private UartTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.UART_16550,
                ComponentCategory.INPUT_OUTPUT, PARAMETERS, UartTypes::shape, Uart16550Behavior::new));
    }

    /** Whether the resolved {@code parameters} put the screen and the keyboard inside the UART. */
    public static boolean isIntegrated(Map<String, Object> parameters) {
        return !SCREEN_PINS.equals(parameters.get(SCREEN));
    }

    /** The bytes between two registers the resolved {@code parameters} draw: 1 or 4. */
    public static int registerStride(Map<String, Object> parameters) {
        return Integer.parseInt(String.valueOf(parameters.getOrDefault(REGISTER_STRIDE, STRIDES.get(0))));
    }

    /** The depth of the FIFOs the resolved {@code parameters} choose: 1 (a 16450) or 16. */
    public static int fifoDepth(Map<String, Object> parameters) {
        return Integer.parseInt(String.valueOf(parameters.getOrDefault(FIFO, FIFO_DEPTHS.get(1))));
    }

    private static ParameterSpec screen(ParameterSpec parameter) {
        return parameter.inGroup(GROUP_SCREEN).visibleWhen(SCREEN, SCREEN_INTEGRATED);
    }

    private static ParameterSpec look(ParameterSpec parameter) {
        return screen(parameter.asDisplayOnly());
    }

    private static ParameterSpec keyboard(ParameterSpec parameter) {
        return parameter.inGroup(GROUP_KEYBOARD).visibleWhen(SCREEN, SCREEN_INTEGRATED);
    }

    private static ComponentShape shape(Map<String, Object> resolved) {
        boolean integrated = isIntegrated(resolved);
        GridSize size = UartPinout.size(integrated, (Integer) resolved.get(TerminalTypes.ROWS),
                (Integer) resolved.get(TerminalTypes.COLUMNS), TerminalTypes.fontSize(resolved));
        return UartPinout.shape(size, integrated);
    }
}
