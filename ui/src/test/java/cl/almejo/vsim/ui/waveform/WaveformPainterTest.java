package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformPainterTest {

    private static final int WIDTH = 200;
    private static final Color SCREEN = new Color(0x101820);
    private static final Color GRID = Color.YELLOW;
    private static final Color TRACE = new Color(0x00E676);

    private final ColorScheme scheme = withDiagram(ColorScheme.LIGHT);

    private static ColorScheme withDiagram(ColorScheme base) {
        return new ColorScheme(base.background(), base.grid(), base.gridLine(), base.wireZero(), base.wireOne(),
                base.wireHighImpedance(), base.wireConflict(), base.wireUnknown(), base.componentStroke(),
                base.componentFill(), base.componentShade(), base.shadow(), base.text(), base.selection(),
                base.preview(), base.displayBackground(), base.segmentOn(), base.segmentOff(), SCREEN, GRID, TRACE);
    }

    /** A 200 px plot from time 0 at {@code pixelsPerUnit}. */
    private static TimeAxis axis(double pixelsPerUnit) {
        TimeAxis axis = new TimeAxis();
        axis.setWidth(WIDTH);
        axis.fit(0, Math.round(WIDTH / pixelsPerUnit));
        return axis;
    }

    private static Trace trace(Object... timesAndValues) {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < timesAndValues.length; index += 2) {
            Object value = timesAndValues[index + 1];
            long time = ((Number) timesAndValues[index]).longValue();
            if (value == null) {
                channel.appendGap(time);
            } else {
                channel.append(time, (Value) value);
            }
        }
        return channel.snapshot();
    }

    private BufferedImage draw(Trace trace, long now, TimeAxis axis) {
        BufferedImage image = new BufferedImage(WIDTH, WaveformPainter.ROW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        WaveformPainter.paint(graphics, new WaveformSnapshot(1, now, Map.of("p1", trace)), List.of("p1"), axis,
                WIDTH, scheme);
        graphics.dispose();
        return image;
    }

    private static Color at(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    private static int count(BufferedImage image, Color color, int left, int right, int top, int bottom) {
        int found = 0;
        for (int x = left; x <= right; x++) {
            for (int y = top; y <= bottom; y++) {
                if (at(image, x, y).equals(color)) {
                    found++;
                }
            }
        }
        return found;
    }

    private static final int HIGH = (int) WaveformPainter.highY(0);
    private static final int LOW = (int) WaveformPainter.lowY(0);
    private static final int MIDDLE = (int) WaveformPainter.middleY(0);

    @Test
    void anEdgeAtTimeOneIsDrawnInPixelColumnTwentyAtTwentyPixelsPerUnit() {
        BufferedImage image = draw(trace(0, Value.ZERO, 1, Value.ONE), 8, axis(20));

        assertThat(at(image, 20, MIDDLE)).isEqualTo(TRACE);
        assertThat(at(image, 10, LOW)).isEqualTo(TRACE);
        assertThat(at(image, 10, HIGH)).isEqualTo(SCREEN);
        assertThat(at(image, 30, HIGH)).isEqualTo(TRACE);
        assertThat(at(image, 30, LOW)).isEqualTo(SCREEN);
        assertThat(at(image, 10, MIDDLE)).isEqualTo(SCREEN);
        assertThat(at(image, 170, HIGH)).as("nothing after the current time").isEqualTo(SCREEN);
    }

    @Test
    void highImpedanceIsADashedMiddleLineAndAConflictARedBand() {
        BufferedImage dashed = draw(trace(0, Value.HIGH_IMPEDANCE), 8, axis(20));
        assertThat(count(dashed, TRACE, 1, 19, MIDDLE, MIDDLE)).isPositive();
        assertThat(count(dashed, SCREEN, 1, 19, MIDDLE, MIDDLE)).isPositive();

        BufferedImage conflict = draw(trace(0, Value.ONE.withConflict()), 8, axis(20));
        assertThat(at(conflict, 10, MIDDLE)).isEqualTo(scheme.wireConflict());
    }

    @Test
    void aWideBusSegmentWritesItsHexValueAndANarrowOneDoesNot() {
        Trace wide = trace(0, Value.of(8, 0x3F));
        BufferedImage band = draw(wide, 8, axis(20));
        assertThat(count(band, TRACE, 61, 99, HIGH + 3, LOW - 3)).as("0x3F inside the band").isPositive();

        Trace narrow = trace(0, Value.of(8, 0x3F), 1, Value.of(8, 0x40), 2, Value.of(8, 0x41), 3, Value.of(8, 0x42));
        BufferedImage segments = draw(narrow, 8, axis(20));
        assertThat(count(segments, TRACE, 5, 15, HIGH + 3, LOW - 3)).as("20 px is too narrow for a value").isZero();
        assertThat(at(segments, 10, HIGH)).as("the band outline").isEqualTo(TRACE);
    }

    @Test
    void aGapIsHatchedAndSoIsTheTimeBeforeATruncatedHistory() {
        BufferedImage gap = draw(trace(0, Value.ONE, 2, null), 8, axis(20));
        assertThat(count(gap, GRID, 50, 59, HIGH + 2, LOW - 2) + count(gap, GRID, 61, 70, HIGH + 2, LOW - 2))
                .isPositive();

        ChannelTrace longHistory = new ChannelTrace(1);
        for (int index = 0; index <= ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS; index++) {
            longHistory.append(index, Value.of(index % 2 == 1));
        }
        Trace truncated = longHistory.snapshot();
        assertThat(truncated.truncated()).isTrue();
        BufferedImage hatched = draw(truncated, 2_000, axis(0.1));
        assertThat(count(hatched, GRID, 10, 90, HIGH + 2, LOW - 2)).isPositive();

        BufferedImage complete = draw(trace(1_024, Value.ONE), 2_000, axis(0.1));
        assertThat(count(complete, GRID, 10, 90, HIGH + 2, LOW - 2)).as("nothing before a complete history").isZero();
    }

    @Test
    void theCursorIsAVerticalLineInTheSelectionColor() {
        TimeAxis axis = axis(20);
        axis.setCursor(5);
        BufferedImage image = draw(trace(0, Value.ZERO), 8, axis);
        assertThat(at(image, 100, 2)).isEqualTo(scheme.selection());
    }

    /** {@code count} events, one per unit from time 0, alternating 0 and 1 (the oldest dropped past the limit). */
    private static Trace everyUnit(int count) {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < count; index++) {
            channel.append(index, Value.of(index % 2 == 1));
        }
        return channel.snapshot();
    }

    private int positionsVisited(Trace trace, long now, TimeAxis axis) {
        BufferedImage image = new BufferedImage(WIDTH, WaveformPainter.ROW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return WaveformPainter.paintRow(graphics, trace, 0, axis, WIDTH, now, scheme);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void aFullHistoryShownWholeVisitsAtMostTwoPositionsPerPixelColumn() {
        int count = ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS;
        Trace dense = everyUnit(count);
        assertThat(dense.eventCount()).isEqualTo(count);
        TimeAxis axis = new TimeAxis();
        axis.setWidth(WIDTH);
        axis.fit(0, count);

        int visited = positionsVisited(dense, count, axis);

        assertThat(visited).as("cost scales with the width, not with %d events", count)
                .isPositive().isLessThanOrEqualTo(2 * WIDTH + 2);
    }

    @Test
    void zoomedInEveryEventIsVisitedOnce() {
        assertThat(positionsVisited(everyUnit(100), 100, axis(20))).isEqualTo(10);
    }

    @Test
    void aBurstInsideOnePixelColumnIsAFullHeightBarAndTheLastValueFollows() {
        // At 0.1 px/unit times 100..109 share pixel column 10; the burst ends high.
        ChannelTrace channel = new ChannelTrace(1);
        channel.append(0, Value.ZERO);
        for (int time = 100; time < 110; time++) {
            channel.append(time, Value.of(time % 2 == 1));
        }
        BufferedImage image = draw(channel.snapshot(), 2_000, axis(0.1));

        assertThat(at(image, 10, HIGH)).isEqualTo(TRACE);
        assertThat(at(image, 10, MIDDLE)).isEqualTo(TRACE);
        assertThat(at(image, 10, LOW)).isEqualTo(TRACE);
        assertThat(at(image, 5, LOW)).isEqualTo(TRACE);
        assertThat(at(image, 5, HIGH)).isEqualTo(SCREEN);
        assertThat(at(image, 50, HIGH)).isEqualTo(TRACE);
        assertThat(at(image, 50, LOW)).isEqualTo(SCREEN);
    }
}
