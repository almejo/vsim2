package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * The circuit color schemes the user can choose (spec §6.7), independent of the Swing theme. CLASSIC, PASTEL, CSIM and
 * TWILIGHT take the "default", "pastel", "csim" and "Twilight" schemes of the original VSim
 * ({@code vsim/src/main/resources/config/colors.json}); PLACA draws the circuit as a printed circuit board; LOGISIM and
 * CHIPMUNK recall the colours of Logisim-evolution and of Chipmunk's diglog (the drawing stays VSim's).
 */
public final class ColorSchemes {

    public static final String CLASSIC_ID = "classic";
    public static final String LIGHT_ID = "light";
    public static final String DARK_ID = "dark";
    public static final String PASTEL_ID = "pastel";
    public static final String CSIM_ID = "csim";
    public static final String TWILIGHT_ID = "twilight";
    public static final String PLACA_ID = "placa";
    public static final String LOGISIM_ID = "logisim";
    public static final String CHIPMUNK_ID = "chipmunk";

    /** VSim's colors: gray board, black 0, red 1, green Z, blue gates, purple labels, yellow selection. */
    public static final ColorScheme CLASSIC = new ColorScheme(
            new Color(0xBDBDBD),          // background      (VSim "background")
            new Color(0xCCCCCC),          // grid            (VSim "grid")
            ColorScheme.derivedGridLine(new Color(0xCCCCCC)), // gridLine (VSim's grid, faint)
            new Color(0x000000),          // wireZero        (VSim "off")
            new Color(0xFF0000),          // wireOne         (VSim "wires-on")
            new Color(0x00FF00),          // wireHighImpedance (VSim "ground", its Z color)
            new Color(0xFF00FF),          // wireConflict    (VSim had none)
            new Color(0x808080),          // wireUnknown     (VSim had none)
            new Color(0x0000FF),          // componentStroke (VSim "gates")
            new Color(0xE6E6FF),          // componentFill   (light, so text stays readable)
            new Color(0xC8C8F0),          // componentShade
            new Color(0, 0, 0, 40),       // shadow
            new Color(0x6600CC),          // text            (VSim "label")
            new Color(0xFFFF00),          // selection       (VSim marching ants)
            new Color(0xFFFF00),          // preview
            new Color(0x0000FF),          // displayBackground (VSim fills the display with "gates")
            new Color(0xFF0000),          // segmentOn       (VSim "wires-on")
            new Color(0x000000),          // segmentOff      (VSim "off")
            new Color(0xBDBDBD),          // diagramBackground (VSim "background")
            new Color(0xCCCCCC),          // diagramGrid     (VSim "grid")
            new Color(0xFF0000),         // diagramTrace    (VSim "signal")
            new Color(0x1565C0),          // memoryRead     (the read light and row)
            new Color(0xE65100));         // memoryWrite    (the write light and value)

    /** Today's scheme (M4-0). */
    public static final ColorScheme LIGHT = ColorScheme.LIGHT;

    /** Goes with the FlatLaf dark theme; every wire state reaches 3:1 against the background. */
    public static final ColorScheme DARK = new ColorScheme(
            new Color(0x1E1F22),          // background
            new Color(0x393B40),          // grid
            ColorScheme.derivedGridLine(new Color(0x393B40)), // gridLine
            new Color(0x3D8B40),          // wireZero
            new Color(0x7CFC7C),          // wireOne
            new Color(0x5C9DFF),          // wireHighImpedance
            new Color(0xFF5252),          // wireConflict
            new Color(0x8A8F98),          // wireUnknown
            new Color(0xC9D1D9),          // componentStroke
            new Color(0x2B2D30),          // componentFill
            new Color(0x232427),          // componentShade
            new Color(0, 0, 0, 90),       // shadow
            new Color(0xDFE1E5),          // text
            new Color(0x4C9AFF),          // selection
            new Color(0x4C9AFF),          // preview
            new Color(0x0D1117),          // displayBackground
            new Color(0xFF453A),          // segmentOn
            new Color(255, 255, 255, 22), // segmentOff
            new Color(0x0D1117),          // diagramBackground
            new Color(255, 255, 255, 32), // diagramGrid
            new Color(0x7CFC7C),         // diagramTrace
            new Color(0x4FC3F7),          // memoryRead     (the read light and row)
            new Color(0xFFA726));         // memoryWrite    (the write light and value)

