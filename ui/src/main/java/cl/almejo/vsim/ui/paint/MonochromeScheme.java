package cl.almejo.vsim.ui.paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Objects;

/**
 * «Blanco y negro» (printing spec §3.3): black strokes on white, light fills and grey levels only, the same whatever
 * scheme the screen uses, so a black and white PDF looks the same from every computer. Wire states are told apart by
 * grey level and dash pattern ({@link #monochromeLook}). The computed colours a {@link ColorScheme} cannot carry
 * (frame tints, tunnel palette) have their own grey here, and {@link MonochromeGraphics} greys whatever a painter
 * still picks by itself.
 */
public final class MonochromeScheme {

    /** The 1 of a wire, every component outline and the text. */
    public static final Color BLACK = Color.BLACK;
    /** The 0 of a wire. */
    public static final Color MID_GREY = new Color(0x7F7F7F);
    /** A floating (Z) or unknown wire. */
    public static final Color LIGHT_GREY = new Color(0xB4B4B4);
    /** The border of a frame; frames have no fill in black and white (agreed with the user 2026-10-20). */
    public static final Color FRAME_BORDER = new Color(0x505050);
    /** The tag, arrow and name of every tunnel, whatever its palette colour on screen. */
    public static final Color TUNNEL = new Color(0x3C3C3C);

    private static final float WIRE_WIDTH = 2.5f;
    private static final float BUS_WIDTH = 4.5f;
    private static final float CONFLICT_WIDTH = 5f;
    private static final float[] DASHES = {6f, 4f};
    private static final float[] DOTS = {0.1f, 6f};
    private static final float[] CONFLICT_DASHES = {9f, 4f};

    private static final ColorScheme SCHEME = new ColorScheme(
            Color.WHITE,
            new Color(0xC8C8C8),
            MID_GREY,
            BLACK,
            LIGHT_GREY,
            BLACK,
            LIGHT_GREY,
            BLACK,
            Color.WHITE,
            new Color(0xEBEBEB),
            new Color(0, 0, 0, 28),
            BLACK,
            new Color(0x606060),
            new Color(0x606060),
            new Color(0xE6E6E6),
            BLACK,
            new Color(0, 0, 0, 22),
            new Color(0xF2F2F2),
            new Color(0, 0, 0, 40),
            BLACK);

    /** How a wire is drawn on paper: its grey and its stroke (solid, dashed or dotted). */
    public record WireLook(Color color, BasicStroke stroke) {
    }

    private MonochromeScheme() {
    }

    /** The black and white scheme; {@code scheme} (the one on screen) does not change it. */
    public static ColorScheme of(ColorScheme scheme) {
        Objects.requireNonNull(scheme, "scheme");
        return SCHEME;
    }

    /**
     * A wire in {@code state}: 1 black solid, 0 mid grey solid, Z light grey dashed, unknown light grey dotted,
     * conflict black thick dashed; without values ({@link WireState#NO_VALUE}) a plain black line, like a schematic.
     * A bus keeps its thickness.
     */
    public static WireLook monochromeLook(WireState state, boolean bus) {
        float width = bus ? BUS_WIDTH : WIRE_WIDTH;
        return switch (state) {
            case ONE, NO_VALUE -> new WireLook(BLACK, solid(width));
            case ZERO -> new WireLook(MID_GREY, solid(width));
            case HIGH_IMPEDANCE -> new WireLook(LIGHT_GREY, patterned(width, BasicStroke.CAP_BUTT, DASHES));
            case UNKNOWN -> new WireLook(LIGHT_GREY, patterned(width, BasicStroke.CAP_ROUND, DOTS));
            case CONFLICT -> new WireLook(BLACK,
                    patterned(Math.max(CONFLICT_WIDTH, width + 1), BasicStroke.CAP_BUTT, CONFLICT_DASHES));
        };
    }

    /** Whether {@code scheme} is the black and white one: painters that draw a state by colour switch to shapes. */
    public static boolean isMonochrome(ColorScheme scheme) {
        return scheme == SCHEME;
    }

    /** {@code color} as a grey of the same luminance, keeping its alpha. */
    public static Color grey(Color color) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        if (red == green && green == blue) {
            return color;
        }
        int level = (int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue);
        return new Color(level, level, level, color.getAlpha());
    }

    private static BasicStroke solid(float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static BasicStroke patterned(float width, int cap, float[] pattern) {
        float[] scaled = new float[pattern.length];
        for (int index = 0; index < pattern.length; index++) {
            scaled[index] = pattern[index] * width / WIRE_WIDTH;
        }
        return new BasicStroke(width, cap, BasicStroke.JOIN_ROUND, 10f, scaled, 0f);
    }
}
