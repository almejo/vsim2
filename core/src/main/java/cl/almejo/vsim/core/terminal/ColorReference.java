package cl.almejo.vsim.core.terminal;

/**
 * A colour a cell asks for, never a resolved colour: the painter resolves it with the part's palette and screen
 * colours at paint time, so changing them repaints text already on the screen (spec §8.1).
 */
public sealed interface ColorReference {

    /** An entry of the 256-colour table: 0–15 the ANSI palette («Paleta ANSI»), 16–255 the xterm cube and grays. */
    record PaletteIndex(int index) implements ColorReference {

        public static final int PALETTE_SIZE = 256;

        public PaletteIndex {
            if (index < 0 || index >= PALETTE_SIZE) {
                throw new IllegalArgumentException("palette index must be in 0..255: " + index);
            }
        }
    }

    /** A literal 24-bit colour from {@code ESC[38;2;r;g;bm}, as {@code 0xRRGGBB}. */
    record Rgb(int rgb) implements ColorReference {

        public static final int MAXIMUM_RGB = 0xFF_FF_FF;

        public Rgb {
            if (rgb < 0 || rgb > MAXIMUM_RGB) {
                throw new IllegalArgumentException("rgb must be in 0..0xFFFFFF: " + Integer.toHexString(rgb));
            }
        }
    }
}
