package cl.almejo.vsim.ui.paint;

/**
 * The eight hues of wire tunnels (spec §5) and the grey of a salida without entrada, each in a light variant (readable
 * on a white tag, contrast at least 3.6:1) and a dark one (readable on a dark tag). Red comes first, so it is the
 * colour of a circuit's first entrada (the user's choice); then the sketch's cyan and amber, purple, green, blue, pink
 * and brown. The exact values keep every two colours, the grey included, at least 80 RGB units apart. Read through
 * {@link ColorScheme}.
 */
final class TunnelPalette {

    private static final int[] LIGHT = {0xC0140A, 0x1D85AC, 0x8C6B18, 0x570BB7, 0x0D901C, 0x162F75, 0x99155A,
            0x4A392A};
    private static final int[] DARK = {0xFF4040, 0x99F0F6, 0xF6CA60, 0xC235FA, 0x3FE765, 0x4095E3, 0xF47CB0,
            0xC78253};
    private static final int LIGHT_GREY = 0x6E7580;
    private static final int DARK_GREY = 0xA0A6B0;
    private static final int LIGHT_SURFACE = 0xFFFFFF;
    private static final int DARK_SURFACE = 0x1D2127;
    private static final int LIGHT_HOLE = 0x1B1E24;
    private static final int DARK_HOLE = 0x050608;
    private static final int LIGHT_HOLE_RIM = 0x8A93A1;
    private static final int DARK_HOLE_RIM = 0x9AA3AF;

    private TunnelPalette() {
    }

    /** The RGB of palette entry {@code index}, or of the grey when {@code index} is outside the palette. */
    static int rgb(int index, boolean dark) {
        int[] palette = dark ? DARK : LIGHT;
        if (index < 0 || index >= palette.length) {
            return dark ? DARK_GREY : LIGHT_GREY;
        }
        return palette[index];
    }

    static int surface(boolean dark) {
        return dark ? DARK_SURFACE : LIGHT_SURFACE;
    }

    static int hole(boolean dark) {
        return dark ? DARK_HOLE : LIGHT_HOLE;
    }

    static int holeRim(boolean dark) {
        return dark ? DARK_HOLE_RIM : LIGHT_HOLE_RIM;
    }
}
