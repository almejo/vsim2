package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * The circuit color schemes the user can choose (spec §6.7), independent of the Swing theme. CLASSIC takes the
 * "default" scheme of the original VSim ({@code vsim/src/main/resources/config/colors.json}).
 */
public final class ColorSchemes {

    public static final String CLASSIC_ID = "classic";
    public static final String LIGHT_ID = "light";
    public static final String DARK_ID = "dark";

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

    private ColorSchemes() {
    }

    /** Scheme ids in the order the preferences dialog lists them. */
    public static List<String> ids() {
        return List.of(CLASSIC_ID, LIGHT_ID, DARK_ID);
    }

    public static Optional<ColorScheme> byId(String id) {
        return switch (id) {
            case CLASSIC_ID -> Optional.of(CLASSIC);
            case LIGHT_ID -> Optional.of(LIGHT);
            case DARK_ID -> Optional.of(DARK);
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
            default -> id;
        };
    }
}
