package cl.almejo.vsim.ui.karnaugh;

import java.awt.Color;

/**
 * The fixed colours of the Karnaugh groups (spec §3): one hue per group index, with a variant for light themes
 * (deeper) and one for dark themes (brighter), like the frame tints. The fill is the same hue, translucent, so cells
 * and overlapping groups stay readable; the group being explained gets a stronger fill.
 */
final class GroupPalette {

    private static final int[] LIGHT = {0x1E78E6, 0xE07000, 0x2EA043, 0x8347AD, 0x0096A0, 0xE5484D, 0xA88400,
        0xD6409F};
    private static final int[] DARK = {0x5C9DFF, 0xFFA040, 0x4CC470, 0xB07CFF, 0x3CD0D8, 0xFF6B6B, 0xE6C84A,
        0xFF70C0};
    private static final int LIGHT_FILL_ALPHA = 38;
    private static final int DARK_FILL_ALPHA = 56;
    private static final int EMPHASIZED_FILL_ALPHA = 90;

    private GroupPalette() {
    }

    static int size() {
        return LIGHT.length;
    }

    /** The outline and text colour of group colour {@code index} (wraps around the palette). */
    static Color color(int index, boolean dark) {
        int[] palette = dark ? DARK : LIGHT;
        return new Color(palette[Math.floorMod(index, palette.length)]);
    }

    /** The translucent fill of group colour {@code index}; stronger for the group being explained. */
    static Color fill(int index, boolean dark, boolean emphasized) {
        int alpha = emphasized ? EMPHASIZED_FILL_ALPHA : dark ? DARK_FILL_ALPHA : LIGHT_FILL_ALPHA;
        return new Color((color(index, dark).getRGB() & 0xFFFFFF) | alpha << 24, true);
    }
}