    /** VSim's "pastel": gray board, black 0, coral 1, green Z, blue gates, orange labels. */
    public static final ColorScheme PASTEL = new ColorScheme(
            new Color(0xBDBDBD),          // background      (VSim "background")
            new Color(0xCCCCCC),          // grid            (VSim "grid")
            ColorScheme.derivedGridLine(new Color(0xCCCCCC)), // gridLine (VSim's grid, faint)
            new Color(0x000000),          // wireZero        (VSim "off")
            new Color(0xDB5340),          // wireOne         (VSim "wires-on")
            new Color(0x00FF00),          // wireHighImpedance (VSim "ground")
            new Color(0xFF00FF),          // wireConflict    (as CLASSIC; VSim had none)
            new Color(0x808080),          // wireUnknown     (as CLASSIC; VSim had none)
            new Color(0x389CD1),          // componentStroke (VSim "gates")
            new Color(0xF2F7FB),          // componentFill   (light, so the orange labels read inside boxes)
            new Color(0xD6E6F2),          // componentShade
            new Color(0, 0, 0, 40),       // shadow
            new Color(0xCC6600),          // text            (VSim "label")
            new Color(0xFFFF00),          // selection       (VSim marching ants)
            new Color(0xFFFF00),          // preview
            new Color(0x1B4F72),          // displayBackground (darker "gates": VSim's red on its blue is 1.29:1)
            new Color(0xDB5340),          // segmentOn       (VSim "wires-on")
            new Color(0x000000),          // segmentOff      (VSim "off")
            new Color(0xBDBDBD),          // diagramBackground (VSim "background")
            new Color(0xCCCCCC),          // diagramGrid     (VSim "grid")
            new Color(0x990033),         // diagramTrace    (VSim "signal")
            new Color(0x1565C0),          // memoryRead     (the read light and row)
            new Color(0xE65100));         // memoryWrite    (the write light and value)

    /** VSim's "csim": blue board, black 0, white 1, green Z, orange gates, yellow labels. */
    public static final ColorScheme CSIM = new ColorScheme(
            new Color(0x0033CC),          // background      (VSim "background")
            new Color(0x0033CC),          // grid            (VSim "grid": the same blue, so no visible dots)
            new Color(255, 255, 255, 24), // gridLine        (faint white: the dots' blue would not show)
            new Color(0x000000),          // wireZero        (VSim "off")
            new Color(0xFFFFFF),          // wireOne         (VSim "wires-on")
            new Color(0x00FF00),          // wireHighImpedance (VSim "ground")
            new Color(0xFF00FF),          // wireConflict    (as CLASSIC; VSim had none)
            new Color(0x8A8F98),          // wireUnknown
            new Color(0xFF9933),          // componentStroke (VSim "gates")
            new Color(0x001A66),          // componentFill   (dark navy, so the yellow labels read inside boxes)
            new Color(0x00124A),          // componentShade
            new Color(0, 0, 0, 90),       // shadow
            new Color(0xFFFF00),          // text            (VSim "label")
            new Color(0xFFFF00),          // selection       (VSim marching ants)
            new Color(0xFFFF00),          // preview
            new Color(0xFF9933),          // displayBackground (VSim fills the display with "gates")
            new Color(0xFFFFFF),          // segmentOn       (VSim "wires-on")
            new Color(0x000000),          // segmentOff      (VSim "off")
            new Color(0x0033CC),          // diagramBackground (VSim "background")
            new Color(255, 255, 255, 40), // diagramGrid     (VSim's grid is the background color)
            new Color(0xFF0000),         // diagramTrace    (VSim "signal")
            new Color(0x4FC3F7),          // memoryRead     (the read light and row)
            new Color(0xFFA726));         // memoryWrite    (the write light and value)

    /** VSim's "Twilight": black board, gray 0, dark red 1, dark green Z, navy gates, blue labels. */
    public static final ColorScheme TWILIGHT = new ColorScheme(
            new Color(0x000000),          // background      (VSim "background")
            new Color(0x1C1C1C),          // grid            (VSim "grid")
            new Color(255, 255, 255, 20), // gridLine        (faint white: the dots' grey is lost on black)
            new Color(0x808080),          // wireZero        (VSim "off")
            new Color(0x800000),          // wireOne         (VSim "wires-on")
            new Color(0x008000),          // wireHighImpedance (VSim "ground")
            new Color(0xFF00FF),          // wireConflict    (as CLASSIC; VSim had none)
            new Color(0x4040A0),          // wireUnknown     (96 RGB units from gray 0)
            new Color(0x004680),          // componentStroke (VSim "gates")
            new Color(0x0A0F1A),          // componentFill   (near black, so the blue labels read inside boxes)
            new Color(0x05080F),          // componentShade
            new Color(0, 0, 0, 90),       // shadow
            new Color(0x0066FF),          // text            (VSim "label")
            new Color(0xFFFF00),          // selection       (VSim marching ants)
            new Color(0xFFFF00),          // preview
            new Color(0x004680),          // displayBackground (VSim fills the display with "gates")
            new Color(0xFF0000),          // segmentOn       (VSim "bus-on": its "wires-on" is 1.14:1 on the display)
            new Color(0x808080),          // segmentOff      (VSim "off")
            new Color(0x000000),          // diagramBackground (VSim "background")
            new Color(0x1C1C1C),          // diagramGrid     (VSim "grid")
            new Color(0x990099),         // diagramTrace    (VSim "signal")
            new Color(0x4FC3F7),          // memoryRead     (the read light and row)
            new Color(0xFFA726));         // memoryWrite    (the write light and value)

