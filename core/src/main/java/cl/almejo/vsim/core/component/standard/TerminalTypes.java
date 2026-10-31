package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.ResizeSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TerminalEncoding;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalGeometry;
import cl.almejo.vsim.core.terminal.TerminalMode;
import cl.almejo.vsim.core.terminal.TerminalSettings;

import java.util.List;
import java.util.Map;

/**
 * The «Terminal» part: a text screen written one byte at a time on a clock edge (spec §2–§8). See
 * {@link TerminalBehavior} for the pins' behaviour, {@link TerminalPinout} for where they are and
 * {@link TerminalProfiles} for the three profiles. Look, teaching, sound and «Dónde se ve» parameters are display-only:
 * changing them never rebuilds the behaviour; the others rebuild it and the new one keeps the screen
 * ({@link TerminalBehavior#adoptStateFrom}).
 */
public final class TerminalTypes {

    /** «Perfil». */
    public static final String PROFILE = "profile";
    /** «Filas». */
    public static final String ROWS = "rows";
    /** «Columnas». */
    public static final String COLUMNS = "columns";
    /** «Historial»: rows kept after they scroll off the top. */
    public static final String SCROLLBACK = "scrollback";
    /** «Ajuste de línea». */
    public static final String WRAP = "wrap";
    /** «Generador de caracteres»: internal font or an external character ROM (Task 11). */
    public static final String GLYPHS = "glyphs";
    /** «Ancho del dato», 7 or 8 bits: the width of D. Shared with the Teclado. */
    public static final String DATA_WIDTH = KeyEncoding.DATA_WIDTH;
    /** «Codificación» of 8-bit data. Shared with the Teclado. */
    public static final String ENCODING = KeyEncoding.ENCODING;
    /** «Modo». */
    public static final String MODE = "mode";
    /** «LF también vuelve al inicio». */
    public static final String LINE_FEED_RETURNS = "line-feed-returns";
    /** «Retroceso borra». */
    public static final String BACKSPACE_ERASES = "backspace-erases";
    /** «Ctrl-L limpia». */
    public static final String FORM_FEED_CLEARS = "form-feed-clears";
    /** «Tabulación». */
    public static final String TAB_WIDTH = "tab-width";
    /** «Colores de pantalla». */
    public static final String SCREEN_COLORS = "screen-colors";
    /** «Paleta ANSI». */
    public static final String ANSI_PALETTE = "ansi-palette";
    /** «Efecto de pantalla». */
    public static final String EFFECT = "effect";
    /** «Fuente». */
    public static final String FONT = "font";
    /** «Tamaño de letra»: it sets the part's size, so it is not display-only. */
    public static final String FONT_SIZE = "font-size";
    /** «Cursor». */
    public static final String CURSOR = "cursor";
    /** «Parpadea». */
    public static final String BLINK = "blink";
    /** «Marcar el último carácter». */
    public static final String MARK_LAST = "mark-last";
    /** «Detener al recibir»: the simulation pauses when the received text ends with it; empty = never. */
    public static final String STOP_ON = "stop-on";
    /** «Salida esperada». */
    public static final String EXPECTED = "expected";
    /** The expected output written in the part. */
    public static final String EXPECTED_TEXT = "expected-text";
    /** The .txt file holding the expected output. */
    public static final String EXPECTED_FILE = "expected-file";
    /** «En formas de onda»: D's bytes as characters or in hexadecimal. */
    public static final String WAVEFORM_FORMAT = "waveform-format";
    /** «Campana (BEL)». */
    public static final String BELL = "bell";
    /** «Golpeteo de teletipo»: its own id, so it is not named like the Teclado's «Sonido de tecla». */
    public static final String TELETYPE_SOUND = "teletype-sound";
    /** «Mostrar»: on the canvas, or on the canvas and in a window. */
    public static final String SHOW = "show";
    /** «Guardar salida». */
    public static final String SAVE_OUTPUT = "save-output";
    /** The .txt file the output is saved to. */
    public static final String OUTPUT_FILE = "output-file";

