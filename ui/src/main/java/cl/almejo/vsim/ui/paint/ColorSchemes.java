package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * The circuit color schemes the user can choose (spec §6.7), independent of the Swing theme. CLASSIC, PASTEL, CSIM and
 * TWILIGHT take the "default", "pastel", "csim" and "Twilight" schemes of the original VSim
 * ({@code vsim/src/main/resources/config/colors.json}).
 */
public final class ColorSchemes {

    public static final String CLASSIC_ID = "classic";
    public static final String LIGHT_ID = "light";
    public static final String DARK_ID = "dark";
    public static final String PASTEL_ID = "pastel";
    public static final String CSIM_ID = "csim";
    public static final String TWILIGHT_ID = "twilight";

    /** VSim's colors: gray board, black 0, red 1, green Z, blue gates, purple labels, yellow selection. */
    public static final ColorScheme CLASSIC = new ColorScheme(
            new Color(0xBDBDBD),          // background      (VSim "background")
            new Color(0xCCCCCC),          // grid            (VSim "grid")
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
            new Color(0xFF0000));         // diagramTrace    (VSim "signal")

    /** Today's scheme (M4-0). */
    public static final ColorScheme LIGHT = ColorScheme.LIGHT;

    /** Goes with the FlatLaf dark theme; every wire state reaches 3:1 against the background. */
    public static final ColorScheme DARK = new ColorScheme(
            new Color(0x1E1F22),          // background
            new Color(0x393B40),          // grid
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
            new Color(0x7CFC7C));         // diagramTrace

    /** VSim's "pastel": gray board, black 0, coral 1, green Z, blue gates, orange labels. */
    public static final ColorScheme PASTEL = new ColorScheme(
            new Color(0xBDBDBD),          // background      (VSim "background")
            new Color(0xCCCCCC),          // grid            (VSim "grid")
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
            new Color(0x990033));         // diagramTrace    (VSim "signal")

    /** VSim's "csim": blue board, black 0, white 1, green Z, orange gates, yellow labels. */
    public static final ColorScheme CSIM = new ColorScheme(
            new Color(0x0033CC),          // background      (VSim "background")
            new Color(0x0033CC),          // grid            (VSim "grid": the same blue, so no visible dots)
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
            new Color(0xFF0000));         // diagramTrace    (VSim "signal")

    /** VSim's "Twilight": black board, gray 0, dark red 1, dark green Z, navy gates, blue labels. */
    public static final ColorScheme TWILIGHT = new ColorScheme(
            new Color(0x000000),          // background      (VSim "background")
            new Color(0x1C1C1C),          // grid            (VSim "grid")
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
            new Color(0x990099));         // diagramTrace    (VSim "signal")

    private ColorSchemes() {
    }

    /** Scheme ids in the order the preferences dialog lists them. */
    public static List<String> ids() {
        return List.of(CLASSIC_ID, LIGHT_ID, DARK_ID, PASTEL_ID, CSIM_ID, TWILIGHT_ID);
    }

    public static Optional<ColorScheme> byId(String id) {
        return switch (id) {
            case CLASSIC_ID -> Optional.of(CLASSIC);
            case LIGHT_ID -> Optional.of(LIGHT);
            case DARK_ID -> Optional.of(DARK);
            case PASTEL_ID -> Optional.of(PASTEL);
            case CSIM_ID -> Optional.of(CSIM);
            case TWILIGHT_ID -> Optional.of(TWILIGHT);
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
            default -> id;
        };
    }
}