    /**
     * «Placa»: a printed circuit board. Green solder mask, copper traces at 0 and bright gold at 1, white silkscreen
     * for the outlines and labels, black chip packages, gold pads at the pins (the only scheme whose pins are not its
     * outline colour), faint lighter-green vias for the grid. Dark board, so the dark
     * variants of the frame tints and the tunnel palette apply (both read on it). Every wire state reaches 3:1 on the
     * board and the texts 4.5:1 (ColorSchemesTest).
     */
    public static final ColorScheme PLACA = new ColorScheme(
            new Color(0x0B3D20),          // background      (solder mask)
            new Color(0x24603A),          // grid            (vias: a faint lighter green)
            new Color(0x6F, 0xCF, 0x8F, 36), // gridLine     (a slightly lighter green, faint)
            new Color(0xC87533),          // wireZero        (copper trace, 3.55:1)
            new Color(0xFFD34D),          // wireOne         (bright gold, 2.4:1 against the copper)
            new Color(0x82A8FF),          // wireHighImpedance (periwinkle, away from copper and gold)
            new Color(0xFF4F8B),          // wireConflict    (pink red, apart from the copper)
            new Color(0xA7AEB2),          // wireUnknown
            new Color(0xF0F0E6),          // componentStroke (white silkscreen)
            new Color(0x1C201E),          // componentFill   (black chip package)
            new Color(0x121513),          // componentShade
            new Color(0, 0, 0, 90),       // shadow
            new Color(0xF5F5EE),          // text            (white silkscreen)
            new Color(0x3FE9FF),          // selection       (cyan: not a wire state, so the glows read)
            new Color(0x3FE9FF),          // preview
            new Color(0x0E1210),          // displayBackground
            new Color(0xFF3B30),          // segmentOn
            new Color(255, 255, 255, 22), // segmentOff
            new Color(0x082E18),          // diagramBackground (a deeper green)
            new Color(255, 255, 255, 32), // diagramGrid
            new Color(0xFFD34D),          // diagramTrace    (the gold of a 1)
            new Color(0xE0B04A),         // pad             (gold pads at the pins, 6.2:1 on the board)
            new Color(0x4FC3F7),          // memoryRead     (the read light and row)
            new Color(0xFFA726));         // memoryWrite    (the write light and value)

    /**
     * «Logisim»: Logisim-evolution's default colours ({@code AppPreferences}): white canvas with gray dots, black parts
     * on white, dark green 0, bright green 1, blue floating, red error, gray unknown; its seven-segment display's red
     * and light-gray segments and its blue rectangle selection.
     */
    public static final ColorScheme LOGISIM = new ColorScheme(
            new Color(0xFFFFFF),          // background      (canvas)
            new Color(0x777777),          // grid            (grid dot)
            ColorScheme.derivedGridLine(new Color(0xCCCCCC)), // gridLine (its zoomed-out dot, faint)
            new Color(0x006400),          // wireZero        (false)
            new Color(0x00D200),          // wireOne         (true)
            new Color(0x2828FF),          // wireHighImpedance (unknown: a floating wire)
            new Color(0xC00000),          // wireConflict    (error)
            new Color(0x808080),          // wireUnknown     (nil)
            new Color(0x000000),          // componentStroke (component)
            new Color(0xFFFFFF),          // componentFill   (parts are not filled: the canvas shows through)
            new Color(0xEEEEEE),          // componentShade
            new Color(0, 0, 0, 40),       // shadow          (as the other light schemes)
            new Color(0x000000),          // text
            new Color(0x004080),          // selection       (the rectangle selection's line)
            new Color(0x999999),          // preview         (the ghost of a part being placed)
            new Color(0xFFFFFF),          // displayBackground (the display has no background of its own)
            new Color(240, 0, 0),         // segmentOn
            new Color(220, 220, 220),     // segmentOff
            new Color(0xFFFFFF),          // diagramBackground (the chronogram)
            new Color(0, 0, 0, 30),       // diagramGrid
            new Color(0x000000),          // diagramTrace    (the chronogram's black lines)
            new Color(0x1565C0),          // memoryRead     (the read light and row)
            new Color(0xE65100));         // memoryWrite    (the write light and value)

