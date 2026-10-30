package cl.almejo.vsim.core.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Test helper: writes text to a screen as the circuit would, one byte per clock edge, and reads the rows back. */
final class ScreenWriter {

    private static final int BYTE_MASK = 0xFF;

    private ScreenWriter() {
    }

    /** Writes the UTF-8 bytes of {@code text}, one per time unit starting at 0. */
    static TerminalScreen write(TerminalScreen screen, String text) {
        return writeBytes(screen, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the given bytes, one per time unit starting at 0. */
    static TerminalScreen writeBytes(TerminalScreen screen, byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            screen.write(bytes[index] & BYTE_MASK, index);
        }
        return screen;
    }

    /** The screen's rows with their trailing spaces trimmed. */
    static List<String> rows(TerminalScreen screen) {
        return Arrays.asList(screen.frame().text().split("\n", -1));
    }

    /** A Logisim-mode screen of the given size. */
    static TerminalScreen logisim(int rows, int columns) {
        return new TerminalScreen(TerminalSettings.logisim(rows, columns));
    }

    /** A VT100-xterm screen of the given size. */
    static TerminalScreen vt(int rows, int columns) {
        return new TerminalScreen(TerminalSettings.vt(rows, columns));
    }

    /** A Teletipo screen of the given size. */
    static TerminalScreen teletype(int rows, int columns) {
        return new TerminalScreen(TerminalSettings.teletype(rows, columns));
    }
}