    public static final String PROFILE_LOGISIM = "logisim";
    public static final String PROFILE_VT = "vt";
    public static final String PROFILE_TELETYPE = "teletype";
    public static final String MODE_LOGISIM = "logisim";
    public static final String MODE_VT = "vt";
    public static final String MODE_TELETYPE = "teletype";
    public static final String GLYPHS_INTERNAL = "internal";
    public static final String GLYPHS_EXTERNAL = "external";
    public static final String FONT_SIZE_SMALL = "small";
    public static final String FONT_SIZE_NORMAL = "normal";
    public static final String FONT_SIZE_LARGE = "large";
    public static final String FONT_PLEX = "plex";
    public static final String FONT_VT323 = "vt323";
    public static final String FONT_SYSTEM = "system";
    public static final String EXPECTED_NONE = "none";
    public static final String EXPECTED_WRITTEN = "text";
    public static final String EXPECTED_FROM_FILE = "file";
    public static final String SAVE_OUTPUT_NO = "no";
    public static final String SAVE_OUTPUT_TO_FILE = "file";

    public static final List<String> PROFILES = List.of(PROFILE_LOGISIM, PROFILE_VT, PROFILE_TELETYPE);
    public static final List<String> SCREEN_COLOR_CHOICES = List.of("green", "amber", "white", "paper", "theme");
    public static final List<String> ANSI_PALETTE_CHOICES = List.of("xterm", "vga", "solarized", "colorblind");
    public static final List<String> EFFECT_CHOICES = List.of("none", "soft", "strong");
    public static final List<String> FONT_CHOICES = List.of(FONT_PLEX, FONT_VT323, FONT_SYSTEM);
    public static final List<String> CURSOR_CHOICES = List.of("block", "hollow", "underline", "bar", "ibeam",
            "automan", "none");
    public static final List<String> BELL_CHOICES = List.of("flash", "sound", "both", "none");

    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLUMNS = 80;
    public static final int MAXIMUM_ROWS = TerminalSettings.MAXIMUM_ROWS;
    public static final int MAXIMUM_COLUMNS = TerminalSettings.MAXIMUM_COLUMNS;

    private static final String SEVEN = String.valueOf(TerminalSettings.SEVEN_BITS);
    private static final String EIGHT = String.valueOf(TerminalSettings.EIGHT_BITS);
    private static final String GROUP_PIECE = "piece";
    private static final String GROUP_SCREEN = "screen";
    private static final String GROUP_DATA = "data";
    private static final String GROUP_INTERPRETATION = "interpretation";
    private static final String GROUP_APPEARANCE = "appearance";
    private static final String GROUP_TEACHING = "teaching";
    private static final String GROUP_SOUND = "sound";
    private static final String GROUP_DISPLAY = "display";

