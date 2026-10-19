package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.KarnaughGroup.CellRectangle;
import cl.almejo.vsim.core.logic.KarnaughGroup.Side;
import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KarnaughLayoutTest {

    @Test
    void twoInputsAreATwoByTwoMapWithARowsAndBColumns() {
        KarnaughLayout layout = KarnaughLayout.of(2);

        assertThat(layout.rows()).isEqualTo(2);
        assertThat(layout.columns()).isEqualTo(2);
        assertThat(layout.rowVariables()).containsExactly(0);
        assertThat(layout.columnVariables()).containsExactly(1);
        assertThat(layout.rowCodes()).containsExactly("0", "1");
        assertThat(layout.columnCodes()).containsExactly("0", "1");
        assertThat(layout.cell(0)).isEqualTo(new GridCell(0, 0));
        assertThat(layout.cell(1)).isEqualTo(new GridCell(0, 1));
        assertThat(layout.cell(2)).isEqualTo(new GridCell(1, 0));
        assertThat(layout.cell(3)).isEqualTo(new GridCell(1, 1));
    }

    @Test
    void threeInputsPutAOnTheRowsAndBcOnTheColumnsInGrayOrder() {
        KarnaughLayout layout = KarnaughLayout.of(3);

        assertThat(layout.rows()).isEqualTo(2);
        assertThat(layout.columns()).isEqualTo(4);
        assertThat(layout.rowVariables()).containsExactly(0);
        assertThat(layout.columnVariables()).containsExactly(1, 2);
        assertThat(layout.columnCodes()).containsExactly("00", "01", "11", "10");
        // Row 0: 0 1 3 2 · row 1: 4 5 7 6
        assertThat(rowsOfTheMap(layout)).containsExactly(List.of(0, 1, 3, 2), List.of(4, 5, 7, 6));
    }

    @Test
    void fourInputsPutAbOnTheRowsAndCdOnTheColumnsInGrayOrder() {
        KarnaughLayout layout = KarnaughLayout.of(4);

        assertThat(layout.rows()).isEqualTo(4);
        assertThat(layout.columns()).isEqualTo(4);
        assertThat(layout.rowVariables()).containsExactly(0, 1);
        assertThat(layout.columnVariables()).containsExactly(2, 3);
        assertThat(layout.rowCodes()).containsExactly("00", "01", "11", "10");
        assertThat(layout.columnCodes()).containsExactly("00", "01", "11", "10");
        assertThat(rowsOfTheMap(layout)).containsExactly(
                List.of(0, 1, 3, 2),
                List.of(4, 5, 7, 6),
                List.of(12, 13, 15, 14),
                List.of(8, 9, 11, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void cellAndTableRowAreInverses(int inputCount) {
        KarnaughLayout layout = KarnaughLayout.of(inputCount);
        Set<GridCell> seen = new HashSet<>();
        for (int tableRow = 0; tableRow < 1 << inputCount; tableRow++) {
            GridCell cell = layout.cell(tableRow);
            assertThat(seen.add(cell)).isTrue();
            assertThat(layout.tableRow(cell.row(), cell.column())).isEqualTo(tableRow);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void neighbouringCellsIncludingTheWrapAroundDifferInOneVariable(int inputCount) {
        KarnaughLayout layout = KarnaughLayout.of(inputCount);
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                int here = layout.tableRow(row, column);
                int right = layout.tableRow(row, (column + 1) % layout.columns());
                int below = layout.tableRow((row + 1) % layout.rows(), column);
                if (layout.columns() > 1) {
                    assertThat(Integer.bitCount(here ^ right)).isEqualTo(1);
                }
                assertThat(Integer.bitCount(here ^ below)).isEqualTo(1);
            }
        }
    }

    @Test
    void onlyTwoToFourInputsHaveAMap() {
        assertThatThrownBy(() -> KarnaughLayout.of(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KarnaughLayout.of(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void everyTermGivesRectanglesWhoseCellsAreExactlyTheTermsRows(int inputCount) {
        KarnaughLayout layout = KarnaughLayout.of(inputCount);
        int all = (1 << inputCount) - 1;
        for (int mask = 0; mask <= all; mask++) {
            for (int value = 0; value <= all; value++) {
                if ((value & ~mask) != 0) {
                    continue;
                }
                Term term = new Term(mask, value);
                KarnaughGroup group = layout.group(term);

                Set<GridCell> expected = new HashSet<>();
                for (int tableRow = 0; tableRow <= all; tableRow++) {
                    if (term.covers(tableRow)) {
                        expected.add(layout.cell(tableRow));
                    }
                }
                assertThat(group.term()).isEqualTo(term);
                assertThat(group.cells()).as("%s", term).isEqualTo(expected);

                Set<GridCell> drawn = new HashSet<>();
                for (CellRectangle rectangle : group.rectangles()) {
                    assertThat(rectangle.row()).isBetween(0, layout.rows() - 1);
                    assertThat(rectangle.column()).isBetween(0, layout.columns() - 1);
                    assertThat(rectangle.row() + rectangle.rowSpan()).isLessThanOrEqualTo(layout.rows());
                    assertThat(rectangle.column() + rectangle.columnSpan()).isLessThanOrEqualTo(layout.columns());
                    for (int row = rectangle.row(); row < rectangle.row() + rectangle.rowSpan(); row++) {
                        for (int column = rectangle.column(); column < rectangle.column() + rectangle.columnSpan();
                             column++) {
                            assertThat(drawn.add(new GridCell(row, column))).as("pieces overlap for %s", term)
                                    .isTrue();
                        }
                    }
                }
                assertThat(drawn).as("%s", term).isEqualTo(expected);
                assertThat(group.rectangles()).hasSizeBetween(1, 4);
            }
        }
    }

    @Test
    void aContiguousGroupIsOneClosedRectangle() {
        // 4 inputs, BD (mask 0101, value 0101): rows 01 and 11, columns 01 and 11 → the 2×2 block in the middle.
        KarnaughGroup group = KarnaughLayout.of(4).group(new Term(0b0101, 0b0101));

        assertThat(group.rectangles()).containsExactly(new CellRectangle(1, 1, 2, 2, Set.of()));
    }

    @Test
    void aGroupWrappingLeftAndRightIsTwoHalvesOpenAtTheEdges() {
        // 4 inputs, D' over rows 00 and 01: A'D' → columns 00 and 10, rows 0 and 1.
        KarnaughGroup group = KarnaughLayout.of(4).group(new Term(0b1001, 0b0000));

        assertThat(group.rectangles()).containsExactly(
                new CellRectangle(0, 0, 2, 1, Set.of(Side.LEFT)),
                new CellRectangle(0, 3, 2, 1, Set.of(Side.RIGHT)));
    }

    @Test
    void aGroupWrappingTopAndBottomIsTwoHalvesOpenAtTheEdges() {
        // 4 inputs, B'C: rows 00 and 10 (first and last), columns 11 and 10.
        KarnaughGroup group = KarnaughLayout.of(4).group(new Term(0b0110, 0b0010));

        assertThat(group.rectangles()).containsExactly(
                new CellRectangle(0, 2, 1, 2, Set.of(Side.TOP)),
                new CellRectangle(3, 2, 1, 2, Set.of(Side.BOTTOM)));
    }

    @Test
    void theFourCornersAreFourQuarters() {
        // 4 inputs, B'D': rows 00 and 10, columns 00 and 10.
        KarnaughGroup group = KarnaughLayout.of(4).group(new Term(0b0101, 0b0000));

        assertThat(group.rectangles()).containsExactly(
                new CellRectangle(0, 0, 1, 1, Set.of(Side.TOP, Side.LEFT)),
                new CellRectangle(0, 3, 1, 1, Set.of(Side.TOP, Side.RIGHT)),
                new CellRectangle(3, 0, 1, 1, Set.of(Side.BOTTOM, Side.LEFT)),
                new CellRectangle(3, 3, 1, 1, Set.of(Side.BOTTOM, Side.RIGHT)));
    }

    @Test
    void aWholeRowWrapsNowhere() {
        // 3 inputs, A: row 1, every column.
        KarnaughGroup group = KarnaughLayout.of(3).group(new Term(0b100, 0b100));

        assertThat(group.rectangles()).containsExactly(new CellRectangle(1, 0, 1, 4, Set.of()));
    }

    @Test
    void theWholeMapIsOneRectangle() {
        KarnaughGroup group = KarnaughLayout.of(4).group(new Term(0, 0));

        assertThat(group.cells()).hasSize(16);
        assertThat(group.rectangles()).containsExactly(new CellRectangle(0, 0, 4, 4, Set.of()));
    }

    @Test
    void aTermUsingInputsBeyondTheMapIsRefused() {
        assertThatThrownBy(() -> KarnaughLayout.of(3).group(new Term(0b1000, 0b1000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<List<Integer>> rowsOfTheMap(KarnaughLayout layout) {
        List<List<Integer>> rows = new java.util.ArrayList<>();
        for (int row = 0; row < layout.rows(); row++) {
            List<Integer> tableRows = new java.util.ArrayList<>();
            for (int column = 0; column < layout.columns(); column++) {
                tableRows.add(layout.tableRow(row, column));
            }
            rows.add(tableRows);
        }
        return rows;
    }
}
