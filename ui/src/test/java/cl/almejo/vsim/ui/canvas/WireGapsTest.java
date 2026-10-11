package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.canvas.WireGaps.Cut;
import cl.almejo.vsim.ui.canvas.WireGaps.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WireGapsTest {

    private static final double THIN = 2.5;
    private static final double BUS = 4.5;
    /** The cap reach of a round-capped 2.5 px vertical stroke: half its width. */
    private static final double THIN_ROUND_CAP = THIN / 2;
    /** The cap reach of a round-capped 4.5 px vertical stroke. */
    private static final double BUS_ROUND_CAP = BUS / 2;
    /** A butt cap (the dashed mismatch stroke) paints nothing past the end of a span. */
    private static final double BUTT_CAP = 0;

    @Test
    void aWireWithoutCutsIsOneSpan() {
        assertThat(WireGaps.visibleSpans(0, 80, List.of(), THIN_ROUND_CAP)).containsExactly(new Span(0, 80));
    }

    @Test
    void oneCutLeavesOutHalfBothStrokesPlusTheClearanceOnEachSide() {
        // h = 2.5 / 2 + 2 + 2.5 / 2 = 4.5
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(40, THIN)), THIN_ROUND_CAP))
                .containsExactly(new Span(0, 35.5), new Span(44.5, 80));
    }

    @Test
    void aWiderHorizontalStrokeWidensTheGap() {
        // h = 4.5 / 2 + 2 + 2.5 / 2 = 5.5
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(40, BUS)), THIN_ROUND_CAP))
                .containsExactly(new Span(0, 34.5), new Span(45.5, 80));
    }

    @Test
    void aWiderVerticalStrokeWidensTheGapToCancelItsRoundCap() {
        // h = 2.5 / 2 + 2 + 4.5 / 2 = 5.5
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(40, THIN)), BUS_ROUND_CAP))
                .containsExactly(new Span(0, 34.5), new Span(45.5, 80));
    }

    @Test
    void aButtCappedVerticalLeavesOnlyTheClearanceBesideTheHorizontalStroke() {
        // h = 2.5 / 2 + 2 + 0 = 3.25, whatever the vertical width
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(40, THIN)), BUTT_CAP))
                .containsExactly(new Span(0, 36.75), new Span(43.25, 80));
    }

    @Test
    void overlappingGapsMergeAndCutsMayComeInAnyOrder() {
        // gaps [43.5, 52.5] and [35.5, 44.5] overlap
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(48, THIN), new Cut(40, THIN)), THIN_ROUND_CAP))
                .containsExactly(new Span(0, 35.5), new Span(52.5, 80));
    }

    @Test
    void gapsThatJustTouchLeaveNoEmptySpanBetweenThem() {
        // gaps [35.5, 44.5] and [44.5, 53.5]
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(40, THIN), new Cut(49, THIN)), THIN_ROUND_CAP))
                .containsExactly(new Span(0, 35.5), new Span(53.5, 80));
    }

    @Test
    void aGapNeverReachesPastTheEndsOfTheWire() {
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(4, THIN)), THIN_ROUND_CAP))
                .containsExactly(new Span(8.5, 80));
        assertThat(WireGaps.visibleSpans(0, 80, List.of(new Cut(76, THIN)), THIN_ROUND_CAP))
                .containsExactly(new Span(0, 71.5));
    }

    @Test
    void cutsOnTheEndsOrOutsideTheWireAreIgnored() {
        assertThat(WireGaps.visibleSpans(8, 80, List.of(new Cut(8, THIN), new Cut(80, THIN), new Cut(100, THIN),
                new Cut(-4, THIN)), THIN_ROUND_CAP)).containsExactly(new Span(8, 80));
    }
}
