package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SheetPlanTest {

    private static final double TOLERANCE = 1e-9;
    private static final double PIXELS = GridPoint.PIXELS_PER_UNIT;
    private static final ScaleMode FULL_SIZE = new ScaleMode.Percentage(100);
    private static final ScaleMode HALF_SIZE = new ScaleMode.Percentage(50);
    private static final ScaleMode FIT = new ScaleMode.FitToOnePage();

    // Carta portrait with round numbers (as PageLayoutTest): a tiled window of 480 × 660 points and a 20 pt overlap,
    // so at 100 % a sheet is 60 × 82.5 grid units and the step between sheets 57.5 × 80.
    private static final PageGeometry GEOMETRY = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, 56, 0, 10,
            20);
    private static final double STEP_X = 57.5;
    private static final double STEP_Y = 80;

    /** Two and a bit sheets wide, one and a bit high at 100 %. */
    private static Rectangle2D tiledArea(double x, double y) {
        return new Rectangle2D.Double(x, y, 130, 90);
    }

    private static Optional<GridPoint> anchorAt(int x, int y) {
        return Optional.of(new GridPoint(x, y));
    }

    @Test
    void withoutAnAnchorTheLayoutIsTheAutomaticOne() {
        List<Rectangle2D> areas = List.of(tiledArea(0, 0), tiledArea(-300, -200), new Rectangle2D.Double(5, 5, 10, 10),
                new Rectangle2D.Double(0, 0, 400, 0), new Rectangle2D.Double(3, 4, 1000, 700));
        for (Rectangle2D area : areas) {
            for (ScaleMode mode : List.of(FULL_SIZE, HALF_SIZE, FIT)) {
                assertSameLayout(SheetPlan.layout(area, GEOMETRY, mode, Optional.empty()),
                        PageLayout.of(area, GEOMETRY, mode));
            }
        }
    }

    @Test
    void anAnchorLeftOfTheAreaStartsTheSheetsOnTheLastLatticeLineBeforeIt() {
        PageLayout layout = SheetPlan.layout(tiledArea(100, 0), GEOMETRY, FULL_SIZE, anchorAt(0, 0));

        assertThat(layout.gridArea().getX()).isCloseTo(STEP_X, within(TOLERANCE));
        assertThat(layout.gridArea().getY()).isCloseTo(0, within(TOLERANCE));
        assertThat(layout.gridArea().getMaxX()).isCloseTo(230, within(TOLERANCE));
        assertThat(layout.gridArea().getMaxY()).isCloseTo(90, within(TOLERANCE));
    }

    @Test
    void anAnchorRightOfTheAreaReachesBackPastIt() {
        PageLayout layout = SheetPlan.layout(tiledArea(100, 0), GEOMETRY, FULL_SIZE, anchorAt(200, 0));

        assertThat(layout.gridArea().getX()).isCloseTo(200 - 2 * STEP_X, within(TOLERANCE));
    }

    @Test
    void anAnchorInsideTheAreaAddsAColumnAndARowBeforeIt() {
        // 2 × 1 sheets laid out automatically
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 80);
        PageLayout layout = SheetPlan.layout(area, GEOMETRY, FULL_SIZE, anchorAt(30, 10));

        assertThat(layout.gridArea().getX()).isCloseTo(30 - STEP_X, within(TOLERANCE));
        assertThat(layout.gridArea().getY()).isCloseTo(10 - STEP_Y, within(TOLERANCE));
        assertThat(layout.columns()).isEqualTo(3);
        assertThat(layout.rows()).isEqualTo(2);
    }

    @Test
    void anAreaStartingExactlyOnALatticeLineAddsNothing() {
        Rectangle2D area = tiledArea(2 * STEP_X, STEP_Y);

        assertSameLayout(SheetPlan.layout(area, GEOMETRY, FULL_SIZE, anchorAt(0, 0)),
                PageLayout.of(area, GEOMETRY, FULL_SIZE));
    }

    @Test
    void negativeCoordinatesFollowTheSameLattice() {
        PageLayout layout = SheetPlan.layout(tiledArea(-300, -200), GEOMETRY, FULL_SIZE, anchorAt(-10, -10));

        // (-300 + 10) / 57.5 = -5.04 → -6 steps; (-200 + 10) / 80 = -2.4 → -3 steps
        assertThat(layout.gridArea().getX()).isCloseTo(-10 - 6 * STEP_X, within(TOLERANCE));
        assertThat(layout.gridArea().getY()).isCloseTo(-10 - 3 * STEP_Y, within(TOLERANCE));
    }

    @Test
    void aFarAnchorOnlyFixesTheLatticeAndAddsNoEmptySheets() {
        Rectangle2D area = tiledArea(0, 0);
        int stepsAway = 1000;
        PageLayout far = SheetPlan.layout(area, GEOMETRY, new ScaleMode.Percentage(80),
                anchorAt(30 + stepsAway * 72, 10));
        PageLayout near = SheetPlan.layout(area, GEOMETRY, new ScaleMode.Percentage(80), anchorAt(30, 10));

        // at 80 % the step is 57.5 / 0.8 = 71.875 units, so 1000 × 72 is not on near's lattice: compare the counts
        PageLayout automatic = PageLayout.of(area, GEOMETRY, new ScaleMode.Percentage(80));
        assertThat(far.columns()).isBetween(automatic.columns(), automatic.columns() + 1);
        assertThat(far.rows()).isBetween(automatic.rows(), automatic.rows() + 1);
        assertThat(far.gridArea().getX()).isGreaterThan(area.getX() - STEP_X / 0.8);
        assertThat(near.gridArea().getX()).isGreaterThan(area.getX() - STEP_X / 0.8);
    }

    @Test
    void anAnchorAWholeNumberOfStepsAwayGivesTheSameSheets() {
        Rectangle2D area = tiledArea(0, 0);
        PageLayout near = SheetPlan.layout(area, GEOMETRY, FULL_SIZE, anchorAt(30, 10));
        // 46 steps of 57.5 = 2645 units; 30 steps of 80 = 2400
        PageLayout far = SheetPlan.layout(area, GEOMETRY, FULL_SIZE, anchorAt(30 + 2645, 10 - 2400));

        assertSameLayout(far, near);
    }

    @Test
    void oneSheetIgnoresTheAnchor() {
        Rectangle2D small = new Rectangle2D.Double(5, 5, 10, 10);
        assertSameLayout(SheetPlan.layout(small, GEOMETRY, FULL_SIZE, anchorAt(1, 1)),
                PageLayout.of(small, GEOMETRY, FULL_SIZE));
        assertSameLayout(SheetPlan.layout(tiledArea(100, 0), GEOMETRY, FIT, anchorAt(0, 0)),
                PageLayout.of(tiledArea(100, 0), GEOMETRY, FIT));
    }

    @Test
    void aRefusedLayoutIgnoresTheAnchor() {
        PageGeometry noRoom = new PageGeometry(Paper.LETTER, PageOrientation.PORTRAIT, 400, 0, 10, 20);
        PageLayout layout = SheetPlan.layout(tiledArea(100, 0), noRoom, FULL_SIZE, anchorAt(0, 0));

        assertThat(layout.problem()).contains(PageLayout.NO_ROOM_KEY);
        assertThat(SheetPlan.anchorApplies(layout, anchorAt(0, 0))).isFalse();
    }

    @Test
    void theWidenedLayoutKeepsTheScaleTheWindowAndTheOverlap() {
        PageLayout automatic = PageLayout.of(tiledArea(100, 0), GEOMETRY, HALF_SIZE);
        PageLayout widened = SheetPlan.layout(tiledArea(100, 0), GEOMETRY, HALF_SIZE, anchorAt(0, 0));

        assertThat(widened.tiled()).isTrue();
        assertThat(widened.scale()).isEqualTo(automatic.scale());
        assertThat(widened.window()).isEqualTo(automatic.window());
        assertThat(widened.overlapPoints()).isEqualTo(automatic.overlapPoints());
    }

    @Test
    void theFirstSheetIsAlwaysOnTheAnchorsLattice() {
        Random random = new Random(20261103);
        for (int attempt = 0; attempt < 2000; attempt++) {
            Rectangle2D area = new Rectangle2D.Double(random.nextInt(2000) - 1000, random.nextInt(2000) - 1000,
                    70 + random.nextInt(500), random.nextInt(400));
            int percent = 10 + random.nextInt(391);
            ScaleMode mode = new ScaleMode.Percentage(percent);
            GridPoint anchor = new GridPoint(random.nextInt(4000) - 2000, random.nextInt(4000) - 2000);
            PageLayout automatic = PageLayout.of(area, GEOMETRY, mode);
            PageLayout layout = SheetPlan.layout(area, GEOMETRY, mode, Optional.of(anchor));
            if (!automatic.tiled()) {
                assertSameLayout(layout, automatic);
                continue;
            }
            double pointsPerUnit = PIXELS * automatic.scale();
            double stepX = (automatic.window().getWidth() - automatic.overlapPoints()) / pointsPerUnit;
            double stepY = (automatic.window().getHeight() - automatic.overlapPoints()) / pointsPerUnit;
            Rectangle2D widened = layout.gridArea();
            assertOnLattice(widened.getX(), anchor.x(), stepX);
            assertOnLattice(widened.getY(), anchor.y(), stepY);
            assertThat(widened.getX()).isLessThanOrEqualTo(area.getX()).isGreaterThan(area.getX() - stepX);
            assertThat(widened.getY()).isLessThanOrEqualTo(area.getY()).isGreaterThan(area.getY() - stepY);
            assertThat(widened.getMaxX()).isCloseTo(area.getMaxX(), within(TOLERANCE));
            assertThat(widened.getMaxY()).isCloseTo(area.getMaxY(), within(TOLERANCE));
            assertThat(layout.tiled()).isTrue();
            assertThat(layout.scale()).isEqualTo(automatic.scale());
            assertThat(layout.window()).isEqualTo(automatic.window());
        }
    }

    @Test
    void theAnchorAppliesOnlyWhenPresentAndTheAutomaticLayoutIsTiled() {
        PageLayout tiled = PageLayout.of(tiledArea(0, 0), GEOMETRY, FULL_SIZE);
        PageLayout single = PageLayout.of(new Rectangle2D.Double(0, 0, 10, 10), GEOMETRY, FULL_SIZE);

        assertThat(SheetPlan.anchorApplies(tiled, anchorAt(0, 0))).isTrue();
        assertThat(SheetPlan.anchorApplies(tiled, Optional.empty())).isFalse();
        assertThat(SheetPlan.anchorApplies(single, anchorAt(0, 0))).isFalse();
    }

    @Test
    void theAnchorOfATiledLayoutIsItsFirstSheetsTopLeft() {
        PageLayout tiled = PageLayout.of(tiledArea(12, -7), GEOMETRY, FULL_SIZE);

        assertThat(SheetPlan.anchorOf(tiled)).isEqualTo(new GridPoint(12, -7));
        // taking the anchor of an automatic layout and laying out with it changes nothing
        assertSameLayout(SheetPlan.layout(tiledArea(12, -7), GEOMETRY, FULL_SIZE,
                Optional.of(SheetPlan.anchorOf(tiled))), tiled);
    }

    @Test
    void aFractionalCornerIsAnchoredOnTheGridPointBeforeIt() {
        PageLayout tiled = PageLayout.of(tiledArea(12.5, -7.25), GEOMETRY, FULL_SIZE);

        assertThat(SheetPlan.anchorOf(tiled)).isEqualTo(new GridPoint(12, -8));
    }

    @Test
    void onlyATiledLayoutHasAnAnchor() {
        PageLayout single = PageLayout.of(new Rectangle2D.Double(0, 0, 10, 10), GEOMETRY, FULL_SIZE);

        assertThatThrownBy(() -> SheetPlan.anchorOf(single)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertOnLattice(double coordinate, int anchor, double step) {
        double steps = (coordinate - anchor) / step;
        assertThat(steps).as("%s from %s by %s", coordinate, anchor, step).isCloseTo(Math.rint(steps),
                within(1e-6));
    }

    private static void assertSameLayout(PageLayout actual, PageLayout expected) {
        assertThat(actual.gridArea()).isEqualTo(expected.gridArea());
        assertThat(actual.tiled()).isEqualTo(expected.tiled());
        assertThat(actual.columns()).isEqualTo(expected.columns());
        assertThat(actual.rows()).isEqualTo(expected.rows());
        assertThat(actual.scale()).isEqualTo(expected.scale());
        assertThat(actual.window()).isEqualTo(expected.window());
        assertThat(actual.overlapPoints()).isEqualTo(expected.overlapPoints());
        assertThat(actual.problem()).isEqualTo(expected.problem());
        for (int index = 0; index < Math.min(actual.pageCount(), 20); index++) {
            assertThat(actual.page(index).gridRectangle()).isEqualTo(expected.page(index).gridRectangle());
        }
    }
}
