package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.ui.theme.ReadableColors;

import java.awt.Color;
import java.util.Optional;

/**
 * The colours of a note's background, text and border (Pulido T5), worked out for the board they are drawn on, so
 * the same names read in every scheme, the built-in ones («Placa» included) and the user's own:
 * <ul>
 *   <li>a background is a pale tint on a light board and a deep one on a dark board (deep under white text, pale
 *   under black text, whatever the board); when that tint is too close to the board (a green note on «Placa»'s
 *   green), it is moved away from it until the box shows;</li>
 *   <li>a text colour is a deep hue on a light surface (the note's background, or the board when it has none) and a
 *   bright one on a dark surface, darkened or lightened until it reaches {@link #MINIMUM_TEXT_CONTRAST}:1 there;
 *   «Automático» is the scheme's text colour, or black or white when that does not read on the note's
 *   background;</li>
 *   <li>a border is the text hue measured against the board, so it reads around the box.</li>
 * </ul>
 * Black and white are always exactly black and white. Read through {@link ColorScheme}; nothing is cached.
 */
final class NotePalette {

    /** WCAG AA for normal text. */
    static final double MINIMUM_TEXT_CONTRAST = ReadableColors.MINIMUM_TEXT_CONTRAST;
    /** How far (in RGB units) a background must stand from the board to show as a box. */
    static final double MINIMUM_BACKGROUND_DISTANCE = 60;
    /** A colour that does not read yet moves towards black or white in this many steps, the last one reaching it. */
    private static final int STEPS = 20;

    private NotePalette() {
    }

    /** The fill behind a note of that background whose text is automatic (the inspector's swatch); empty for NONE. */
    static Optional<Color> background(NoteColor color, Color board) {
        return background(new NoteStyle(color, NoteColor.NONE, NoteColor.NONE), board);
    }

    /**
     * The fill behind a note in {@code style}; empty without a background. The tint is deep on a dark board and pale
     * on a light one, except under white text (always deep) or black text (always pale), so the text chosen reads.
     */
    static Optional<Color> background(NoteStyle style, Color board) {
        NoteColor color = style.background();
        if (color == NoteColor.NONE) {
            return Optional.empty();
        }
        boolean deep = switch (style.text()) {
            case WHITE -> true;
            case BLACK -> false;
            default -> isDark(board);
        };
        Color tint = new Color(switch (color) {
            case NONE -> throw new IllegalStateException("NONE has no background");
            case BLUE -> deep ? 0x1F3A66 : 0xD6E6FB;
            case GREEN -> deep ? 0x1E5631 : 0xD4F0DA;
            case YELLOW -> deep ? 0x5C4A0E : 0xFFF0A6;
            case RED -> deep ? 0x5E2224 : 0xFCDADA;
            case PURPLE -> deep ? 0x40295F : 0xEADCF5;
            case GRAY -> deep ? 0x3A3F47 : 0xE1E4E8;
            case BLACK -> 0x000000;
            case WHITE -> 0xFFFFFF;
        });
        if (color == NoteColor.BLACK || color == NoteColor.WHITE) {
            return Optional.of(tint);
        }
        return Optional.of(apartFrom(board, tint));
    }

    /** {@code tint}, moved away from {@code board} (lighter on a dark board, darker on a light one) until it shows. */
    private static Color apartFrom(Color board, Color tint) {
        Color away = isDark(board) ? Color.WHITE : Color.BLACK;
        Color moved = tint;
        for (int step = 1; distance(moved, board) < MINIMUM_BACKGROUND_DISTANCE && step <= STEPS; step++) {
            moved = mix(tint, away, (double) step / STEPS);
        }
        return moved;
    }

    /** The colour of a note's text, in {@code style} on {@code board} where the scheme's text is {@code schemeText}. */
    static Color text(NoteStyle style, Color board, Color schemeText) {
        Optional<Color> fill = background(style, board);
        if (style.text() == NoteColor.NONE) {
            // without a background the note's text is the scheme's, exactly as before styles existed
            return fill.map(surface -> automaticText(surface, schemeText)).orElse(schemeText);
        }
        return readable(style.text(), fill.orElse(board));
    }

    /** The border of a note; empty for {@code NONE}. */
    static Optional<Color> border(NoteColor color, Color board) {
        return color == NoteColor.NONE ? Optional.empty() : Optional.of(readable(color, board));
    }

    /** The scheme's text colour where it reads on {@code surface}, else black or white, whichever reads better. */
    private static Color automaticText(Color surface, Color schemeText) {
        if (ReadableColors.contrast(schemeText, surface) >= MINIMUM_TEXT_CONTRAST) {
            return schemeText;
        }
        return ReadableColors.contrast(Color.BLACK, surface) >= ReadableColors.contrast(Color.WHITE, surface)
                ? Color.BLACK : Color.WHITE;
    }

    /** {@code color}'s hue for {@code surface}, pushed towards black or white until it reads there. */
    private static Color readable(NoteColor color, Color surface) {
        boolean dark = isDark(surface);
        Color hue = new Color(switch (color) {
            case NONE -> throw new IllegalArgumentException("NONE has no hue");
            case BLUE -> dark ? 0x8AB4FF : 0x1557B0;
            case GREEN -> dark ? 0x6FDC8C : 0x18792E;
            case YELLOW -> dark ? 0xFFD84D : 0x7A5900;
            case RED -> dark ? 0xFF8A80 : 0xC62828;
            case PURPLE -> dark ? 0xCBA6FF : 0x8347AD;
            case GRAY -> dark ? 0xB8BEC6 : 0x5A616B;
            case BLACK -> 0x000000;
            case WHITE -> 0xFFFFFF;
        });
        if (color == NoteColor.BLACK || color == NoteColor.WHITE) {
            return hue;
        }
        Color towards = dark ? Color.WHITE : Color.BLACK;
        Color shade = hue;
        for (int step = 1; ReadableColors.contrast(shade, surface) < MINIMUM_TEXT_CONTRAST && step <= STEPS; step++) {
            shade = mix(hue, towards, (double) step / STEPS);
        }
        return shade;
    }

    /** The same rule as {@link ColorScheme#isDark}: a board under half luminance is dark. */
    private static boolean isDark(Color surface) {
        double luminance = (0.2126 * surface.getRed() + 0.7152 * surface.getGreen() + 0.0722 * surface.getBlue())
                / 255;
        return luminance < 0.5;
    }

    private static Color mix(Color color, Color towards, double share) {
        return new Color(channel(color.getRed(), towards.getRed(), share),
                channel(color.getGreen(), towards.getGreen(), share),
                channel(color.getBlue(), towards.getBlue(), share));
    }

    private static int channel(int from, int to, double share) {
        return (int) Math.round(from + (to - from) * share);
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}
