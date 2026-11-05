package cl.almejo.vsim.ui.window.tabs;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The colour of each document's group of tabs (spec §4.2): a fixed palette of six, given by order of first opening in
 * the session, each with a light-theme and a dark-theme variant (the dark one lighter, so it reads on a dark strip).
 * Colours are made at call time, never cached by the caller: the theme changes live. The hues follow the decided
 * mockup (blue and orange first). EDT only.
 */
public final class GroupColors {

    /** Light-theme variants: deep enough for a band and a dot on a light strip. */
    private static final int[] LIGHT = {0x1B5FAE, 0xB25B12, 0x2E7D32, 0x7B3FA0, 0x00796B, 0xB03A48};
    /** Dark-theme variants of the same hues, light enough on a dark strip. */
    private static final int[] DARK = {0x8DC6FF, 0xF5B47A, 0x8FD694, 0xC9A0F0, 0x6FD6C8, 0xF2939E};
    /** Below this luminance (0–255) the panel background counts as a dark theme. */
    private static final double DARK_LUMINANCE = 128;

    private static final Map<Object, Integer> ORDINALS = new WeakHashMap<>();
    private static int nextOrdinal;

    private GroupColors() {
    }

    /** The colour of palette entry {@code ordinal} (any non-negative number; the palette repeats after six). */
    public static Color colorFor(int ordinal, boolean dark) {
        int[] palette = dark ? DARK : LIGHT;
        return new Color(palette[Math.floorMod(ordinal, palette.length)]);
    }

    /** The palette entry of {@code document}: assigned the first time it is asked, kept for the session. */
    public static synchronized int ordinalOf(Object document) {
        return ORDINALS.computeIfAbsent(document, key -> nextOrdinal++);
    }

    /** The colour of {@code document}'s group on the current theme. */
    public static Color colorOf(Object document) {
        return colorFor(ordinalOf(document), chromeIsDark());
    }

    /** Whether the window chrome (FlatLaf's panel background, read now) is dark. */
    public static boolean chromeIsDark() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            return false;
        }
        double luminance = 0.299 * background.getRed() + 0.587 * background.getGreen()
                + 0.114 * background.getBlue();
        return luminance < DARK_LUMINANCE;
    }
}
