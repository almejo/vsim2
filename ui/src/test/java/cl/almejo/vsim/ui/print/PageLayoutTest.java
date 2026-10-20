package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PageLayoutTest {

    private static final double TOLERANCE = 1e-9;
    private static final double PIXELS = GridPoint.PIXELS_PER_UNIT;
    private static final ScaleMode FULL_SIZE = new ScaleMode.Percentage(100);
    private static final ScaleMode FIT = new ScaleMode.FitToOnePage();

    // Carta portrait (612 × 792) with round numbers: 56 pt margins, 10 pt marks strip, 20 pt overlap.
    private static final double MARGIN = 56;
    private static final double STRIP = 10;
    private static final double OVERLAP = 20;
    /** The window of a single page: 500 × 680. */
    private static final double SINGLE_WIDTH = 612 - 2 * MARGIN;
    private static final double SINGLE_HEIGHT = 792 - 2 * MARGIN;
    /** The window of a tiled page: 480 × 660, so the step is 460 × 640. */
    private static final double TILED_WIDTH = SINGLE_WIDTH - 2 * STRIP;
    private static final double TILED_HEIGHT = SINGLE_HEIGHT - 2 * STRIP;
    private static final double STEP_X = TILED_WIDTH - OVERLAP;
    private static final double STEP_Y = TILED_HEIGHT - OVERLAP;

    private static PageGeometry roundGeometry(double headerBandPoints) {
        return new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, MARGIN, headerBandPoints, STRIP, OVERLAP);
    }

    /** A grid area whose size is given in printed points at 100 %. */
    private static Rectangle2D gridArea(double x, double y, double widthPoints, double heightPoints) {
        return new Rectangle2D.Double(x, y, widthPoints / PIXELS, heightPoints / PIXELS);
    }

    @Test
    void theStandardGeometryHasOneCentimetreMarginsAndOverlap() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);

        assertThat(geometry.marginPoints()).isCloseTo(72 / 2.54, within(1e-9));
        assertThat(geometry.overlapPoints()).isCloseTo(72 / 2.54, within(1e-9));
        assertThat(geometry.headerBandPoints()).isZero();
        assertThat(geometry.marksStripPoints()).isPositive();
        assertThat(PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, true).headerBandPoints()).isPositive();
    }

    @Test
    void theStandardGeometryLeavesRoomOnEveryPaper() {
        for (Paper paper : Paper.values()) {
            for (PageOrientation orientation : PageOrientation.values()) {
                PageGeometry geometry = PageGeometry.standard(paper, orientation, true);
                PageLayout layout = PageLayout.of(gridArea(0, 0, 5000, 5000), geometry, FULL_SIZE);

                assertThat(layout.problem()).isEmpty();
                // the overlap is not clamped: the second column starts one window minus 1 cm later
                double tiledWidth = geometry.paperWidthPoints() - 2 * geometry.marginPoints()
                        - 2 * geometry.marksStripPoints();
                assertThat(layout.page(1).gridRectangle().getX() * PIXELS)
                        .isCloseTo(tiledWidth - geometry.overlapPoints(), within(1e-6));
            }
        }
    }

    @Test
    void landscapeSwapsThePaper() {
        PageGeometry portrait = PageGeometry.standard(Paper.A3, PageOrientation.PORTRAIT, false);
        PageGeometry landscape = PageGeometry.standard(Paper.A3, PageOrientation.LANDSCAPE, false);

        assertThat(portrait.paperWidthPoints()).isEqualTo(Paper.A3.widthPoints());
        assertThat(portrait.paperHeightPoints()).isEqualTo(Paper.A3.heightPoints());
        assertThat(landscape.paperWidthPoints()).isEqualTo(Paper.A3.heightPoints());
        assertThat(landscape.paperHeightPoints()).isEqualTo(Paper.A3.widthPoints());
    }

    @Test
    void landscapeChangesTheCountOfAWideCircuit() {
        Rectangle2D wide = gridArea(0, 0, 700, 100);

        PageLayout portrait = PageLayout.of(wide, PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false),
                FULL_SIZE);
        PageLayout landscape = PageLayout.of(wide, PageGeometry.standard(Paper.A4, PageOrientation.LANDSCAPE, false),
                FULL_SIZE);

        assertThat(portrait.columns()).isEqualTo(2);
        assertThat(landscape.pageCount()).isEqualTo(1);
    }

    @Test
    void fitToOnePageNeverEnlargesAndIsAlwaysOnePage() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);

        PageLayout small = PageLayout.of(gridArea(0, 0, 80, 40), geometry, FIT);
        PageLayout huge = PageLayout.of(gridArea(-5000, 300, 40_000, 9_000), geometry, FIT);

        assertThat(small.scale()).isEqualTo(1.0);
        assertThat(small.pageCount()).isEqualTo(1);
        assertThat(small.tiled()).isFalse();
        assertThat(huge.scale()).isLessThan(1.0);
        assertThat(huge.pageCount()).isEqualTo(1);
        assertThat(huge.tiled()).isFalse();
        Rectangle2D onPaper = huge.page(0).paperRectangle();
        assertThat(onPaper.getWidth()).isLessThanOrEqualTo(geometry.paperWidthPoints() - 2 * geometry.marginPoints()
                + TOLERANCE);
        assertThat(onPaper.getHeight()).isLessThanOrEqualTo(geometry.paperHeightPoints()
                - 2 * geometry.marginPoints() + TOLERANCE);
        assertThat(huge.page(0).gridRectangle()).isEqualTo(gridArea(-5000, 300, 40_000, 9_000));
    }

    @Test
    void fitToOnePageFillsTheLimitingDimension() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 2 * SINGLE_WIDTH, SINGLE_HEIGHT), roundGeometry(0), FIT);

        assertThat(layout.scale()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(layout.page(0).paperRectangle().getWidth()).isCloseTo(SINGLE_WIDTH, within(TOLERANCE));
        assertThat(layout.page(0).paperRectangle().getHeight()).isCloseTo(SINGLE_HEIGHT / 2, within(TOLERANCE));
    }

    @Test
    void aSinglePageIsCentredInTheWindow() {
        PageLayout layout = PageLayout.of(gridArea(3, 4, 100, 200), roundGeometry(0), FULL_SIZE);

        PrintPage page = layout.page(0);
        assertThat(layout.tiled()).isFalse();
        assertThat(page.index()).isZero();
        assertThat(page.paperRectangle().getX()).isCloseTo(MARGIN + (SINGLE_WIDTH - 100) / 2, within(TOLERANCE));
        assertThat(page.paperRectangle().getY()).isCloseTo(MARGIN + (SINGLE_HEIGHT - 200) / 2, within(TOLERANCE));
        assertThat(page.gridRectangle()).isEqualTo(gridArea(3, 4, 100, 200));
    }

    @Test
    void aCircuitTwoPointThreeWindowsWideTakesThreeColumns() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, TILED_WIDTH + STEP_X * 1.3, 100), roundGeometry(0),
                FULL_SIZE);

        assertThat(layout.tiled()).isTrue();
        assertThat(layout.columns()).isEqualTo(3);
        assertThat(layout.rows()).isEqualTo(1);
        assertThat(layout.pageCount()).isEqualTo(3);
    }

    @Test
    void anExactFitAddsNoPage() {
        PageLayout twoWindows = PageLayout.of(gridArea(0, 0, TILED_WIDTH + STEP_X, 100), roundGeometry(0),
                FULL_SIZE);
        PageLayout justOver = PageLayout.of(gridArea(0, 0, TILED_WIDTH + STEP_X + 0.01, 100), roundGeometry(0),
                FULL_SIZE);
        PageLayout oneWindow = PageLayout.of(gridArea(0, 0, SINGLE_WIDTH, SINGLE_HEIGHT), roundGeometry(0),
                FULL_SIZE);
        PageLayout justOverOneWindow = PageLayout.of(gridArea(0, 0, SINGLE_WIDTH + 0.01, 100), roundGeometry(0),
                FULL_SIZE);

        assertThat(twoWindows.columns()).isEqualTo(2);
        assertThat(justOver.columns()).isEqualTo(3);
        assertThat(oneWindow.pageCount()).isEqualTo(1);
        assertThat(oneWindow.tiled()).isFalse();
        assertThat(justOverOneWindow.columns()).isEqualTo(2);
        assertThat(justOverOneWindow.tiled()).isTrue();
    }

    @Test
    void theStepIsConstantAndOnlyNeighboursOverlap() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 2000, 100), roundGeometry(0), FULL_SIZE);

        assertThat(layout.columns()).isEqualTo(5);
        for (int column = 0; column < layout.columns(); column++) {
            assertThat(layout.page(column).gridRectangle().getX()).isCloseTo(column * STEP_X / PIXELS,
                    within(TOLERANCE));
        }
        for (int column = 0; column + 1 < layout.columns(); column++) {
            Rectangle2D left = layout.page(column).gridRectangle();
            Rectangle2D right = layout.page(column + 1).gridRectangle();
            assertThat(left.getMaxX() - right.getX()).isCloseTo(OVERLAP / PIXELS, within(TOLERANCE));
        }
        for (int column = 0; column + 2 < layout.columns(); column++) {
            Rectangle2D first = layout.page(column).gridRectangle();
            Rectangle2D third = layout.page(column + 2).gridRectangle();
            assertThat(first.getMaxX()).isLessThanOrEqualTo(third.getX());
        }
        Rectangle2D last = layout.page(4).gridRectangle();
        assertThat(last.getMaxX()).isCloseTo(2000 / PIXELS, within(TOLERANCE));
        assertThat(layout.page(4).paperRectangle().getWidth()).isCloseTo(2000 - 4 * STEP_X, within(TOLERANCE));
    }

    @Test
    void tiledPagesStartAtTheTopLeftOfTheWindow() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 1000, 1000), roundGeometry(0), FULL_SIZE);

        for (int index = 0; index < layout.pageCount(); index++) {
            PrintPage page = layout.page(index);
            assertThat(page.paperRectangle().getX()).isCloseTo(MARGIN + STRIP, within(TOLERANCE));
            assertThat(page.paperRectangle().getY()).isCloseTo(MARGIN + STRIP, within(TOLERANCE));
            assertThat(page.paperRectangle().getWidth()).isLessThanOrEqualTo(TILED_WIDTH + TOLERANCE);
            assertThat(page.paperRectangle().getHeight()).isLessThanOrEqualTo(TILED_HEIGHT + TOLERANCE);
        }
    }

    @Test
    void pagesComeInReadingOrder() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 1000, 1000), roundGeometry(0), FULL_SIZE);
        List<String> order = new ArrayList<>();
        for (int index = 0; index < layout.pageCount(); index++) {
            PrintPage page = layout.page(index);
            assertThat(page.index()).isEqualTo(index);
            order.add(page.row() + "," + page.column());
        }

        assertThat(layout.columns()).isEqualTo(3);
        assertThat(layout.rows()).isEqualTo(2);
        assertThat(order).containsExactly("0,0", "0,1", "0,2", "1,0", "1,1", "1,2");
        assertThat(layout.page(4).gridRectangle().getX()).isCloseTo(STEP_X / PIXELS, within(TOLERANCE));
        assertThat(layout.page(4).gridRectangle().getY()).isCloseTo(STEP_Y / PIXELS, within(TOLERANCE));
        assertThat(layout.page(5).paperRectangle().getHeight()).isCloseTo(1000 - STEP_Y, within(TOLERANCE));
    }

    @Test
    void theHeaderBandIsSubtractedBeforeCounting() {
        // a band of 20 at the top and 20 at the bottom leaves 640 of the 680 points of a single page
        PageLayout withoutBand = PageLayout.of(gridArea(0, 0, 100, 670), roundGeometry(0), FULL_SIZE);
        PageLayout withBand = PageLayout.of(gridArea(0, 0, 100, 670), roundGeometry(20), FULL_SIZE);

        assertThat(withoutBand.pageCount()).isEqualTo(1);
        assertThat(withBand.rows()).isEqualTo(2);
        assertThat(withBand.page(0).paperRectangle().getY()).isCloseTo(MARGIN + 20 + STRIP, within(TOLERANCE));
    }

    @Test
    void theHeaderBandAlsoShrinksTheFit() {
        PageLayout withoutBand = PageLayout.of(gridArea(0, 0, 100, 2 * SINGLE_HEIGHT), roundGeometry(0), FIT);
        PageLayout withBand = PageLayout.of(gridArea(0, 0, 100, 2 * SINGLE_HEIGHT), roundGeometry(20), FIT);

        assertThat(withoutBand.scale()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(withBand.scale()).isCloseTo((SINGLE_HEIGHT - 40) / (2 * SINGLE_HEIGHT), within(TOLERANCE));
        assertThat(withBand.page(0).paperRectangle().getY()).isCloseTo(MARGIN + 20, within(TOLERANCE));
    }

    @Test
    void aNegativeOriginMapsToTheSameSheets() {
        PageLayout atOrigin = PageLayout.of(gridArea(0, 0, 1000, 1000), roundGeometry(0), FULL_SIZE);
        PageLayout farAway = PageLayout.of(gridArea(-12_345, -678, 1000, 1000), roundGeometry(0), FULL_SIZE);

        assertThat(farAway.pageCount()).isEqualTo(atOrigin.pageCount());
        for (int index = 0; index < atOrigin.pageCount(); index++) {
            Rectangle2D expected = atOrigin.page(index).gridRectangle();
            Rectangle2D actual = farAway.page(index).gridRectangle();
            assertThat(actual.getX()).isCloseTo(expected.getX() - 12_345, within(1e-6));
            assertThat(actual.getY()).isCloseTo(expected.getY() - 678, within(1e-6));
            assertThat(actual.getWidth()).isCloseTo(expected.getWidth(), within(1e-6));
            assertThat(actual.getHeight()).isCloseTo(expected.getHeight(), within(1e-6));
            assertThat(farAway.page(index).paperRectangle()).isEqualTo(atOrigin.page(index).paperRectangle());
        }
    }

    @Test
    void aZeroHeightAreaIsOneRow() {
        PageLayout fitted = PageLayout.of(gridArea(10, 5, 300, 0), roundGeometry(0), FIT);
        PageLayout tiled = PageLayout.of(gridArea(10, 5, 1200, 0), roundGeometry(0), FULL_SIZE);
        PageLayout point = PageLayout.of(gridArea(10, 5, 0, 0), roundGeometry(0), FIT);

        assertThat(fitted.pageCount()).isEqualTo(1);
        assertThat(fitted.scale()).isEqualTo(1.0);
        assertThat(tiled.rows()).isEqualTo(1);
        assertThat(tiled.columns()).isEqualTo(3);
        assertThat(tiled.page(2).gridRectangle().getHeight()).isZero();
        assertThat(point.pageCount()).isEqualTo(1);
        assertThat(point.scale()).isEqualTo(1.0);
    }

    @Test
    void aPercentageScalesThePrintedLength() {
        PageLayout half = PageLayout.of(gridArea(0, 0, 1000, 100), roundGeometry(0), new ScaleMode.Percentage(50));
        PageLayout twice = PageLayout.of(gridArea(0, 0, 1000, 100), roundGeometry(0), new ScaleMode.Percentage(200));

        assertThat(half.scale()).isEqualTo(0.5);
        assertThat(half.pageCount()).isEqualTo(1);
        assertThat(twice.scale()).isEqualTo(2.0);
        assertThat(twice.columns()).isEqualTo(5);
        // the grid width per page shrinks with the scale: the tiled window at 2 points per pixel
        assertThat(twice.page(0).gridRectangle().getWidth()).isCloseTo(TILED_WIDTH / 2 / PIXELS,
                within(TOLERANCE));
    }

    @Test
    void theSmallestTextFollowsTheScale() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 2 * SINGLE_WIDTH, 100), roundGeometry(0), FIT);

        assertThat(layout.smallestTextPoints(12)).isCloseTo(6, within(TOLERANCE));
    }

    @Test
    void aWindowWithoutRoomIsRefused() {
        PageGeometry noRoom = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, 306, 0, STRIP, OVERLAP);

        PageLayout layout = PageLayout.of(gridArea(0, 0, 100, 100), noRoom, FIT);

        assertThat(layout.problem()).contains(PageLayout.NO_ROOM_KEY);
        assertThat(layout.pageCount()).isZero();
        assertThat(layout.pageCountExact()).isZero();
        assertThat(layout.columns()).isZero();
        assertThat(layout.rows()).isZero();
        assertThatThrownBy(() -> layout.page(0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(PageLayout.of(gridArea(0, 0, 100, 100), roundGeometry(0), FIT).problem()).isEmpty();
    }

    @Test
    void aHeaderBandTallerThanThePageIsRefused() {
        PageLayout layout = PageLayout.of(gridArea(0, 0, 100, 100), roundGeometry(400), FULL_SIZE);

        assertThat(layout.problem()).contains(PageLayout.NO_ROOM_KEY);
    }

    @Test
    void aStripThatEatsTheTiledWindowIsRefusedOnlyWhenTiling() {
        PageGeometry wideStrip = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, MARGIN, 0, 250, OVERLAP);

        assertThat(PageLayout.of(gridArea(0, 0, 100, 100), wideStrip, FULL_SIZE).problem()).isEmpty();
        assertThat(PageLayout.of(gridArea(0, 0, 1000, 100), wideStrip, FULL_SIZE).problem())
                .contains(PageLayout.NO_ROOM_KEY);
    }

    @Test
    void anOverlapLargerThanHalfTheWindowIsClamped() {
        PageGeometry wideOverlap = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, MARGIN, 0, STRIP, 400);

        PageLayout layout = PageLayout.of(gridArea(0, 0, 2000, 100), wideOverlap, FULL_SIZE);

        double step = TILED_WIDTH / 2;
        assertThat(layout.columns()).isEqualTo(1 + (int) Math.ceil((2000 - TILED_WIDTH) / step));
        assertThat(layout.page(1).gridRectangle().getX()).isCloseTo(step / PIXELS, within(TOLERANCE));
    }

    @Test
    void hugeCountsAreCountedWithoutMaterialisingPages() {
        // 3164 × 3164 sheets at 400 %: about ten million pages
        double width = (TILED_WIDTH + STEP_X * 3162.5) / 4;
        double height = (TILED_HEIGHT + STEP_Y * 3162.5) / 4;
        PageLayout layout = PageLayout.of(gridArea(0, 0, width, height), roundGeometry(0),
                new ScaleMode.Percentage(400));

        assertThat(layout.columns()).isEqualTo(3164);
        assertThat(layout.rows()).isEqualTo(3164);
        assertThat(layout.pageCountExact()).isEqualTo(3164L * 3164L);
        assertThat(layout.pageCount()).isEqualTo(3164 * 3164);
        PrintPage last = layout.page(layout.pageCount() - 1);
        assertThat(last.row()).isEqualTo(3163);
        assertThat(last.column()).isEqualTo(3163);
    }

    @Test
    void countsBeyondIntegersSaturate() {
        PageLayout layout = PageLayout.of(new Rectangle2D.Double(0, 0, 1e15, 1e15), roundGeometry(0),
                new ScaleMode.Percentage(400));

        assertThat(layout.pageCountExact()).isEqualTo(Long.MAX_VALUE);
        assertThat(layout.pageCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(layout.columns()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void aBadAreaOrGeometryIsAProgrammingError() {
        assertThatThrownBy(() -> PageLayout.of(new Rectangle2D.Double(0, 0, -1, 4), roundGeometry(0), FIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageLayout.of(new Rectangle2D.Double(0, Double.NaN, 1, 4), roundGeometry(0), FIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageGeometry(Paper.A4, PageOrientation.PORTRAIT, -1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageGeometry(Paper.A4, PageOrientation.PORTRAIT, 1, Double.POSITIVE_INFINITY,
                0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
