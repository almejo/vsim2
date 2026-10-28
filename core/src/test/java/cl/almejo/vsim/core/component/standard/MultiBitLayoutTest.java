package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiBitLayoutTest {

    @Test
    void theBoxGrowsWithTheBitsAndWrapsInRowsOfEightFromNineBits() {
        assertThat(MultiBitLayout.boxFor(1)).isEqualTo(new GridSize(2, 3));
        assertThat(MultiBitLayout.boxFor(4)).isEqualTo(new GridSize(5, 3));
        assertThat(MultiBitLayout.boxFor(8)).isEqualTo(new GridSize(10, 3));
        assertThat(MultiBitLayout.boxFor(9)).isEqualTo(new GridSize(10, 5));
        assertThat(MultiBitLayout.boxFor(16)).isEqualTo(new GridSize(10, 5));
        assertThat(MultiBitLayout.boxFor(32)).isEqualTo(new GridSize(10, 9));
    }

    @Test
    void theMostSignificantBitIsTheLeftmostCellOfTheTopRow() {
        List<MultiBitLayout.BitCell> cells = MultiBitLayout.cells(8);

        assertThat(cells).hasSize(8);
        assertThat(cells.get(0).bit()).isEqualTo(7);
        assertThat(cells.get(7).bit()).isZero();
        assertThat(cells.get(0).x()).isLessThan(cells.get(7).x());
        assertThat(cells.get(0).y()).isEqualTo(cells.get(7).y());
    }

    @Test
    void aGapSeparatesEveryFourBitsAndRowsStackDownwards() {
        List<MultiBitLayout.BitCell> cells = MultiBitLayout.cells(16);

        double insideGroup = cells.get(1).x() - cells.get(0).x();
        double acrossGroups = cells.get(4).x() - cells.get(3).x();
        assertThat(acrossGroups - insideGroup).isEqualTo(MultiBitLayout.GAP);
        assertThat(cells.get(8).bit()).isEqualTo(7);
        assertThat(cells.get(8).x()).isEqualTo(cells.get(0).x());
        assertThat(cells.get(8).y()).isGreaterThan(cells.get(0).y());
    }

    @Test
    void everyCellFitsInsideTheBoxAtEveryWidth() {
        for (int width = 1; width <= 32; width++) {
            GridSize box = MultiBitLayout.boxFor(width);
            for (MultiBitLayout.BitCell cell : MultiBitLayout.cells(width)) {
                assertThat(cell.x()).isGreaterThanOrEqualTo(0);
                assertThat(cell.y()).isGreaterThanOrEqualTo(0);
                assertThat(cell.x() + cell.width()).isLessThanOrEqualTo(box.width() * MultiBitLayout.UNIT);
                assertThat(cell.y() + cell.height()).isLessThanOrEqualTo(box.height() * MultiBitLayout.UNIT);
            }
        }
    }

    @Test
    void theCellAtAPointIsFoundAndAPointOutsideAnyCellFindsNone() {
        MultiBitLayout.BitCell cell = MultiBitLayout.cells(8).get(2);

        assertThat(MultiBitLayout.cellAt(8, cell.x() + 1, cell.y() + 1)).hasValue(cell);
        assertThat(MultiBitLayout.cellAt(8, -3, -3)).isEmpty();
    }

    @Test
    void thePinSitsInTheMiddleOfItsSideOneLeadAwayFromTheBody() {
        GridSize box = MultiBitLayout.boxFor(8);

        assertThat(MultiBitLayout.pinFor(8, "right")).isEqualTo(new GridPoint(box.width() + 2, 2));
        assertThat(MultiBitLayout.pinFor(8, "left")).isEqualTo(new GridPoint(0, 2));
        assertThat(MultiBitLayout.pinFor(8, "bottom")).isEqualTo(new GridPoint(5, box.height() + 2));
        assertThat(MultiBitLayout.pinFor(8, "top")).isEqualTo(new GridPoint(5, 0));
    }

    @Test
    void theShapeAddsTheLeadToTheSideOfThePinAndOffsetsTheBody() {
        ComponentShape right = MultiBitLayout.shape(8, "right", "out", PinDirection.OUTPUT);
        ComponentShape left = MultiBitLayout.shape(8, "left", "in", PinDirection.INPUT);

        assertThat(right.size()).isEqualTo(new GridSize(12, 3));
        assertThat(right.pins()).hasSize(1);
        assertThat(right.pins().get(0).width()).isEqualTo(8);
        assertThat(right.pins().get(0).direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(MultiBitLayout.bodyOrigin(8, "right")).isEqualTo(GridPoint.ORIGIN);
        assertThat(left.size()).isEqualTo(new GridSize(12, 3));
        assertThat(MultiBitLayout.bodyOrigin(8, "left")).isEqualTo(new GridPoint(2, 0));
        assertThat(MultiBitLayout.bodyOrigin(8, "top")).isEqualTo(new GridPoint(0, 2));
    }

    @Test
    void anUnknownSideOrAWidthOutOfRangeIsRefused() {
        assertThatThrownBy(() -> MultiBitLayout.pinFor(8, "diagonal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MultiBitLayout.boxFor(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MultiBitLayout.boxFor(33)).isInstanceOf(IllegalArgumentException.class);
    }
}
