package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SheetGridTest {

    private static final double TOLERANCE = 1e-9;
    private static final ScaleMode FULL_SIZE = new ScaleMode.Percentage(100);

    // As SheetPlanTest: at 100 % a tiled sheet is 60 × 82.5 grid units and the step 57.5 × 80 (a 2.5 unit overlap).
    private static final PageGeometry GEOMETRY = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, 56, 0, 10,
            20);

    /** 125 × 87.5 units: 3 columns and 2 rows. */
    private static final SheetGrid THREE_BY_TWO = SheetGrid.of(PageLayout.of(new Rectangle2D.Double(0, 0, 125, 87.5),
            GEOMETRY, FULL_SIZE));
    /** 12.5 × 10 units centred on one sheet whose window is 62.5 × 85 units. */
    private static final SheetGrid ONE_SHEET = SheetGrid.of(PageLayout.of(new Rectangle2D.Double(10, 20, 12.5, 10),
            GEOMETRY, FULL_SIZE));

    @Test
    void aTiledGridHasTheLayoutsColumnsAndRows() {
        assertThat(THREE_BY_TWO.columns()).isEqualTo(3);
        assertThat(THREE_BY_TWO.rows()).isEqualTo(2);
        assertThat(THREE_BY_TWO.tiled()).isTrue();
        assertThat(ONE_SHEET.tiled()).isFalse();
        assertThat(ONE_SHEET.columns()).isEqualTo(1);
    }

    @Test
    void theLastSheetIsTheWholeWindowNotCutToTheArea() {
        assertRectangle(THREE_BY_TWO.sheetRectangle(0, 0), 0, 0, 60, 82.5);
        assertRectangle(THREE_BY_TWO.sheetRectangle(2, 1), 115, 80, 60, 82.5);
    }

    @Test
    void oneSheetIsTheWindowCentredOnTheArea() {
        assertRectangle(ONE_SHEET.sheetRectangle(0, 0), 10 + 6.25 - 31.25, 20 + 5 - 42.5, 62.5, 85);
    }

    @Test
    void everyPrintedPageLiesInsideItsSheetFromItsTopLeftCorner() {
        PageLayout layout = PageLayout.of(new Rectangle2D.Double(-37, 12, 300, 200), GEOMETRY,
                new ScaleMode.Percentage(75));
        SheetGrid grid = SheetGrid.of(layout);
        for (int index = 0; index < layout.pageCount(); index++) {
            PrintPage page = layout.page(index);
            Rectangle2D sheet = grid.sheetRectangle(page.column(), page.row());
            Rectangle2D printed = page.gridRectangle();
            assertThat(sheet.getX()).isCloseTo(printed.getX(), within(TOLERANCE));
            assertThat(sheet.getY()).isCloseTo(printed.getY(), within(TOLERANCE));
            assertThat(sheet.getMaxX()).isGreaterThanOrEqualTo(printed.getMaxX() - TOLERANCE);
            assertThat(sheet.getMaxY()).isGreaterThanOrEqualTo(printed.getMaxY() - TOLERANCE);
            assertThat(grid.sheetNumber(page.column(), page.row())).isEqualTo(index + 1);
        }
    }

    @Test
    void sheetsAreNumberedInReadingOrderFromOne() {
        assertThat(THREE_BY_TWO.sheetNumber(0, 0)).isEqualTo(1);
        assertThat(THREE_BY_TWO.sheetNumber(2, 0)).isEqualTo(3);
        assertThat(THREE_BY_TWO.sheetNumber(0, 1)).isEqualTo(4);
        assertThat(THREE_BY_TWO.sheetNumber(2, 1)).isEqualTo(6);
    }

    @Test
    void aSheetOutsideTheGridIsAProgrammingError() {
        assertThatThrownBy(() -> THREE_BY_TWO.sheetRectangle(3, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> THREE_BY_TWO.sheetNumber(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void theVisibleRangesAreClampedToTheGrid() {
        assertThat(THREE_BY_TWO.columnsBetween(0, 10)).isEqualTo(new SheetGrid.Range(0, 1));
        assertThat(THREE_BY_TWO.columnsBetween(58, 59)).as("inside the overlap").isEqualTo(new SheetGrid.Range(0, 2));
        assertThat(THREE_BY_TWO.columnsBetween(-1000, 5000)).isEqualTo(new SheetGrid.Range(0, 3));
        assertThat(THREE_BY_TWO.columnsBetween(-1000, -500).isEmpty()).isTrue();
        assertThat(THREE_BY_TWO.columnsBetween(200, 300).isEmpty()).isTrue();
        assertThat(THREE_BY_TWO.rowsBetween(81, 81.5)).isEqualTo(new SheetGrid.Range(0, 2));
        assertThat(THREE_BY_TWO.rowsBetween(100, 1000)).isEqualTo(new SheetGrid.Range(1, 2));
        assertThat(ONE_SHEET.columnsBetween(-20, -10)).isEqualTo(new SheetGrid.Range(0, 1));
        assertThat(ONE_SHEET.columnsBetween(100, 200).isEmpty()).isTrue();
    }

    @Test
    void overlapBandsLieOnlyBetweenNeighbours() {
        assertThat(THREE_BY_TWO.overlapBands(0, 0)).containsExactly(
                new Rectangle2D.Double(57.5, 0, 2.5, 82.5), new Rectangle2D.Double(0, 80, 60, 2.5));
        assertThat(THREE_BY_TWO.overlapBands(2, 0)).as("no right neighbour")
                .containsExactly(new Rectangle2D.Double(115, 80, 60, 2.5));
        assertThat(THREE_BY_TWO.overlapBands(1, 1)).as("no lower neighbour")
                .containsExactly(new Rectangle2D.Double(115, 80, 2.5, 82.5));
        assertThat(THREE_BY_TWO.overlapBands(2, 1)).isEmpty();
        assertThat(ONE_SHEET.overlapBands(0, 0)).isEmpty();
    }

    @Test
    void aPartAcrossABorderIsCut() {
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(50, 10, 15, 5))).isTrue();
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(10, 75, 5, 10))).isTrue();
    }

    @Test
    void aPartInsideOneSheetOrInsideAnOverlapIsNotCut() {
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(10, 10, 20, 20))).isFalse();
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(58, 10, 1.5, 5))).isFalse();
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(58, 80.5, 1.5, 1.5))).as("where four sheets meet")
                .isFalse();
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(57.5, 0, 60, 82.5))).as("exactly one whole sheet")
                .isFalse();
    }

    @Test
    void aFrameLargerThanASheetIsNotMarked() {
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(10, 10, 70, 20))).isFalse();
        assertThat(THREE_BY_TWO.isCut(new Rectangle2D.Double(10, 10, 20, 83))).isFalse();
    }

    @Test
    void nothingIsCutOnOneSheet() {
        assertThat(ONE_SHEET.isCut(new Rectangle2D.Double(10, 20, 12.5, 10))).isFalse();
        assertThat(ONE_SHEET.isCut(new Rectangle2D.Double(-100, 20, 12.5, 10))).isFalse();
    }

    @Test
    void anAnchoredGridIsTheWidenedLayoutsGrid() {
        PageLayout layout = SheetPlan.layout(new Rectangle2D.Double(0, 0, 125, 87.5), GEOMETRY, FULL_SIZE,
                Optional.of(new GridPoint(30, 10)));
        SheetGrid grid = SheetGrid.of(layout);

        // from (-27.5, -70): 3 columns reach 147.5 ≥ 125, 2 rows reach 92.5 ≥ 87.5
        assertThat(grid.columns()).isEqualTo(3);
        assertThat(grid.rows()).isEqualTo(2);
        assertRectangle(grid.sheetRectangle(1, 1), 30, 10, 60, 82.5);
    }

    @Test
    void aRefusedLayoutHasNoSheets() {
        PageGeometry noRoom = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, 400, 0, 10, 20);
        SheetGrid grid = SheetGrid.of(PageLayout.of(new Rectangle2D.Double(0, 0, 125, 87.5), noRoom, FULL_SIZE));

        assertThat(grid.columns()).isZero();
        assertThat(grid.rows()).isZero();
        assertThat(grid.columnsBetween(-1000, 1000).isEmpty()).isTrue();
        assertThat(grid.rowsBetween(-1000, 1000).isEmpty()).isTrue();
        assertThat(grid.isCut(new Rectangle2D.Double(0, 0, 1, 1))).isFalse();
    }

    @Test
    void aRangeKnowsItsSizeAndContents() {
        SheetGrid.Range range = new SheetGrid.Range(1, 3);

        assertThat(range.isEmpty()).isFalse();
        assertThat(range.size()).isEqualTo(2);
        assertThat(range.contains(1)).isTrue();
        assertThat(range.contains(3)).isFalse();
        assertThat(new SheetGrid.Range(2, 2).isEmpty()).isTrue();
        assertThatThrownBy(() -> new SheetGrid.Range(3, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRectangle(Rectangle2D actual, double x, double y, double width, double height) {
        assertThat(actual.getX()).as("x").isCloseTo(x, within(TOLERANCE));
        assertThat(actual.getY()).as("y").isCloseTo(y, within(TOLERANCE));
        assertThat(actual.getWidth()).as("width").isCloseTo(width, within(TOLERANCE));
        assertThat(actual.getHeight()).as("height").isCloseTo(height, within(TOLERANCE));
    }
}
