package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.document.FrameTint;

import java.awt.Color;

/**
 * Colors of the circuit drawing, independent of the Swing theme. M4-D adds the other schemes; painters read the
 * scheme they are given on every paint and never cache colors.
 */
public record ColorScheme(Color background, Color grid, Color wireZero, Color wireOne, Color wireHighImpedance,
                          Color wireConflict, Color wireUnknown, Color componentStroke, Color componentFill,
                          Color componentShade, Color shadow, Color text, Color selection, Color preview,
                          Color displayBackground, Color segmentOn, Color segmentOff,
                          Color diagramBackground, Color diagramGrid, Color diagramTrace) {

    public static final ColorScheme LIGHT = new ColorScheme(
            new Color(0xF7F8FA),
            new Color(0xC9CED6),
            new Color(0x1B5E20),
            new Color(0x3CC23C),
            new Color(0x2962FF),
            new Color(0xE53935),
            new Color(0x8A8F98),
            new Color(0x263238),
            new Color(0xFFFFFF),
            new Color(0xE3E8EE),
            new Color(0, 0, 0, 40),
            new Color(0x263238),
            new Color(0x1E88E5),
            new Color(0x1E88E5),
            new Color(0x22272E),
            new Color(0xFF3B30),
            new Color(255, 255, 255, 28),
            new Color(0x22272E),
            new Color(255, 255, 255, 40),
            new Color(0x3CC23C));

    private static final int LIGHT_TINT_ALPHA = 40;
    private static final int DARK_TINT_ALPHA = 56;
    private static final int BORDER_ALPHA = 170;
    private static final int LIGHT_VEIL_ALPHA = 115;
    private static final int DARK_VEIL_ALPHA = 140;

    /** This scheme with {@code role} painted in {@code color} (M6.8 scheme editor). */
    public ColorScheme with(ColorRole role, Color color) {
        return ColorRole.scheme(each -> each == role ? color : each.get(this));
    }

    /** True when the board is dark, which picks the dark variant of the frame tints. */
    public boolean isDark() {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255;
        return luminance < 0.5;
    }

    /**
     * The fill of a frame of that tint: a hue at low alpha, so what lies under it stays readable. Each tint has a
     * light and a dark variant (chosen by {@link #isDark}); {@code NONE} is fully transparent.
     */
    public Color frameTint(FrameTint tint) {
        int rgb = switch (tint) {
            case NONE -> -1;
            case BLUE -> isDark() ? 0x4C8DFF : 0x1E78E6;
            case GREEN -> isDark() ? 0x4CC470 : 0x2EA043;
            case YELLOW -> isDark() ? 0xE6C84A : 0xE0A800;
            case RED -> isDark() ? 0xFF6B6B : 0xE5484D;
            case PURPLE -> isDark() ? 0xB07CFF : 0x8347AD;
            case GRAY -> isDark() ? 0xA0A6B0 : 0x7A808A;
        };
        if (rgb < 0) {
            return new Color(0, 0, 0, 0);
        }
        return new Color((rgb & 0xFFFFFF) | ((isDark() ? DARK_TINT_ALPHA : LIGHT_TINT_ALPHA) << 24), true);
    }

    /** The border of a frame. */
    public Color frameBorder() {
        return new Color((componentStroke.getRGB() & 0xFFFFFF) | (BORDER_ALPHA << 24), true);
    }

    /** The title of a frame; the scheme's text color, which reads on the board and on every tint. */
    public Color frameTitle() {
        return text;
    }

    /**
     * The dark translucent cover over what lies outside a frame while it is moved or resized: black at about 45 % on
     * a light board and 55 % on a dark one, so it darkens in every scheme.
     */
    public Color frameVeil() {
        return new Color(0, 0, 0, isDark() ? DARK_VEIL_ALPHA : LIGHT_VEIL_ALPHA);
    }
}
