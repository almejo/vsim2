package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.ColorReference;

import java.awt.Color;
import java.util.function.Function;

/**
 * The Terminal's own colours (spec §8.1): the screen colours («Colores de pantalla», the background and the default
 * text) and the ANSI palettes («Paleta ANSI», the 16 colours of SGR 30–37, 90–97, 40–47 and 100–107). The values are
 * the mockup's {@code SCHEMES} and {@code ANSI_PALETTES}; indexes 16–255 are always the xterm cube and gray ramp and a
 * 24-bit colour is literal. A cell stores a reference and the painter resolves it here at paint time, so changing the
 * palette repaints the text already on the screen.
 */
public final class TerminalPalettes {

    /** The six levels of each channel of the xterm 6 × 6 × 6 cube (indexes 16–231). */
    private static final int[] CUBE_LEVELS = {0, 95, 135, 175, 215, 255};
    private static final int CUBE_START = 16;
    private static final int CUBE_SIDE = 6;
    private static final int GRAY_RAMP_START = 232;
    private static final int GRAY_RAMP_FIRST_LEVEL = 8;
    private static final int GRAY_RAMP_STEP = 10;
    /** SGR 1 shows colours 0–7 as their bright twins 8–15. */
    private static final int BRIGHT_OFFSET = 8;
    private static final int ANSI_COLORS = 16;

    private TerminalPalettes() {
    }

    /** «Colores de pantalla»: the phosphors and paper are fixed values; «Del tema de VSim» is the canvas scheme's. */
    public enum ScreenColors {
        GREEN("green", scheme -> new Color(0x33FF66), scheme -> new Color(0x0B130D)),
        AMBER("amber", scheme -> new Color(0xFFB000), scheme -> new Color(0x140D00)),
        WHITE("white", scheme -> new Color(0xE8E8E8), scheme -> new Color(0x101010)),
        PAPER("paper", scheme -> new Color(0x2A2A2A), scheme -> new Color(0xF6F1E3)),
        THEME("theme", ColorScheme::text, ColorScheme::background);

        private final String value;
        private final Function<ColorScheme, Color> text;
        private final Function<ColorScheme, Color> background;

        ScreenColors(String value, Function<ColorScheme, Color> text, Function<ColorScheme, Color> background) {
            this.value = value;
            this.text = text;
            this.background = background;
        }

        /** The choice saved as {@code value} in the «screen-colors» parameter; white for anything else. */
        public static ScreenColors of(Object value) {
            for (ScreenColors colors : values()) {
                if (colors.value.equals(String.valueOf(value))) {
                    return colors;
                }
            }
            return WHITE;
        }

        /** The default text colour, read from {@code scheme} now for «Del tema». */
        public Color text(ColorScheme scheme) {
            return text.apply(scheme);
        }

        /** The screen's background, read from {@code scheme} now for «Del tema». */
        public Color background(ColorScheme scheme) {
            return background.apply(scheme);
        }
    }

    /** «Paleta ANSI»: the 16 colours, black to bright white. */
    public enum AnsiPalette {
        XTERM("xterm", 0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
                0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF),
        VGA("vga", 0x000000, 0xAA0000, 0x00AA00, 0xAA5500, 0x0000AA, 0xAA00AA, 0x00AAAA, 0xAAAAAA,
                0x555555, 0xFF5555, 0x55FF55, 0xFFFF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFFFF),
        SOLARIZED("solarized", 0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
                0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3),
        /** «Para daltónicos», based on Okabe–Ito. */
        COLORBLIND("colorblind", 0x000000, 0xD55E00, 0x009E73, 0xF0E442, 0x0072B2, 0xCC79A7, 0x56B4E9, 0xE5E5E5,
                0x666666, 0xFF8C42, 0x2FD3A0, 0xFFF27A, 0x3D9BE0, 0xE8A3C6, 0x8FD0F5, 0xFFFFFF);

        private final String value;
        private final Color[] colors;

        AnsiPalette(String value, int... rgbs) {
            if (rgbs.length != ANSI_COLORS) {
                throw new IllegalArgumentException(value + " needs " + ANSI_COLORS + " colours");
            }
            this.value = value;
            this.colors = new Color[rgbs.length];
            for (int index = 0; index < rgbs.length; index++) {
                colors[index] = new Color(rgbs[index]);
            }
        }

        /** The choice saved as {@code value} in the «ansi-palette» parameter; xterm for anything else. */
        public static AnsiPalette of(Object value) {
            for (AnsiPalette palette : values()) {
                if (palette.value.equals(String.valueOf(value))) {
                    return palette;
                }
            }
            return XTERM;
        }

        /** Colour {@code index}, 0–15. */
        public Color color(int index) {
            return colors[index];
        }
    }

    /**
     * The colour {@code reference} asks for with {@code palette}: 0–15 from the palette (0–7 as their bright twins when
     * {@code bold}), 16–255 the xterm cube and gray ramp, a 24-bit colour as it is.
     */
    public static Color resolve(ColorReference reference, AnsiPalette palette, boolean bold) {
        return switch (reference) {
            case ColorReference.Rgb rgb -> new Color(rgb.rgb());
            case ColorReference.PaletteIndex paletteIndex -> {
                int index = paletteIndex.index();
                if (bold && index < BRIGHT_OFFSET) {
                    index += BRIGHT_OFFSET;
                }
                yield index < ANSI_COLORS ? palette.color(index) : color256(index);
            }
        };
    }

    /** Entry {@code index} (16–255) of xterm's 256-colour table: the 6 × 6 × 6 cube, then 24 grays. */
    static Color color256(int index) {
        if (index < GRAY_RAMP_START) {
            int value = index - CUBE_START;
            return new Color(CUBE_LEVELS[value / (CUBE_SIDE * CUBE_SIDE)], CUBE_LEVELS[value / CUBE_SIDE % CUBE_SIDE],
                    CUBE_LEVELS[value % CUBE_SIDE]);
        }
        int gray = GRAY_RAMP_FIRST_LEVEL + (index - GRAY_RAMP_START) * GRAY_RAMP_STEP;
        return new Color(gray, gray, gray);
    }
}
