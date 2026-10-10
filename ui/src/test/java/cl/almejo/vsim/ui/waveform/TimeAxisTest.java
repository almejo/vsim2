package cl.almejo.vsim.ui.waveform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimeAxisTest {

    private final TimeAxis axis = new TimeAxis();

    /** An 800 px plot showing {@code from} to {@code to} (so the scale is 800 / (to - from), clamped). */
    private TimeAxis showing(long from, long to) {
        axis.setWidth(800);
        axis.fit(from, to);
        return axis;
    }

    @Test
    void aNewAxisShowsTheLastFourThousandUnitsAndFollowsTheCurrentTime() {
        axis.setWidth(800);
        axis.follow(10_000);
        assertThat(axis.isFollowing()).isTrue();
        assertThat(axis.scale()).isCloseTo(0.2, within(1e-12));
        assertThat(axis.startTime()).isCloseTo(6_000, within(1e-9));
        assertThat(axis.endTime()).isCloseTo(10_000, within(1e-9));
        assertThat(axis.xOf(8_000)).isCloseTo(400, within(1e-9));
        assertThat(axis.timeAt(400)).isCloseTo(8_000, within(1e-9));
    }

    @Test
    void theScaleStaysBetweenGateDelaysAndManyClockPeriods() {
        axis.setWidth(800);
        for (int step = 0; step < 20; step++) {
            axis.zoomIn();
        }
        assertThat(axis.scale()).isEqualTo(TimeAxis.MAXIMUM_SCALE);
        for (int step = 0; step < 40; step++) {
            axis.zoomOut();
        }
        assertThat(axis.scale()).isEqualTo(TimeAxis.MINIMUM_SCALE);
        assertThat(showing(0, 1).scale()).isEqualTo(TimeAxis.MAXIMUM_SCALE);
        assertThat(showing(0, 100_000_000).scale()).isEqualTo(TimeAxis.MINIMUM_SCALE);
    }

    @Test
    void scrollingByHandStopsFollowingAndZoomKeepsTheTimeUnderThePointer() {
        axis.setWidth(800);
        axis.follow(10_000);
        axis.scrollBy(-100);
        assertThat(axis.isFollowing()).isFalse();
        assertThat(axis.startTime()).isCloseTo(5_500, within(1e-9));

        double under = axis.timeAt(200);
        axis.zoomAround(200, 2);
        assertThat(axis.scale()).isCloseTo(0.4, within(1e-12));
        assertThat(axis.timeAt(200)).isCloseTo(under, within(1e-9));

        axis.follow(20_000);
        assertThat(axis.startTime()).as("not following any more").isCloseTo(5_500 + 1_000 - 500, within(1e-9));
    }

    @Test
    void whileFollowingZoomKeepsTheRightEdge() {
        axis.setWidth(800);
        axis.follow(10_000);
        axis.zoomIn();
        assertThat(axis.isFollowing()).isTrue();
        assertThat(axis.endTime()).isCloseTo(10_000, within(1e-9));
        axis.setFollowing(false);
        axis.setFollowing(true);
        axis.follow(12_000);
        assertThat(axis.endTime()).isCloseTo(12_000, within(1e-9));
    }

    @Test
    void ticksFollowOneTwoFiveSteps() {
        assertThat(showing(0, 20).tickSpacing()).isEqualTo(2);           // 40 px per unit
        assertThat(showing(0, 40).tickSpacing()).isEqualTo(5);           // 20 px per unit
        assertThat(showing(0, 800).tickSpacing()).isEqualTo(100);        // 1 px per unit
        assertThat(showing(0, 4_000).tickSpacing()).isEqualTo(500);      // 0.2 px per unit
        assertThat(showing(0, 1_600_000).tickSpacing()).isEqualTo(200_000); // 1/2000 px per unit
        assertThat(showing(0, 800).ticks()).containsExactly(0L, 100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L);
        assertThat(showing(150, 950).ticks()).startsWith(200L).endsWith(900L);
    }

    @Test
    void aFaintLineMarksEveryUnitFromEightPixelsPerUnit() {
        assertThat(showing(0, 100).showsUnitGrid()).isTrue();
        assertThat(showing(0, 101).showsUnitGrid()).isFalse();
    }

    @Test
    void showDelaysUsesTwentyPixelsPerUnitAroundTheGivenTime() {
        axis.setWidth(800);
        axis.follow(5_000);
        axis.showDelays(1_000);
        assertThat(axis.scale()).isEqualTo(TimeAxis.DELAY_SCALE);
        assertThat(axis.isFollowing()).isFalse();
        assertThat(axis.startTime()).isCloseTo(980, within(1e-9));
        assertThat(axis.xOf(1_001) - axis.xOf(1_000)).isCloseTo(20, within(1e-9));
        axis.centerOn(2_000);
        assertThat(axis.timeAt(400)).isCloseTo(2_000, within(1e-9));
    }

    @Test
    void aClickSnapsToAnEdgeWithinFourPixels() {
        showing(0, 800); // 1 px per unit
        List<Long> edges = List.of(100L, 110L);
        assertThat(axis.snap(103, edges)).isEqualTo(100);
        assertThat(axis.snap(105.5, edges)).isEqualTo(106);
        assertThat(axis.snap(107, edges)).isEqualTo(110);
        assertThat(axis.snap(300, edges)).isEqualTo(300);
        showing(0, 8_000); // 0.1 px per unit
        assertThat(axis.snap(10.3, edges)).isEqualTo(100);
    }

    @Test
    void theCursorIsSet() {
        assertThat(axis.cursor()).isEmpty();
        axis.setCursor(42);
        assertThat(axis.cursor()).hasValue(42);
    }

    @Test
    void timesAreWrittenWithSpacesBetweenThousands() {
        assertThat(TimeAxis.format(0)).isEqualTo("0");
        assertThat(TimeAxis.format(12_345)).isEqualTo("12 345");
        assertThat(TimeAxis.format(1_234_567)).isEqualTo("1 234 567");
        assertThat(TimeAxis.format(-5)).isEqualTo("-5");
    }
}
