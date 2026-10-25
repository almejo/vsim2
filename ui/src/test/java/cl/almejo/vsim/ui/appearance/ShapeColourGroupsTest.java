package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.panels.ColorSwatches;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The shape editor's colour pickers for figures (fill, line) and texts (letters, border). */
class ShapeColourGroupsTest {
    private final ComponentDefinition definition = ShapeEditorPanelTest.definition();
    private final ShapeEditorPanel panel = new ShapeEditorPanel(new ShapeEditRequest(definition,
            ComponentAppearance.of(definition), ShapeEditScope.INSTANCE, true, false, "A2", 2, "U1"),
            AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);

    private SymbolDraft withFigureAndText() {
        return SymbolDraft.of(panel.appearance(), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE)
                .addText(new SymbolText(new SymbolPoint(8, 8), Optional.of("X"), TextSize.MEDIUM, TextAlignment.LEFT));
    }

    @Test
    void theFigureGroupShowsOnlyWhileAFigureIsSelectedAndNeverOffersRedForTheFill() {
        SymbolDraft draft = withFigureAndText();
        panel.apply(draft.select(Set.of()));
        assertThat(panel.figureGroupVisible()).isFalse();

        panel.apply(draft.select(Set.of(0)));

        assertThat(panel.figureGroupVisible()).isTrue();
        assertThat(panel.figureFills().values()).doesNotContain(NoteColor.RED).contains(NoteColor.GREEN);
        assertThat(panel.figureLines().values()).contains(NoteColor.RED);
    }

    @Test
    void pickingAFillAndALineColoursTheSelectedFigureAndEachPickIsOneStep() {
        panel.apply(withFigureAndText().select(Set.of(0)));

        pick(panel.figureFills(), NoteColor.GREEN);
        pick(panel.figureLines(), NoteColor.PURPLE);

        SymbolPath path = (SymbolPath) panel.draft().elements().getFirst();
        assertThat(path.fill()).isEqualTo(NoteColor.GREEN);
        assertThat(path.line()).isEqualTo(NoteColor.PURPLE);
        assertThat(path.filled()).isTrue();
    }

    @Test
    void pickingColoursForATextKeepsItsOtherSettings() {
        SymbolDraft draft = withFigureAndText();
        int textIndex = draft.elements().size() - 1;
        panel.apply(draft.select(Set.of(textIndex)));

        pick(panel.textColors(), NoteColor.BLUE);
        pick(panel.textBorders(), NoteColor.GRAY);

        SymbolText text = (SymbolText) panel.draft().elements().get(textIndex);
        assertThat(text.color()).isEqualTo(NoteColor.BLUE);
        assertThat(text.border()).isEqualTo(NoteColor.GRAY);
        assertThat(text.size()).isEqualTo(TextSize.MEDIUM);
        assertThat(text.text()).contains("X");
    }

    @Test
    void severalFiguresWithDifferentColoursShowNoChoice() {
        SymbolDraft draft = withFigureAndText().setFigureColours(0, NoteColor.GREEN, NoteColor.NONE)
                .addFigure(SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(16, 16), false));

        panel.apply(draft.select(Set.of(0, draft.elements().size() - 1)));

        assertThat(panel.figureFills().showsNoChoice()).isTrue();
    }

    private static void pick(ColorSwatches<NoteColor> swatches, NoteColor color) {
        swatches.swatch(swatches.values().indexOf(color)).doClick(0);
    }
}
