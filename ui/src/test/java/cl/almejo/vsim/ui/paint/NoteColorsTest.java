package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pulido T5: the note colours read on every built-in scheme, «Placa» included, and on the white board of «Fondo blanco
 * para imprimir»: every text ≥ 4.5:1 on its background or on the board, every border ≥ 3:1 on the board, and every
 * tinted background stands apart from the board.
 */
class NoteColorsTest {

    private static final double READABLE = 4.5;
    private static final double BORDER_VISIBLE = 3.0;
    private static final List<NoteColor> HUES = List.of(NoteColor.BLUE, NoteColor.GREEN, NoteColor.YELLOW,
            NoteColor.RED, NoteColor.PURPLE, NoteColor.GRAY);

    private static List<ColorScheme> schemes() {
        List<ColorScheme> schemes = new ArrayList<>();
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            schemes.add(scheme);
            schemes.add(CircuitImage.printScheme(scheme));
        }
        return schemes;
    }

    @Test
    void aPlainNoteIsPaintedInTheSchemesTextColourWithNoBoxAsAlways() {
        for (ColorScheme scheme : schemes()) {
            assertThat(scheme.noteText(NoteStyle.PLAIN)).isEqualTo(scheme.text());
            assertThat(scheme.noteBackground(NoteColor.NONE)).isEmpty();
            assertThat(scheme.noteBorder(NoteColor.NONE)).isEmpty();
        }
    }

    @Test
    void everyTextColourReadsOnTheBoardAndOnEveryBackground() {
        for (ColorScheme scheme : schemes()) {
            for (NoteColor background : NoteColor.values()) {
                for (NoteColor text : texts(background)) {
                    NoteStyle style = new NoteStyle(background, text, NoteColor.NONE);
                    Color surface = scheme.noteBackground(style).orElse(scheme.background());
                    assertThat(ReadableColors.contrast(scheme.noteText(style), surface))
                            .as("%s text on %s in %s", text, background, scheme.background())
                            .isGreaterThanOrEqualTo(READABLE);
                }
            }
        }
    }

    @Test
    void everyBorderShowsOnTheBoard() {
        for (ColorScheme scheme : schemes()) {
            for (NoteColor hue : HUES) {
                assertThat(ReadableColors.contrast(scheme.noteBorder(hue).orElseThrow(), scheme.background()))
                        .as("%s border on %s", hue, scheme.background()).isGreaterThanOrEqualTo(BORDER_VISIBLE);
            }
        }
    }

    @Test
    void everyTintedBackgroundStandsApartFromTheBoard() {
        for (ColorScheme scheme : schemes()) {
            for (NoteColor hue : HUES) {
                Color fill = scheme.noteBackground(hue).orElseThrow();
                assertThat(distance(fill, scheme.background())).as("%s on %s", hue, scheme.background())
                        .isGreaterThanOrEqualTo(NotePalette.MINIMUM_BACKGROUND_DISTANCE);
            }
        }
    }

    /**
     * A black note on Twilight's black board, a white one on Claro or on any white print: the box would vanish, so it
     * gets a quiet outline that shows on the board; a note with its own border keeps it, and a plain note or a note
     * whose background already stands apart gets no line.
     */
    @Test
    void aBackgroundOfTheBoardsColourGetsAQuietOutlineAndNothingElseChanges() {
        NoteStyle blackNote = new NoteStyle(NoteColor.BLACK, NoteColor.NONE, NoteColor.NONE);
        NoteStyle whiteNote = new NoteStyle(NoteColor.WHITE, NoteColor.NONE, NoteColor.NONE);
        List<ColorScheme> boards = List.of(ColorSchemes.TWILIGHT, ColorSchemes.LIGHT,
                CircuitImage.printScheme(ColorSchemes.DARK));
        List<NoteStyle> notes = List.of(blackNote, whiteNote, whiteNote);
        for (int index = 0; index < boards.size(); index++) {
            ColorScheme scheme = boards.get(index);
            Color line = scheme.noteOutline(notes.get(index)).orElseThrow();
            assertThat(ReadableColors.contrast(line, scheme.background())).as("on %s", scheme.background())
                    .isGreaterThanOrEqualTo(BORDER_VISIBLE)
                    .isLessThan(READABLE); // quiet: not as loud as a chosen border
        }
        for (ColorScheme scheme : schemes()) {
            assertThat(scheme.noteOutline(NoteStyle.PLAIN)).isEmpty();
            assertThat(scheme.noteOutline(new NoteStyle(NoteColor.NONE, NoteColor.RED, NoteColor.NONE))).isEmpty();
            for (NoteColor hue : HUES) {
                assertThat(scheme.noteOutline(new NoteStyle(hue, NoteColor.NONE, NoteColor.NONE)))
                        .as("%s stands apart on %s", hue, scheme.background()).isEmpty();
                assertThat(scheme.noteOutline(new NoteStyle(NoteColor.WHITE, NoteColor.NONE, hue)))
                        .isEqualTo(scheme.noteBorder(hue));
            }
        }
    }

    @Test
    void blackAndWhiteAreExactlyBlackAndWhite() {
        ColorScheme placa = ColorSchemes.PLACA;
        assertThat(placa.noteBackground(NoteColor.BLACK)).contains(Color.BLACK);
        assertThat(placa.noteBackground(NoteColor.WHITE)).contains(Color.WHITE);
        assertThat(placa.noteText(new NoteStyle(NoteColor.WHITE, NoteColor.BLACK, NoteColor.NONE)))
                .isEqualTo(Color.BLACK);
        assertThat(placa.noteBorder(NoteColor.WHITE)).isEqualTo(Optional.of(Color.WHITE));
    }

    /**
     * The text colours checked on {@code background}: every hue, and on a background «Automático» (without one it is
     * the scheme's text, as ever: {@code ColorSchemesTest} checks it) and black and white (the user's exact choice,
     * which the background follows: deep under white, pale under black), except on a background of the same colour.
     */
    private static List<NoteColor> texts(NoteColor background) {
        List<NoteColor> texts = new ArrayList<>(HUES);
        if (background == NoteColor.NONE) {
            return texts;
        }
        texts.add(NoteColor.NONE);
        if (background != NoteColor.WHITE) {
            texts.add(NoteColor.WHITE);
        }
        if (background != NoteColor.BLACK) {
            texts.add(NoteColor.BLACK);
        }
        return texts;
    }

    @Test
    void whiteTextGetsADeepBackgroundAndBlackTextAPaleOneOnAnyBoard() {
        NoteStyle whiteOnPurple = new NoteStyle(NoteColor.PURPLE, NoteColor.WHITE, NoteColor.NONE);
        NoteStyle blackOnPurple = new NoteStyle(NoteColor.PURPLE, NoteColor.BLACK, NoteColor.NONE);

        Color deep = ColorSchemes.LIGHT.noteBackground(whiteOnPurple).orElseThrow();
        Color pale = ColorSchemes.DARK.noteBackground(blackOnPurple).orElseThrow();

        assertThat(ReadableColors.contrast(deep, Color.WHITE)).isGreaterThan(ReadableColors.contrast(deep, Color.BLACK));
        assertThat(ReadableColors.contrast(pale, Color.BLACK)).isGreaterThan(ReadableColors.contrast(pale, Color.WHITE));
        assertThat(ColorSchemes.LIGHT.noteBackground(NoteColor.PURPLE)).as("automatic text: pale on a light board")
                .isNotEqualTo(Optional.of(deep));
    }

    private static double distance(Color first, Color second) {
        return Math.sqrt(Arrays.stream(new int[]{first.getRed() - second.getRed(),
                first.getGreen() - second.getGreen(), first.getBlue() - second.getBlue()})
                .mapToDouble(delta -> delta * delta).sum());
    }
}
