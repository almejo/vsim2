package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.KarnaughGroup.CellRectangle;
import cl.almejo.vsim.core.logic.KarnaughGroup.Side;
import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void everyDrawnRectangleCoversItsPositionsModuloTheAxes(int inputCount) {
        KarnaughLayout layout = KarnaughLayout.of(inputCount);
        for (int firstRow = 0; firstRow < layout.rows(); firstRow++) {
            for (int firstColumn = 0; firstColumn < layout.columns(); firstColumn++) {
                for (int rowSpan = 1; rowSpan <= layout.rows(); rowSpan++) {
                    for (int columnSpan = 1; columnSpan <= layout.columns(); columnSpan++) {
                        DrawnGroup drawn = layout.drawn(firstRow, firstColumn, rowSpan, columnSpan);
                        String description = firstRow + "," + firstColumn + " " + rowSpan + "x" + columnSpan;

                        Set<GridCell> expected = new HashSet<>();
                        for (int rowOffset = 0; rowOffset < rowSpan; rowOffset++) {
                            for (int columnOffset = 0; columnOffset < columnSpan; columnOffset++) {
                                expected.add(new GridCell((firstRow + rowOffset) % layout.rows(),
                                        (firstColumn + columnOffset) % layout.columns()));
                            }
                        }
                        assertThat(drawn.cells()).as(description).isEqualTo(expected);
                        assertThat(tableRows(layout, drawn.cells())).as(description).isSorted();
                        assertThat(cellsOfThePieces(layout, drawn.rectangles())).as(description).isEqualTo(expected);
                        boolean rowsWrap = wraps(firstRow, rowSpan, layout.rows());
                        boolean columnsWrap = wraps(firstColumn, columnSpan, layout.columns());
                        assertThat(drawn.rectangles()).as(description)
                                .hasSize((rowsWrap ? 2 : 1) * (columnsWrap ? 2 : 1));
                        for (CellRectangle rectangle : drawn.rectangles()) {
                            assertThat(rectangle.isOpen(Side.TOP)).as(description)
                                    .isEqualTo(rowsWrap && rectangle.row() == 0);
                            assertThat(rectangle.isOpen(Side.BOTTOM)).as(description)
                                    .isEqualTo(rowsWrap && rectangle.row() + rectangle.rowSpan() == layout.rows());
                            assertThat(rectangle.isOpen(Side.LEFT)).as(description)
                                    .isEqualTo(columnsWrap && rectangle.column() == 0);
                            assertThat(rectangle.isOpen(Side.RIGHT)).as(description).isEqualTo(
                                    columnsWrap && rectangle.column() + rectangle.columnSpan() == layout.columns());
                        }
                        layout.term(drawn.cells()).ifPresent(term -> assertThat(drawn.rectangles())
                                .as(description).isEqualTo(layout.group(term).rectangles()));
                    }
                }
            }
        }
    }

    @Test
    void aRectanglePassingTheRightEdgeIsTwoHalvesOpenAtTheEdges() {
        DrawnGroup drawn = KarnaughLayout.of(4).drawn(1, 3, 2, 2);

        assertThat(drawn.rectangles()).containsExactly(
                new CellRectangle(1, 0, 2, 1, Set.of(Side.LEFT)),
                new CellRectangle(1, 3, 2, 1, Set.of(Side.RIGHT)));
    }

    @Test
    void aRectanglePassingTheBottomOfTheTwoByFourMapIsTheWholeAxisAndWrapsNowhere() {
        DrawnGroup drawn = KarnaughLayout.of(3).drawn(1, 1, 2, 2);

        assertThat(drawn.rectangles()).containsExactly(new CellRectangle(0, 1, 2, 2, Set.of()));
    }

    @Test
    void aRectanglePassingTheRightEdgeOfTheTwoByTwoMapIsTheWholeAxis() {
        DrawnGroup drawn = KarnaughLayout.of(2).drawn(0, 1, 1, 2);

        assertThat(drawn.rectangles()).containsExactly(new CellRectangle(0, 0, 1, 2, Set.of()));
    }

    @Test
    void aRectangleOverBothEdgesIsFourQuarters() {
        DrawnGroup drawn = KarnaughLayout.of(4).drawn(3, 3, 2, 2);

        assertThat(drawn.rectangles()).containsExactly(
                new CellRectangle(0, 0, 1, 1, Set.of(Side.TOP, Side.LEFT)),
                new CellRectangle(0, 3, 1, 1, Set.of(Side.TOP, Side.RIGHT)),
                new CellRectangle(3, 0, 1, 1, Set.of(Side.BOTTOM, Side.LEFT)),
                new CellRectangle(3, 3, 1, 1, Set.of(Side.BOTTOM, Side.RIGHT)));
    }

    @Test
    void aSpanOfTheWholeAxisStartingInTheMiddleIsOnePiece() {
        DrawnGroup drawn = KarnaughLayout.of(4).drawn(2, 1, 1, 4);

        assertThat(drawn.rectangles()).containsExactly(new CellRectangle(2, 0, 1, 4, Set.of()));
        assertThat(drawn.cells()).hasSize(4);
    }

    @Test
    void aThreeWideRectangleIsStillDrawnButIsNoTerm() {
        KarnaughLayout layout = KarnaughLayout.of(4);
        DrawnGroup drawn = layout.drawn(0, 0, 1, 3);

        assertThat(drawn.cells()).hasSize(3);
        assertThat(drawn.rectangles()).containsExactly(new CellRectangle(0, 0, 1, 3, Set.of()));
        assertThat(layout.term(drawn.cells())).isEmpty();
        assertThat(layout.term(layout.drawn(1, 3, 3, 1).cells())).isEmpty();
    }

    @Test
    void aTwoWideRectangleInTheMiddleOfTheTwoByFourMapIsATerm() {
        KarnaughLayout layout = KarnaughLayout.of(3);

        // Columns 01 and 11: C.
        assertThat(layout.term(layout.drawn(0, 1, 2, 2).cells())).contains(new Term(0b001, 0b001));
    }

    @Test
    void drawnRectanglesOutsideTheMapAreRefused() {
        KarnaughLayout layout = KarnaughLayout.of(3);

        assertThatThrownBy(() -> layout.drawn(2, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(-1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, 4, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, -1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, 0, 3, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, 0, 1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.drawn(0, 0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCornersOfTheFourByFourMapAreFourQuartersLikeTheirTerm() {
        KarnaughLayout layout = KarnaughLayout.of(4);
        DrawnGroup corners = layout.corners().orElseThrow();

        assertThat(corners.cells()).containsExactlyInAnyOrder(new GridCell(0, 0), new GridCell(0, 3),
                new GridCell(3, 0), new GridCell(3, 3));
        // B'D'
        assertThat(layout.term(corners.cells())).contains(new Term(0b0101, 0b0000));
        assertThat(corners.rectangles()).isEqualTo(layout.group(new Term(0b0101, 0b0000)).rectangles());
        assertThat(corners.rectangles()).hasSize(4);
    }

    @Test
    void theCornersOfTheTwoByFourMapAreTheTwoEdgeColumnsDrawnLikeTheirTerm() {
        KarnaughLayout layout = KarnaughLayout.of(3);
        DrawnGroup corners = layout.corners().orElseThrow();

        assertThat(corners.cells()).containsExactlyInAnyOrder(new GridCell(0, 0), new GridCell(0, 3),
                new GridCell(1, 0), new GridCell(1, 3));
        // C'
        assertThat(layout.term(corners.cells())).contains(new Term(0b001, 0b000));
        assertThat(corners.rectangles()).isEqualTo(layout.group(new Term(0b001, 0b000)).rectangles());
    }

    @Test
    void theTwoByTwoMapHasNoCornersGesture() {
        assertThat(KarnaughLayout.of(2).corners()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void theTermOfADrawnRectangleIsTheSubcubeWithExactlyItsCells(int inputCount) {
        KarnaughLayout layout = KarnaughLayout.of(inputCount);
        for (int firstRow = 0; firstRow < layout.rows(); firstRow++) {
            for (int firstColumn = 0; firstColumn < layout.columns(); firstColumn++) {
                for (int rowSpan = 1; rowSpan <= layout.rows(); rowSpan++) {
                    for (int columnSpan = 1; columnSpan <= layout.columns(); columnSpan++) {
                        Set<GridCell> cells = layout.drawn(firstRow, firstColumn, rowSpan, columnSpan).cells();
                        assertThat(layout.term(cells)).as("%d,%d %dx%d", firstRow, firstColumn, rowSpan, columnSpan)
                                .isEqualTo(bruteForceSubcube(layout, cells));
                    }
                }
            }
        }
    }

    @Test
    void cellsThatAreNoSubcubeHaveNoTerm() {
        KarnaughLayout layout = KarnaughLayout.of(4);

        assertThat(layout.term(Set.of())).isEmpty();
        assertThat(layout.term(Set.of(new GridCell(0, 0), new GridCell(1, 1)))).isEmpty();
        assertThat(layout.term(Set.of(new GridCell(0, 0), new GridCell(0, 2)))).isEmpty();
        assertThat(layout.term(Set.of(new GridCell(0, 0), new GridCell(0, 1), new GridCell(1, 0),
                new GridCell(2, 2)))).isEmpty();
        assertThat(layout.term(Set.of(new GridCell(0, 1)))).contains(new Term(0b1111, 0b0001));
    }

    @Test
    void cellsOutsideTheMapAreRefusedByTerm() {
        assertThatThrownBy(() -> KarnaughLayout.of(3).term(Set.of(new GridCell(2, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Optional<Term> bruteForceSubcube(KarnaughLayout layout, Set<GridCell> cells) {
        int all = (1 << layout.inputCount()) - 1;
        for (int mask = 0; mask <= all; mask++) {
            for (int value = 0; value <= all; value++) {
                if ((value & ~mask) != 0) {
                    continue;
                }
                Set<GridCell> covered = new HashSet<>();
                for (int tableRow = 0; tableRow <= all; tableRow++) {
                    if ((tableRow & mask) == value) {
                        covered.add(layout.cell(tableRow));
                    }
                }
                if (covered.equals(cells)) {
                    return Optional.of(new Term(mask, value));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean wraps(int first, int span, int size) {
        return span < size && first + span > size;
    }

    private static List<Integer> tableRows(KarnaughLayout layout, Set<GridCell> cells) {
        List<Integer> rows = new java.util.ArrayList<>();
        for (GridCell cell : cells) {
            rows.add(layout.tableRow(cell.row(), cell.column()));
        }
        return rows;
    }

    private static Set<GridCell> cellsOfThePieces(KarnaughLayout layout, List<CellRectangle> rectangles) {
        Set<GridCell> cells = new HashSet<>();
        for (CellRectangle rectangle : rectangles) {
            assertThat(rectangle.row() + rectangle.rowSpan()).isLessThanOrEqualTo(layout.rows());
            assertThat(rectangle.column() + rectangle.columnSpan()).isLessThanOrEqualTo(layout.columns());
            for (int row = rectangle.row(); row < rectangle.row() + rectangle.rowSpan(); row++) {
                for (int column = rectangle.column(); column < rectangle.column() + rectangle.columnSpan(); column++) {
                    assertThat(cells.add(new GridCell(row, column))).as("pieces overlap").isTrue();
                }
            }
        }
        return cells;
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
