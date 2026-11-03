package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class PageDecorationsTest {

    private static PageLayout threeByThree() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        return PageLayout.of(new Rectangle2D.Double(0, 0, 105, 125), geometry, new ScaleMode.Percentage(150));
    }

    @Test
    void theSheetInTheMiddleHasFourNeighboursNumberedInReadingOrder() {
        PageLayout layout = threeByThree();
        assertThat(layout.columns()).isEqualTo(3);
        assertThat(layout.rows()).isEqualTo(3);
        PageDecorations.Neighbours neighbours = PageDecorations.neighbours(layout, layout.page(4));
        assertThat(neighbours.left()).hasValue(4);
        assertThat(neighbours.right()).hasValue(6);
        assertThat(neighbours.above()).hasValue(2);
        assertThat(neighbours.below()).hasValue(8);
    }

    @Test
    void aCornerSheetHasTwoNeighboursAndTheOppositeCornerTheOtherTwo() {
        PageLayout layout = threeByThree();
        PageDecorations.Neighbours first = PageDecorations.neighbours(layout, layout.page(0));
        assertThat(first.left()).isEmpty();
        assertThat(first.above()).isEmpty();
        assertThat(first.right()).hasValue(2);
        assertThat(first.below()).hasValue(4);
        PageDecorations.Neighbours last = PageDecorations.neighbours(layout, layout.page(8));
        assertThat(last.right()).isEmpty();
        assertThat(last.below()).isEmpty();
        assertThat(last.left()).hasValue(8);
        assertThat(last.above()).hasValue(6);
    }

    @Test
    void aSingleSheetHasNoNeighbours() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        PageLayout layout = PageLayout.of(new Rectangle2D.Double(0, 0, 10, 10), geometry, new ScaleMode.FitToOnePage());
        PageDecorations.Neighbours neighbours = PageDecorations.neighbours(layout, layout.page(0));
        assertThat(neighbours).isEqualTo(new PageDecorations.Neighbours(OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.empty(), OptionalInt.empty()));
    }
}
