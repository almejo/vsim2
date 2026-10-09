package cl.almejo.vsim.ui.paint;

/** Segments a..g (bits 0..6) lit for each hexadecimal digit, as on a common 7-segment display. */
public final class SevenSegmentGlyph {

    private static final int[] SEGMENTS = {
            0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F, 0x77, 0x7C, 0x39, 0x5E, 0x79, 0x71};

    private SevenSegmentGlyph() {
    }

    public static int segments(int digit) {
        return SEGMENTS[digit & 0xF];
    }

    public static boolean isLit(int digit, int segment) {
        return (segments(digit) >>> segment & 1) == 1;
    }
}
