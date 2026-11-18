package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link WaveformPainter#paintSteps}: a row of equal steps (a machine test's columns) drawn as a recorded row. */
class WaveformPainterStepsTest {

    private static final int STEP = 20;

    @Test
    void eachStepIsDrawnAtItsLevelWithAnEdgeBetweenDifferentSteps() {
        BufferedImage image = new BufferedImage(3 * STEP, WaveformPainter.ROW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            WaveformPainter.paintSteps(graphics, List.of(Optional.of(Value.ZERO), Optional.of(Value.ZERO),
                    Optional.of(Value.ONE)), 0, STEP, 0, ColorSchemes.DARK, null, null);
        } finally {
            graphics.dispose();
        }
        int trace = ColorSchemes.DARK.diagramTrace().getRGB();
        int low = (int) WaveformPainter.lowY(0);
        int high = (int) WaveformPainter.highY(0);
        assertThat(image.getRGB(5, low)).as("step 1 low").isEqualTo(trace);
        assertThat(image.getRGB(STEP + 5, low)).as("step 2 low, joined to step 1").isEqualTo(trace);
        assertThat(image.getRGB(2 * STEP + 5, high)).as("step 3 high").isEqualTo(trace);
        assertThat(image.getRGB(2 * STEP, (low + high) / 2)).as("the edge between 2 and 3").isEqualTo(trace);
        assertThat(new Color(image.getRGB(STEP, (low + high) / 2))).as("no edge between equal steps")
                .isNotEqualTo(ColorSchemes.DARK.diagramTrace());
    }
}
