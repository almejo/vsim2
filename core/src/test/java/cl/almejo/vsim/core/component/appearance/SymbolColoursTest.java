package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SymbolColoursTest {

    private static SymbolPath triangle(NoteColor fill, NoteColor line, boolean filled) {
        return new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(32, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(16, 32), Optional.empty())), true, filled, fill, line);
    }

    @Test
    void theOldConstructorsMeanTheDefaultLook() {
        SymbolPath path = new SymbolPath(triangle(NoteColor.NONE, NoteColor.NONE, true).nodes(), true, true);
        SymbolText text = new SymbolText(new SymbolPoint(8, 8), Optional.of("A"), TextSize.SMALL, TextAlignment.LEFT);

        assertThat(path.fill()).isEqualTo(NoteColor.NONE);
        assertThat(path.line()).isEqualTo(NoteColor.NONE);
        assertThat(text.color()).isEqualTo(NoteColor.NONE);
        assertThat(text.border()).isEqualTo(NoteColor.NONE);
    }

    @Test
    void redIsNotOfferedForAFillAndIsRefused() {
        assertThat(SymbolPath.FILL_CHOICES).doesNotContain(NoteColor.RED).contains(NoteColor.NONE, NoteColor.BLUE);
        assertThatThrownBy(() -> triangle(NoteColor.RED, NoteColor.NONE, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(triangle(NoteColor.NONE, NoteColor.RED, true).line()).isEqualTo(NoteColor.RED);
    }

    @Test
    void aFillColourNeedsAFilledFigureAndUnfillingForgetsIt() {
        assertThatThrownBy(() -> triangle(NoteColor.BLUE, NoteColor.NONE, false))
                .isInstanceOf(IllegalArgumentException.class);
        SymbolPath coloured = triangle(NoteColor.BLUE, NoteColor.GREEN, true);

        SymbolPath unfilled = coloured.reshaped(coloured.nodes(), true, false);

        assertThat(unfilled.fill()).isEqualTo(NoteColor.NONE);
        assertThat(unfilled.line()).isEqualTo(NoteColor.GREEN);
        assertThat(coloured.reshaped(coloured.nodes(), true, true).fill()).isEqualTo(NoteColor.BLUE);
    }

    @Test
    void aTextMayHoldSeveralLinesButNotACarriageReturn() {
        SymbolText text = new SymbolText(new SymbolPoint(8, 8), Optional.of("uno\ndos"), TextSize.SMALL,
                TextAlignment.RIGHT);

        assertThat(text.lines()).containsExactly("uno", "dos");
        assertThatThrownBy(() -> new SymbolText(new SymbolPoint(8, 8), Optional.of("uno\rdos"), TextSize.SMALL,
                TextAlignment.RIGHT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movingKeepsTheColoursOfEveryElement() {
        SymbolText text = new SymbolText(new SymbolPoint(8, 8), Optional.of("A"), TextSize.SMALL, TextAlignment.LEFT,
                NoteColor.PURPLE, NoteColor.GRAY);
        SymbolDraft draft = SymbolDraft.of(new ComponentAppearance(new GridSize(4, 4), Optional.empty(), List.of()),
                        List.of(), false)
                .addFigure(triangle(NoteColor.BLUE, NoteColor.GREEN, true)).addText(text)
                .select(Set.of(0, 1)).moveSelection(8, 0, SnapMode.FREE);

        SymbolPath path = (SymbolPath) draft.elements().get(0);
        SymbolText moved = (SymbolText) draft.elements().get(1);
        assertThat(path.fill()).isEqualTo(NoteColor.BLUE);
        assertThat(path.line()).isEqualTo(NoteColor.GREEN);
        assertThat(moved.color()).isEqualTo(NoteColor.PURPLE);
        assertThat(moved.border()).isEqualTo(NoteColor.GRAY);
    }

    @Test
    void settingColoursOnAFigureAndOnATextIsOneDraftEditEach() {
        SymbolText text = new SymbolText(new SymbolPoint(8, 8), Optional.of("A"), TextSize.SMALL, TextAlignment.LEFT);
        SymbolDraft draft = SymbolDraft.of(new ComponentAppearance(new GridSize(4, 4), Optional.empty(), List.of()),
                        List.of(), false)
                .addFigure(triangle(NoteColor.NONE, NoteColor.NONE, true)).addText(text);

        SymbolDraft coloured = draft.setFigureColours(0, NoteColor.GREEN, NoteColor.PURPLE)
                .editText(1, text.coloured(NoteColor.BLUE, NoteColor.GRAY));

        assertThat(((SymbolPath) coloured.elements().get(0)).fill()).isEqualTo(NoteColor.GREEN);
        assertThat(((SymbolPath) coloured.elements().get(0)).line()).isEqualTo(NoteColor.PURPLE);
        assertThat(((SymbolText) coloured.elements().get(1)).color()).isEqualTo(NoteColor.BLUE);
    }
}
