package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TimeDiagramPainterTest {

    /** Drawn 4 times larger so one document pixel covers whole image pixels. */
    private static final double SCALE = 4;
    /** The component sits at grid (2, 2): 16 document pixels from the image origin. */
    private static final double ORIGIN = 16;
    private static final int INTERVAL = 150;
    private static final Color SCREEN = new Color(0x101820);
    private static final Color GRID = Color.YELLOW;
    private static final Color TRACE = new Color(0x00E676);

    private final ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.TIME_DIAGRAM);
    private final ColorScheme scheme = SchemeVariants.withDiagram(ColorScheme.LIGHT, SCREEN, GRID, TRACE);

    private BufferedImage draw(VisibleState state) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(2, 2), Rotation.R0, Map.of(), "");
        BufferedImage image = new BufferedImage(240, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(SCALE, SCALE);
        ComponentDrawing.paint(graphics, type, component, state, scheme, PainterRegistry.standard());
        graphics.dispose();
        return image;
    }

    private static Color colorAt(BufferedImage image, double localX, double localY) {
        return new Color(image.getRGB((int) Math.floor((ORIGIN + localX) * SCALE),
                (int) Math.floor((ORIGIN + localY) * SCALE)), true);
    }

    /** {@code count} samples, one every {@link #INTERVAL} time units from 0, all with the same channel values. */
    private static VisibleState.TimeDiagramState samples(int count, Value... channels) {
        List<TimeDiagramSample> samples = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            samples.add(new TimeDiagramSample((long) index * INTERVAL, List.of(channels)));
        }
        return new VisibleState.TimeDiagramState(samples);
    }

    private double center(int channel) {
        return TimeDiagramPainter.channelCenter(type, channel);
    }

    /** The middle of the newest sample. */
    private double newest() {
        return TimeDiagramPainter.plotRight(type) - TimeDiagramPainter.PITCH / 2;
    }

    @Test
    void theScreenUsesTheSchemeDiagramBackground() {
        BufferedImage image = draw(VisibleState.NONE);
        assertThat(colorAt(image, 20, 8)).isEqualTo(SCREEN);
    }

    @Test
    void zeroAndOneAreDrawnInTheTraceColorAtTheirLevels() {
        BufferedImage image = draw(samples(12, Value.ONE, Value.ZERO, Value.ZERO, Value.ZERO));
        double swing = TimeDiagramPainter.SWING;
        assertThat(colorAt(image, newest(), center(0) - swing)).isEqualTo(TRACE);
        assertThat(colorAt(image, newest(), center(0) + swing)).isEqualTo(SCREEN);
        assertThat(colorAt(image, newest(), center(1) + swing)).isEqualTo(TRACE);
        assertThat(colorAt(image, newest(), center(1) - swing)).isEqualTo(SCREEN);
    }

    @Test
    void aConflictIsARedBand() {
        BufferedImage image = draw(samples(12, Value.ZERO, Value.ZERO, Value.ZERO, Value.ONE.withConflict()));
        assertThat(colorAt(image, newest(), center(3))).isEqualTo(scheme.wireConflict());
        assertThat(colorAt(image, newest(), center(3) - TimeDiagramPainter.SWING + 0.5)).isEqualTo(scheme.wireConflict());
    }

    @Test
    void highImpedanceIsADashedMiddleLine() {
        BufferedImage image = draw(samples(12, Value.ZERO, Value.ZERO, Value.HIGH_IMPEDANCE, Value.ZERO));
        double swing = TimeDiagramPainter.SWING;
        assertThat(colorAt(image, newest(), center(2) - swing)).isEqualTo(SCREEN);
        assertThat(colorAt(image, newest(), center(2) + swing)).isEqualTo(SCREEN);
        int traced = 0;
        int gaps = 0;
        int row = (int) Math.floor((ORIGIN + center(2)) * SCALE);
        int from = (int) Math.ceil((ORIGIN + TimeDiagramPainter.sampleLeft(type, 1, 12)) * SCALE);
        int to = (int) Math.floor((ORIGIN + TimeDiagramPainter.sampleLeft(type, 10, 12)) * SCALE) - 2;
        for (int column = from; column < to; column++) {
            Color color = new Color(image.getRGB(column, row), true);
            if (color.equals(TRACE)) {
                traced++;
            } else if (color.equals(SCREEN)) {
                gaps++;
            }
        }
        assertThat(traced).isGreaterThan(4);
        assertThat(gaps).isGreaterThan(4);
    }

    @Test
    void aFaintLineMarksEveryTenthSample() {
        BufferedImage image = draw(samples(12, Value.ZERO, Value.ZERO, Value.ZERO, Value.ZERO));
        double between = (center(0) + center(1)) / 2;
        assertThat(colorAt(image, TimeDiagramPainter.sampleLeft(type, 10, 12), between)).isEqualTo(GRID);
        assertThat(colorAt(image, TimeDiagramPainter.sampleLeft(type, 5, 12) + 0.5, between)).isEqualTo(SCREEN);
    }

    @Test
    void channelNamesAreWrittenAtTheLeft() {
        BufferedImage image = draw(VisibleState.NONE);
        for (int channel = 0; channel < type.pinCount(); channel++) {
            int written = 0;
            for (double x = TimeDiagramPainter.plotLeft(); x < TimeDiagramPainter.plotLeft() + TimeDiagramPainter.NAME_COLUMN; x += 0.25) {
                for (double y = center(channel) - 2; y < center(channel) + 2; y += 0.25) {
                    if (!colorAt(image, x, y).equals(SCREEN)) {
                        written++;
                    }
                }
            }
            assertThat(written).as("channel " + channel).isGreaterThan(10);
        }
    }

    @Test
    void aLongHistoryShowsOnlyTheNewestSamplesThatFit() {
        BufferedImage image = draw(samples(10_000, Value.ONE, Value.ZERO, Value.ZERO, Value.ZERO));
        double low = center(1) + TimeDiagramPainter.SWING;
        assertThat(colorAt(image, TimeDiagramPainter.plotLeft() + 0.5, low)).isEqualTo(TRACE);
        assertThat(colorAt(image, TimeDiagramPainter.plotLeft() - 1, low)).isEqualTo(SCREEN);
        assertThat(TimeDiagramPainter.capacity(type)).isGreaterThanOrEqualTo(24);
        assertThat(TimeDiagramPainter.visibleSamples(type, samples(10_000, Value.ONE)))
                .hasSize(TimeDiagramPainter.capacity(type));
    }

    @Test
    void samplesWithFewerValuesThanChannelsOrNoIntervalStillDraw() {
        BufferedImage image = draw(samples(5, Value.ONE));
        assertThat(colorAt(image, newest(), center(1) - TimeDiagramPainter.SWING)).isEqualTo(SCREEN);
        assertThat(colorAt(image, newest(), center(1) + TimeDiagramPainter.SWING)).isEqualTo(SCREEN);
        BufferedImage scratch = new BufferedImage(60, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scratch.createGraphics();
        assertThatCode(() -> new TimeDiagramPainter().paint(graphics, type, Map.of(), samples(30, Value.ZERO), scheme))
                .doesNotThrowAnyException();
        graphics.dispose();
    }
}