    /**
     * «Chipmunk»: diglog's 4-bit colour table ({@code log.c}) and default roles: dark gray screen, cyan gates, red gate
     * pins (the only scheme besides «Placa» whose pins are not its outline colour), dark-yellow labels, a white cursor;
     * the wires as in its glow mode: red 1 ({@code ONLED}), black 0 ({@code OFFLED}) and the plain green of a wire with
     * no value for Z. diglog draws no grid and has no conflict or unknown colour: those are ours, faint or apart.
     */
    public static final ColorScheme CHIPMUNK = new ColorScheme(
            new Color(0x444444),          // background      (gray, 4/15)
            new Color(0x555555),          // grid            (diglog shows none: barely there)
            new Color(255, 255, 255, 14), // gridLine
            new Color(0x000000),          // wireZero        (OFFLED: black)
            new Color(0xFF0000),          // wireOne         (ONLED: red)
            new Color(0x00FF00),          // wireHighImpedance (WIRE: green, a wire with no value)
            new Color(0xFF00FF),          // wireConflict    (as CLASSIC; its CONFLICT is the red of a 1)
            new Color(0xAAAAAA),          // wireUnknown     (light gray, 10/15)
            new Color(0x00FFFF),          // componentStroke (GATE: cyan)
            new Color(0x444444),          // componentFill   (gates are outlines on the screen)
            new Color(0x3A3A3A),          // componentShade
            new Color(0, 0, 0, 90),       // shadow          (as the other dark schemes)
            new Color(0xCCCC00),          // text            (LABELTEXT: dark yellow)
            new Color(0xFFFFFF),          // selection       (SELECT: white)
            new Color(0xFFFFFF),          // preview         (CURSOR: white)
            new Color(0x000000),          // displayBackground (OFFLED black)
            new Color(0xFF0000),          // segmentOn       (ONLED: red)
            new Color(255, 0, 0, 40),     // segmentOff      (a faint red, so the digit's shape shows)
            new Color(0x444444),          // diagramBackground (the screen)
            new Color(255, 255, 255, 40), // diagramGrid     (DIVISION: white, faint)
            new Color(0xFFFF00),          // diagramTrace    (SCOPE: yellow)
            new Color(0xFF0000),          // pad             (GATEPIN: red)
            new Color(0x4FC3F7),          // memoryRead     (the read light and row)
            new Color(0xFFA726));         // memoryWrite    (the write light and value)

    private ColorSchemes() {
    }

    /** Scheme ids in the order the preferences dialog lists them. */
    public static List<String> ids() {
        return List.of(CLASSIC_ID, LIGHT_ID, DARK_ID, PASTEL_ID, CSIM_ID, TWILIGHT_ID, PLACA_ID, LOGISIM_ID, CHIPMUNK_ID);
    }

    public static Optional<ColorScheme> byId(String id) {
        return switch (id) {
            case CLASSIC_ID -> Optional.of(CLASSIC);
            case LIGHT_ID -> Optional.of(LIGHT);
            case DARK_ID -> Optional.of(DARK);
            case PASTEL_ID -> Optional.of(PASTEL);
            case CSIM_ID -> Optional.of(CSIM);
            case TWILIGHT_ID -> Optional.of(TWILIGHT);
            case PLACA_ID -> Optional.of(PLACA);
            case LOGISIM_ID -> Optional.of(LOGISIM);
            case CHIPMUNK_ID -> Optional.of(CHIPMUNK);
            default -> Optional.empty();
        };
    }

    /** The scheme that goes with the theme when the user did not choose one. */
    public static ColorScheme forTheme(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    /** Visible name of a scheme id; an unknown id is shown as it is. */
    public static String displayName(String id) {
        return switch (id) {
            case CLASSIC_ID -> Messages.text("scheme.classic");
            case LIGHT_ID -> Messages.text("scheme.light");
            case DARK_ID -> Messages.text("scheme.dark");
            case PASTEL_ID -> Messages.text("scheme.pastel");
            case CSIM_ID -> Messages.text("scheme.csim");
            case TWILIGHT_ID -> Messages.text("scheme.twilight");
            case PLACA_ID -> Messages.text("scheme.placa");
            case LOGISIM_ID -> Messages.text("scheme.logisim");
            case CHIPMUNK_ID -> Messages.text("scheme.chipmunk");
            default -> id;
        };
    }
}
