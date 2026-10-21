package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** The background of the canvas: dots, lines (every 2 units, stronger every 10) or nothing, thinning out with zoom. */
class GridBackgroundTest {

    private static final int SIZE = 200;
    private static final ColorScheme SCHEME = ColorScheme.LIGHT;

    private final CircuitRenderer renderer = new CircuitRenderer(StandardComponentTypes.registry(),
            PainterRegistry.standard(), SCHEME);

    /** The background at {@code zoom} with grid point (50, 50) in the middle (at 100 % and 25 % the origin is 0, 0). */
    private BufferedImage paint(GridStyle style, double zoom) {
        Viewport viewport = new Viewport();
        viewport.showAt(zoom, SIZE / 2.0 / (8 * zoom), SIZE / 2.0 / (8 * zoom), SIZE, SIZE);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        renderer.paintBackground(graphics, viewport, SIZE, SIZE, style);
        graphics.dispose();
        return image;
    }

    /** {@code color} painted over {@code below}, as one pixel shows it. */
    private static int over(Color color, Color below) {
        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = pixel.createGraphics();
        graphics.setColor(below);
        graphics.fillRect(0, 0, 1, 1);
        graphics.setColor(color);
        graphics.fillRect(0, 0, 1, 1);
        graphics.dispose();
        return pixel.getRGB(0, 0);
    }

    private static int background() {
        return SCHEME.background().getRGB();
    }

    private static int minorLine() {
        return over(SCHEME.gridLine(), SCHEME.background());
    }

    private static int majorLine() {
        return over(SCHEME.gridLineMajor(), SCHEME.background());
    }

    @Test
    void dotsAreTodaysGridOnePixelOnEveryUnit() {
        BufferedImage image = paint(GridStyle.DOTS, 1);
        assertThat(image.getRGB(8, 8)).isEqualTo(SCHEME.grid().getRGB());
        assertThat(image.getRGB(12, 12)).isEqualTo(background());
        assertThat(image.getRGB(16, 12)).as("no line between the dots").isEqualTo(background());
    }

    @Test
    void linesAreDrawnEveryTwoUnitsAndTheTenthIsStronger() {
        BufferedImage image = paint(GridStyle.LINES, 1);
        assertThat(image.getRGB(16, 5)).as("x = 2").isEqualTo(minorLine());
        assertThat(image.getRGB(5, 16)).as("y = 2").isEqualTo(minorLine());
        assertThat(image.getRGB(8, 5)).as("x = 1 has no line").isEqualTo(background());
        assertThat(image.getRGB(80, 5)).as("x = 10").isEqualTo(majorLine());
        assertThat(image.getRGB(5, 160)).as("y = 20").isEqualTo(majorLine());
        assertThat(image.getRGB(9, 9)).as("no dots with lines").isEqualTo(background());
        assertThat(majorLine()).isNotEqualTo(minorLine()).isNotEqualTo(background());
        assertThat(minorLine()).isNotEqualTo(background());
    }

    @Test
    void theLinesAreOneLogicalPixelWide() {
        BufferedImage image = paint(GridStyle.LINES, 4);
        int lineX = -1;
        for (int x = 0; x < SIZE && lineX < 0; x++) {
            if (image.getRGB(x, 3) != background()) {
                lineX = x;
            }
        }
        assertThat(lineX).isGreaterThanOrEqualTo(0);
        assertThat(image.getRGB(lineX + 1, 3)).isEqualTo(background());
    }

    @Test
    void nothingLeavesOnlyTheBackground() {
        BufferedImage image = paint(GridStyle.NONE, 1);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                assertThat(image.getRGB(x, y)).isEqualTo(background());
            }
        }
    }

    @Test
    void atAQuarterOnlyTheTenUnitLinesAreLeft() {
        BufferedImage image = paint(GridStyle.LINES, 0.25);
        assertThat(image.getRGB(4, 3)).as("x = 2 is 4 px away: hidden").isEqualTo(background());
        assertThat(image.getRGB(20, 3)).as("x = 10").isEqualTo(majorLine());
    }

    @Test
    void theFinestLinesShownDependOnTheirSpacingOnScreen() {
        assertThat(CircuitRenderer.finestLineStep(8)).hasValue(2);
        assertThat(CircuitRenderer.finestLineStep(3)).hasValue(2);
        assertThat(CircuitRenderer.finestLineStep(2.9)).hasValue(10);
        assertThat(CircuitRenderer.finestLineStep(0.6)).hasValue(10);
        assertThat(CircuitRenderer.finestLineStep(0.59)).isEmpty();
    }
}
