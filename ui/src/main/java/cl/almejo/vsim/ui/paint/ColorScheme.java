package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.ui.theme.ReadableColors;

import java.awt.Color;
import java.util.Optional;

/**
 * Colors of the circuit drawing, independent of the Swing theme. M4-D adds the other schemes; painters read the
 * scheme they are given on every paint and never cache colors. {@code pad} is the colour of the pin marks (the dot
 * of a 1-bit pin, the square of a bus pin); leads, outlines, wire junctions and open-pin rings stay
 * {@code componentStroke}. {@code memoryRead} and {@code memoryWrite} colour the activity of a RAM or ROM (its light,
 * the row of the content window and the badge) for a read and for a write.
 */
public record ColorScheme(Color background, Color grid, Color gridLine, Color wireZero, Color wireOne, Color wireHighImpedance,
                          Color wireConflict, Color wireUnknown, Color componentStroke, Color componentFill,
                          Color componentShade, Color shadow, Color text, Color selection, Color preview,
                          Color displayBackground, Color segmentOn, Color segmentOff,
                          Color diagramBackground, Color diagramGrid, Color diagramTrace, Color pad,
                          Color memoryRead, Color memoryWrite) {

    /** The activity colours of a scheme that gives none: a blue for a read, an orange for a write (on a light fill). */
    private static final Color DEFAULT_MEMORY_READ = new Color(0x1E88E5);
    private static final Color DEFAULT_MEMORY_WRITE = new Color(0xF57C00);

    /**
     * A scheme whose pins are drawn like its outlines ({@code pad = componentStroke}) and whose memory activity has
     * the default blue and orange: used by tests and by schemes that name only the older colours.
     */
    public ColorScheme(Color background, Color grid, Color gridLine, Color wireZero, Color wireOne,
                       Color wireHighImpedance, Color wireConflict, Color wireUnknown, Color componentStroke,
                       Color componentFill, Color componentShade, Color shadow, Color text, Color selection,
                       Color preview, Color displayBackground, Color segmentOn, Color segmentOff,
                       Color diagramBackground, Color diagramGrid, Color diagramTrace) {
        this(background, grid, gridLine, wireZero, wireOne, wireHighImpedance, wireConflict, wireUnknown,
                componentStroke, componentFill, componentShade, shadow, text, selection, preview, displayBackground,
                segmentOn, segmentOff, diagramBackground, diagramGrid, diagramTrace, componentStroke,
                DEFAULT_MEMORY_READ, DEFAULT_MEMORY_WRITE);
    }

    /** A scheme whose pins are drawn like its outlines ({@code pad = componentStroke}): every scheme but «Placa». */
    public ColorScheme(Color background, Color grid, Color gridLine, Color wireZero, Color wireOne,
                       Color wireHighImpedance, Color wireConflict, Color wireUnknown, Color componentStroke,
                       Color componentFill, Color componentShade, Color shadow, Color text, Color selection,
                       Color preview, Color displayBackground, Color segmentOn, Color segmentOff,
                       Color diagramBackground, Color diagramGrid, Color diagramTrace, Color memoryRead,
                       Color memoryWrite) {
        this(background, grid, gridLine, wireZero, wireOne, wireHighImpedance, wireConflict, wireUnknown,
                componentStroke, componentFill, componentShade, shadow, text, selection, preview, displayBackground,
                segmentOn, segmentOff, diagramBackground, diagramGrid, diagramTrace, componentStroke, memoryRead,
                memoryWrite);
    }

    public static final ColorScheme LIGHT = new ColorScheme(
            new Color(0xF7F8FA),
            new Color(0xC9CED6),
            derivedGridLine(new Color(0xC9CED6)),
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
            new Color(0x3CC23C),
            DEFAULT_MEMORY_READ,
            DEFAULT_MEMORY_WRITE);

    private static final int LIGHT_TINT_ALPHA = 40;
    private static final int DARK_TINT_ALPHA = 56;
    private static final int BORDER_ALPHA = 170;
    private static final int LIGHT_VEIL_ALPHA = 115;
    private static final int DARK_VEIL_ALPHA = 140;
    /** How much of the dots' colour a derived grid line keeps (spec §4.6: very faint, 35–45 %). */
    private static final double GRID_LINE_OPACITY = 0.4;
    /** The ten-unit line is the grid line at about twice its alpha. */
    private static final int MAJOR_GRID_LINE_FACTOR = 2;
    private static final int GUIDE_ALPHA = 220;
    private static final double STICKY_FOLD_SHARE = 0.18;
    private static final Color STICKY_DARK_TEXT = new Color(0x1F1F1F);
    private static final Color STICKY_LIGHT_TEXT = new Color(0xF5F5F5);
    private static final int STICKY_LIGHT_SHADOW_ALPHA = 60;
    private static final int STICKY_DARK_SHADOW_ALPHA = 130;
    private static final int LIGHT_ALIGNMENT_GUIDE = 0xB4008C;
    private static final int DARK_ALIGNMENT_GUIDE = 0xFF66E0;

    /**
     * The grid line of a scheme that has none of its own (a custom scheme stored before the grid of lines): the
     * dots' colour {@code grid} at 40 % of its alpha, so it is drawn over the board as a faint line.
     */
    public static Color derivedGridLine(Color grid) {
        int alpha = (int) Math.round(grid.getAlpha() * GRID_LINE_OPACITY);
        return new Color((grid.getRGB() & 0xFFFFFF) | (alpha << 24), true);
    }

    /** The stronger line drawn every ten units: {@link #gridLine()} at twice its alpha (derived, not a role). */
    public Color gridLineMajor() {
        int alpha = Math.min(255, gridLine.getAlpha() * MAJOR_GRID_LINE_FACTOR);
        return new Color((gridLine.getRGB() & 0xFFFFFF) | (alpha << 24), true);
    }

    /** This scheme with {@code role} painted in {@code color} (M6.8 scheme editor). */
    public ColorScheme with(ColorRole role, Color color) {
        return ColorRole.scheme(each -> each == role ? color : each.get(this));
    }

    /** True when the board is dark, which picks the dark variant of the frame tints. */
    public boolean isDark() {
        return ReadableColors.perceivedBrightness(background) < 0.5;
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

    /**
     * The fill behind a note with that background ({@link NotePalette}): a pale tint on a light board, a deep one on a
     * dark board, always apart from the board; empty for {@code NONE}.
     */
    public Optional<Color> noteBackground(NoteColor color) {
        return NotePalette.background(color, background);
    }

    /**
     * The fill behind a note in {@code style}: as {@link #noteBackground(NoteColor)}, but deep under white text and pale
     * under black text whatever the board, so the text chosen reads on it.
     */
    public Optional<Color> noteBackground(NoteStyle style) {
        return NotePalette.background(style, background);
    }

    /**
     * The colour of a note's text: its colour made readable on its background (or the board), or for «Automático»
     * this scheme's text colour, black or white when that does not read on the note's background.
     */
    public Color noteText(NoteStyle style) {
        return NotePalette.text(style, background, text);
    }

    /** The border of a note in that colour, readable on the board; empty for {@code NONE}. */
    public Optional<Color> noteBorder(NoteColor color) {
        return NotePalette.border(color, background);
    }

    /**
     * The line around a note's box in {@code style}: its border, or, when it has none and its background is the
     * board's own colour (a black note on Twilight, a white one on Claro or on a white print), a quiet line that shows
     * where the box is. Empty when the note needs no line (a plain note keeps its look).
     */
    public Optional<Color> noteOutline(NoteStyle style) {
        return NotePalette.outline(style, background);
    }

    /**
     * The paper of a sticky note of that colour: a post-it, so a bright one on any board. On a light board it is pale;
     * on a dark board it is saturated and a little deeper (like the frame tints, derived from {@link #isDark}, so
     * every built-in scheme and the user's own get both). Its text and fold follow the paper's own brightness.
     */
    public Color stickyPaper(StickyColor color) {
        boolean dark = isDark();
        return new Color(switch (color) {
            case YELLOW -> dark ? 0xF0D44A : 0xFFF176;
            case PINK -> dark ? 0xEE9DB7 : 0xF8BBD0;
            case BLUE -> dark ? 0x7CC1F0 : 0xBBDEFB;
            case GREEN -> dark ? 0x86CE8C : 0xC8E6C9;
            case ORANGE -> dark ? 0xF4AD5E : 0xFFCC80;
        });
    }

    /** The folded corner of a sticky note: its paper in a darker shade when its text is dark, a lighter one otherwise. */
    public Color stickyFold(StickyColor color) {
        Color paper = stickyPaper(color);
        return mixWith(paper, stickyTextIsDark(paper) ? Color.BLACK : Color.WHITE, STICKY_FOLD_SHARE);
    }

    /**
     * The text of a sticky note: near black or near white, whichever reads better on that paper (the paper's own
     * brightness decides, not the theme: a bright paper on a dark board takes dark text); at least 4.5:1 on it.
     */
    public Color stickyText(StickyColor color) {
        return stickyTextIsDark(stickyPaper(color)) ? STICKY_DARK_TEXT : STICKY_LIGHT_TEXT;
    }

    private static boolean stickyTextIsDark(Color paper) {
        return ReadableColors.contrast(STICKY_DARK_TEXT, paper) >= ReadableColors.contrast(STICKY_LIGHT_TEXT, paper);
    }

    /** The soft shadow under a sticky note: translucent black, stronger on a dark board. */
    public Color stickyShadow() {
        return new Color(0, 0, 0, isDark() ? STICKY_DARK_SHADOW_ALPHA : STICKY_LIGHT_SHADOW_ALPHA);
    }

    private static Color mixWith(Color color, Color towards, double share) {
        return new Color((int) Math.round(color.getRed() + (towards.getRed() - color.getRed()) * share),
                (int) Math.round(color.getGreen() + (towards.getGreen() - color.getGreen()) * share),
                (int) Math.round(color.getBlue() + (towards.getBlue() - color.getBlue()) * share));
    }

    /**
     * The colour of a wire tunnel: its entrada's palette entry ({@code 0} .. {@code Tunnels.COLOR_COUNT - 1}), or grey
     * for any other index ({@code Tunnels.NO_COLOR}: a salida without entrada). Like the frame tints, each colour has a
     * light and a dark variant ({@link #isDark}), both readable on {@link #tunnelSurface()}.
     */
    public Color tunnelColor(int index) {
        return new Color(TunnelPalette.rgb(index, isDark()));
    }

    /** The inside of a tunnel's tag: white on a light board, a dark grey on a dark one, whatever the scheme's fill. */
    public Color tunnelSurface() {
        return new Color(TunnelPalette.surface(isDark()));
    }

    /** The hole of a tunnel's tag, where its wire goes in. */
    public Color tunnelHole() {
        return new Color(TunnelPalette.hole(isDark()));
    }

    /** The thin light rim around a tunnel's hole. */
    public Color tunnelHoleRim() {
        return new Color(TunnelPalette.holeRim(isDark()));
    }

    /**
     * The colour of the alignment guides (spec §6, derived, not a role): cyan as in Photoshop, slightly translucent so
     * it does not compete with the wires, a deep cyan on a light board and a light one on a dark board. Guides are
     * drawn dashed, so even next to a blue «Z» wire they read as an aid, not a wire.
     */
    public Color guide() {
        return isDark() ? new Color(0x4D, 0xD0, 0xE1, GUIDE_ALPHA) : new Color(0x00, 0x86, 0xA8, GUIDE_ALPHA);
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

    /**
     * The alignment lines drawn while a part is dragged (spec §3.3, §6): a magenta, like the smart guides of drawing
     * programs, apart from the blue selection and the wire colours; deep on a light board, light on a dark one, so it
     * reaches 3:1 against the background of every built-in scheme. Derived, not an editable role.
     */
    public Color alignmentGuide() {
        return new Color(isDark() ? DARK_ALIGNMENT_GUIDE : LIGHT_ALIGNMENT_GUIDE);
    }
}
