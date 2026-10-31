package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterProfile;
import cl.almejo.vsim.core.terminal.TerminalSettings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.TerminalTypes.ANSI_PALETTE;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.BACKSPACE_ERASES;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.BELL;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.BLINK;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.CURSOR;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.DATA_WIDTH;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.EFFECT;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.ENCODING;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.FONT;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.FONT_SIZE;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.FORM_FEED_CLEARS;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.LINE_FEED_RETURNS;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.MARK_LAST;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.MODE;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.SCREEN_COLORS;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.SCROLLBACK;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.TAB_WIDTH;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.TYPING_SOUND;
import static cl.almejo.vsim.core.component.standard.TerminalTypes.WRAP;

/**
 * The Terminal's three profiles, the table of spec §7: «Logisim (LRV32IM)», «VT100‑xterm» (new parts) and «Teletipo».
 * Name, rows, columns, the character generator, the teaching properties, «Mostrar» and «Guardar salida» are in none:
 * they are the user's. «Codificación» is only in VT100‑xterm, the one profile with 8-bit data (with 7 bits it is
 * hidden and must not count as a difference).
 */
final class TerminalProfiles {

    private static final String SEVEN = String.valueOf(TerminalSettings.SEVEN_BITS);
    private static final String EIGHT = String.valueOf(TerminalSettings.EIGHT_BITS);

    private TerminalProfiles() {
    }

    /** One profile per choice of {@link TerminalTypes#PROFILE}, in its order. */
    static List<ParameterProfile> all() {
        return List.of(logisim(), vt(), teletype());
    }

    private static ParameterProfile logisim() {
        Map<String, Object> values = common();
        values.put(MODE, TerminalTypes.MODE_LOGISIM);
        values.put(DATA_WIDTH, SEVEN);
        values.put(BACKSPACE_ERASES, true);
        values.put(FORM_FEED_CLEARS, true);
        values.put(SCROLLBACK, 0);
        values.put(SCREEN_COLORS, "green");
        values.put(ANSI_PALETTE, "xterm");
        values.put(EFFECT, "soft");
        values.put(FONT, TerminalTypes.FONT_VT323);
        values.put(CURSOR, "block");
        values.put(BLINK, true);
        values.put(BELL, "none");
        values.put(TYPING_SOUND, false);
        return new ParameterProfile(TerminalTypes.PROFILE_LOGISIM, values);
    }

    private static ParameterProfile vt() {
        Map<String, Object> values = common();
        values.put(MODE, TerminalTypes.MODE_VT);
        values.put(DATA_WIDTH, EIGHT);
        values.put(ENCODING, "utf8");
        values.put(BACKSPACE_ERASES, false);
        values.put(FORM_FEED_CLEARS, true);
        values.put(SCROLLBACK, TerminalSettings.VT_SCROLLBACK_LINES);
        values.put(SCREEN_COLORS, "white");
        values.put(ANSI_PALETTE, "xterm");
        values.put(EFFECT, "none");
        values.put(FONT, TerminalTypes.FONT_PLEX);
        values.put(CURSOR, "bar");
        values.put(BLINK, true);
        values.put(BELL, "flash");
        values.put(TYPING_SOUND, false);
        return new ParameterProfile(TerminalTypes.PROFILE_VT, values);
    }

    private static ParameterProfile teletype() {
        Map<String, Object> values = common();
        values.put(MODE, TerminalTypes.MODE_TELETYPE);
        values.put(DATA_WIDTH, SEVEN);
        values.put(BACKSPACE_ERASES, false);
        values.put(FORM_FEED_CLEARS, false);
        values.put(SCROLLBACK, TerminalSettings.MAXIMUM_SCROLLBACK_LINES);
        values.put(SCREEN_COLORS, "paper");
        values.put(ANSI_PALETTE, "vga");
        values.put(EFFECT, "none");
        values.put(FONT, TerminalTypes.FONT_PLEX);
        values.put(CURSOR, "underline");
        values.put(BLINK, false);
        values.put(BELL, "both");
        values.put(TYPING_SOUND, true);
        return new ParameterProfile(TerminalTypes.PROFILE_TELETYPE, values);
    }

    /** What every profile sets the same way. */
    private static Map<String, Object> common() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(StandardParameters.EDGE.name(), StandardParameters.RISING);
        values.put(LINE_FEED_RETURNS, true);
        values.put(TAB_WIDTH, TerminalSettings.DEFAULT_TAB_WIDTH);
        values.put(WRAP, true);
        values.put(FONT_SIZE, TerminalTypes.FONT_SIZE_NORMAL);
        values.put(MARK_LAST, true);
        return values;
    }
}