    private static final List<ParameterSpec> PARAMETERS = List.of(
            ParameterSpec.choice(PROFILE, PROFILE_VT, PROFILES).inGroup(GROUP_PIECE),
            ParameterSpec.integer(ROWS, DEFAULT_ROWS, TerminalSettings.MINIMUM_ROWS, MAXIMUM_ROWS)
                    .inGroup(GROUP_SCREEN),
            ParameterSpec.integer(COLUMNS, DEFAULT_COLUMNS, TerminalSettings.MINIMUM_COLUMNS, MAXIMUM_COLUMNS)
                    .inGroup(GROUP_SCREEN),
            ParameterSpec.integer(SCROLLBACK, TerminalSettings.VT_SCROLLBACK_LINES, 0,
                    TerminalSettings.MAXIMUM_SCROLLBACK_LINES).inGroup(GROUP_SCREEN),
            ParameterSpec.bool(WRAP, true).inGroup(GROUP_SCREEN),
            ParameterSpec.choice(GLYPHS, GLYPHS_INTERNAL, List.of(GLYPHS_INTERNAL, GLYPHS_EXTERNAL))
                    .inGroup(GROUP_SCREEN),
            ParameterSpec.choice(DATA_WIDTH, EIGHT, List.of(SEVEN, EIGHT)).inGroup(GROUP_DATA),
            ParameterSpec.choice(ENCODING, KeyEncoding.UTF8_VALUE,
                    List.of(KeyEncoding.UTF8_VALUE, KeyEncoding.LATIN1_VALUE))
                    .visibleWhen(DATA_WIDTH, EIGHT).inGroup(GROUP_DATA),
            StandardParameters.EDGE.inGroup(GROUP_DATA),
            ParameterSpec.choice(MODE, MODE_VT, List.of(MODE_LOGISIM, MODE_VT, MODE_TELETYPE))
                    .inGroup(GROUP_INTERPRETATION),
            ParameterSpec.bool(LINE_FEED_RETURNS, true).inGroup(GROUP_INTERPRETATION),
            ParameterSpec.bool(BACKSPACE_ERASES, false).inGroup(GROUP_INTERPRETATION),
            ParameterSpec.bool(FORM_FEED_CLEARS, true).inGroup(GROUP_INTERPRETATION),
            ParameterSpec.integer(TAB_WIDTH, TerminalSettings.DEFAULT_TAB_WIDTH, TerminalSettings.MINIMUM_TAB_WIDTH,
                    TerminalSettings.MAXIMUM_TAB_WIDTH).inGroup(GROUP_INTERPRETATION),
            look(ParameterSpec.choice(SCREEN_COLORS, "white", SCREEN_COLOR_CHOICES), GROUP_APPEARANCE),
            look(ParameterSpec.choice(ANSI_PALETTE, "xterm", ANSI_PALETTE_CHOICES), GROUP_APPEARANCE),
            look(ParameterSpec.choice(EFFECT, "none", EFFECT_CHOICES), GROUP_APPEARANCE),
            look(ParameterSpec.choice(FONT, FONT_PLEX, FONT_CHOICES), GROUP_APPEARANCE),
            ParameterSpec.choice(FONT_SIZE, FONT_SIZE_NORMAL, List.of(FONT_SIZE_SMALL, FONT_SIZE_NORMAL,
                    FONT_SIZE_LARGE)).inGroup(GROUP_APPEARANCE),
            look(ParameterSpec.choice(CURSOR, "bar", CURSOR_CHOICES), GROUP_APPEARANCE),
            look(ParameterSpec.bool(BLINK, true), GROUP_APPEARANCE),
            look(ParameterSpec.bool(MARK_LAST, true), GROUP_TEACHING),
            ParameterSpec.text(STOP_ON, "").inGroup(GROUP_TEACHING),
            look(ParameterSpec.choice(EXPECTED, EXPECTED_NONE, List.of(EXPECTED_NONE, EXPECTED_WRITTEN,
                    EXPECTED_FROM_FILE)), GROUP_TEACHING),
            look(ParameterSpec.text(EXPECTED_TEXT, "").asMultiline().visibleWhen(EXPECTED, EXPECTED_WRITTEN),
                    GROUP_TEACHING),
            look(ParameterSpec.text(EXPECTED_FILE, "").visibleWhen(EXPECTED, EXPECTED_FROM_FILE), GROUP_TEACHING),
            look(ParameterSpec.choice(WAVEFORM_FORMAT, "chars", List.of("chars", "hex")), GROUP_TEACHING),
            look(ParameterSpec.choice(BELL, "flash", BELL_CHOICES), GROUP_SOUND),
            look(ParameterSpec.bool(TELETYPE_SOUND, false), GROUP_SOUND),
            look(ParameterSpec.choice(SHOW, "canvas", List.of("canvas", "window")), GROUP_DISPLAY),
            look(ParameterSpec.choice(SAVE_OUTPUT, SAVE_OUTPUT_NO, List.of(SAVE_OUTPUT_NO, SAVE_OUTPUT_TO_FILE)),
                    GROUP_DISPLAY),
            look(ParameterSpec.text(OUTPUT_FILE, "").visibleWhen(SAVE_OUTPUT, SAVE_OUTPUT_TO_FILE), GROUP_DISPLAY));

