package cl.almejo.vsim.ui.paint;

/**
 * The soft fills of a bus splitter's groups in the «Grupos» window and the Propiedades summary (one per pin, in pin
 * order, repeating after eight), the grey of unused bits and the text written on them, each in a light variant (pale
 * fills, dark text) and a dark one (deep fills, light text). The values are the approved mockup's (2026-11-01). Read
 * through {@link ColorScheme}.
 */
final class BitGroupPalette {

    /** Violet, sky, orange, green, rose, teal, yellow, blue: eight hues, none grey, so no pin looks like «sin usar». */
    private static final int[] LIGHT = {0xC9A8F0, 0x86CCF2, 0xFF9F5A, 0x8FE09E, 0xF598AE, 0x4FD1C5, 0xF2DC5B,
            0x7F8CF0};
    private static final int[] DARK = {0x8A3A9E, 0x2468B0, 0x9A4A10, 0x2F7428, 0xA52F4B, 0x077060, 0x6E6A00,
            0x5A48C8};
    private static final int LIGHT_UNUSED = 0xF0F0F0;
    private static final int DARK_UNUSED = 0x23272E;
    private static final int LIGHT_TEXT = 0x1F1F1F;
    private static final int DARK_TEXT = 0xE6E6E6;
    private static final int LIGHT_UNUSED_TEXT = 0x6E7580;
    private static final int DARK_UNUSED_TEXT = 0xA0A6B0;

    private BitGroupPalette() {
    }

    /** The fill of pin {@code pin} (from 0): the palette repeats after eight pins. */
    static int fill(int pin, boolean dark) {
        int[] palette = dark ? DARK : LIGHT;
        return palette[Math.floorMod(pin, palette.length)];
    }

    static int unused(boolean dark) {
        return dark ? DARK_UNUSED : LIGHT_UNUSED;
    }

    static int text(boolean dark) {
        return dark ? DARK_TEXT : LIGHT_TEXT;
    }

    static int unusedText(boolean dark) {
        return dark ? DARK_UNUSED_TEXT : LIGHT_UNUSED_TEXT;
    }
}
