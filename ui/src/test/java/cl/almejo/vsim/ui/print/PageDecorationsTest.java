package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class PageDecorationsTest {

    private static PageLayout threeByThree() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        return PageLayout.of(new Rectangle2D.Double(0, 0, 105, 125), geometry, new ScaleMode.Percentage(150));
    }

    /** The neighbours of sheet {@code index} when every sheet goes out. */
    private static PageDecorations.Neighbours neighboursOf(PageLayout layout, int index) {
        return PageDecorations.neighbours(layout, layout.page(index), PrintedSheets.all(layout));
    }

    @Test
    void aSkippedSheetIsNoNeighbour() {
        PageLayout layout = threeByThree();
        SheetGrid grid = SheetGrid.of(layout);
        List<Bounds> drawn = new ArrayList<>();
        for (int index : new int[] {1, 4, 8}) {
            Rectangle2D sheet = grid.sheetRectangle(index % 3, index / 3);
            drawn.add(new Bounds((int) sheet.getCenterX(), (int) sheet.getCenterY(), 1, 1));
        }
        PrintedSheets printed = PrintedSheets.of(grid, drawn, Optional.empty(), true);

        PageDecorations.Neighbours neighbours = PageDecorations.neighbours(layout, layout.page(4), printed);

        assertThat(neighbours.above()).as("sheet 2 goes out").hasValue(2);
        assertThat(neighbours.left()).as("sheet 4 is skipped").isEmpty();
        assertThat(neighbours.right()).isEmpty();
        assertThat(neighbours.below()).isEmpty();
    }

    @Test
    void theSheetInTheMiddleHasFourNeighboursNumberedInReadingOrder() {
        PageLayout layout = threeByThree();
        assertThat(layout.columns()).isEqualTo(3);
        assertThat(layout.rows()).isEqualTo(3);
        PageDecorations.Neighbours neighbours = neighboursOf(layout, 4);
        assertThat(neighbours.left()).hasValue(4);
        assertThat(neighbours.right()).hasValue(6);
        assertThat(neighbours.above()).hasValue(2);
        assertThat(neighbours.below()).hasValue(8);
    }

    @Test
    void aCornerSheetHasTwoNeighboursAndTheOppositeCornerTheOtherTwo() {
        PageLayout layout = threeByThree();
        PageDecorations.Neighbours first = neighboursOf(layout, 0);
        assertThat(first.left()).isEmpty();
        assertThat(first.above()).isEmpty();
        assertThat(first.right()).hasValue(2);
        assertThat(first.below()).hasValue(4);
        PageDecorations.Neighbours last = neighboursOf(layout, 8);
        assertThat(last.right()).isEmpty();
        assertThat(last.below()).isEmpty();
        assertThat(last.left()).hasValue(8);
        assertThat(last.above()).hasValue(6);
    }

    @Test
    void aSingleSheetHasNoNeighbours() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        PageLayout layout = PageLayout.of(new Rectangle2D.Double(0, 0, 10, 10), geometry, new ScaleMode.FitToOnePage());
        PageDecorations.Neighbours neighbours = neighboursOf(layout, 0);
        assertThat(neighbours).isEqualTo(new PageDecorations.Neighbours(OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.empty(), OptionalInt.empty()));
    }
}