    /**
     * The bottom-right corner grip (Task 7b): columns on the right edge, rows on the bottom one, one value for every
     * cell of the current «Tamaño de letra» dragged (decision 3: cell sizes in pixels at 100 %).
     */
    private static final ResizeSpec RESIZE = ResizeSpec.corner(
            ResizeSpec.Axis.stepped(COLUMNS, ResizeSpec.Edge.RIGHT,
                    parameters -> (double) fontSize(parameters).cellWidth() / GridPoint.PIXELS_PER_UNIT),
            ResizeSpec.Axis.stepped(ROWS, ResizeSpec.Edge.BOTTOM,
                    parameters -> (double) fontSize(parameters).cellHeight() / GridPoint.PIXELS_PER_UNIT));

    private TerminalTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.TERMINAL, ComponentCategory.INPUT_OUTPUT,
                PARAMETERS, TerminalTypes::shape, TerminalBehavior::new)
                .withProfiles(PROFILE, TerminalProfiles.all())
                .withResize(RESIZE));
    }

    /** What decides how the resolved {@code parameters} turn bytes into a screen. */
    public static TerminalSettings settings(Map<String, Object> parameters) {
        return new TerminalSettings(mode(parameters.get(MODE)), (Integer) parameters.get(ROWS),
                (Integer) parameters.get(COLUMNS), (Integer) parameters.get(SCROLLBACK),
                Boolean.TRUE.equals(parameters.get(WRAP)), dataWidth(parameters),
                KeyEncoding.LATIN1_VALUE.equals(parameters.get(ENCODING)) ? TerminalEncoding.LATIN1
                        : TerminalEncoding.UTF8,
                Boolean.TRUE.equals(parameters.get(LINE_FEED_RETURNS)),
                Boolean.TRUE.equals(parameters.get(BACKSPACE_ERASES)),
                Boolean.TRUE.equals(parameters.get(FORM_FEED_CLEARS)), (Integer) parameters.get(TAB_WIDTH));
    }

    /** The cell size the resolved {@code parameters} choose with «Tamaño de letra». */
    public static TerminalFontSize fontSize(Map<String, Object> parameters) {
        return switch (String.valueOf(parameters.get(FONT_SIZE))) {
            case FONT_SIZE_SMALL -> TerminalFontSize.SMALL;
            case FONT_SIZE_LARGE -> TerminalFontSize.LARGE;
            default -> TerminalFontSize.NORMAL;
        };
    }

    /** The width of D, 7 or 8 bits, that the resolved {@code parameters} choose. */
    static int dataWidth(Map<String, Object> parameters) {
        return SEVEN.equals(String.valueOf(parameters.get(DATA_WIDTH))) ? TerminalSettings.SEVEN_BITS
                : TerminalSettings.EIGHT_BITS;
    }

    private static TerminalMode mode(Object value) {
        return switch (String.valueOf(value)) {
            case MODE_LOGISIM -> TerminalMode.LOGISIM;
            case MODE_TELETYPE -> TerminalMode.TELETYPE;
            default -> TerminalMode.VT;
        };
    }

    private static ParameterSpec look(ParameterSpec parameter, String group) {
        return parameter.asDisplayOnly().inGroup(group);
    }

    private static ComponentShape shape(Map<String, Object> resolved) {
        return TerminalPinout.shape(TerminalGeometry.size((Integer) resolved.get(ROWS),
                (Integer) resolved.get(COLUMNS), fontSize(resolved)), dataWidth(resolved));
    }
}
