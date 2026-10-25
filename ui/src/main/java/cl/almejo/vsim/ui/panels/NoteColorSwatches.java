package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.ui.i18n.NoteColorNames;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The notes' colour pickers, for whoever else paints with the notes' palette (the notes' inspector, the shape editor's
 * texts and figures): one swatch per colour as {@code role} shows it on the board now, read at paint time from the
 * scheme.
 */
public final class NoteColorSwatches {

    /** Swatches per row: the palette fits a side panel in two rows. */
    public static final int PER_ROW = 5;

    private NoteColorSwatches() {
    }

    /**
     * Swatches of {@code colors} for {@code role}: the fill of a background, the colour of a text or a border;
     * «Ninguno» is the crossed-out swatch and the text's «Automático» the scheme's text colour.
     */
    public static ColorSwatches<NoteColor> of(NoteStyle.Role role, List<NoteColor> colors, NoteColor selected,
                                              Supplier<ColorScheme> scheme) {
        return new ColorSwatches<>(colors, selected, color -> NoteColorNames.of(role, color),
                color -> fill(role, color, scheme.get()), PER_ROW);
    }

    private static Optional<Color> fill(NoteStyle.Role role, NoteColor color, ColorScheme scheme) {
        return switch (role) {
            case BACKGROUND -> scheme.noteBackground(color);
            case TEXT -> color == NoteColor.NONE ? Optional.of(scheme.text()) : scheme.noteBorder(color);
            case BORDER -> scheme.noteBorder(color);
        };
    }
}
