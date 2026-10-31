package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** The seven cursors of spec §8.5, each drawn in a 8 × 16 cell at 4×. */
class TerminalCursorPainterTest {

    private static final int SCALE = 4;
    private static final Rectangle2D CELL = new Rectangle2D.Double(10, 10, 8, 16);
    private static final Color TEXT = new Color(0xE8E8E8);
    private static final int IMAGE_SIZE = 160;

    private static BufferedImage draw(TerminalCursorShape shape, double automanSeconds) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(SCALE, SCALE);
        TerminalCursorPainter.paint(graphics, shape, CELL, TEXT, automanSeconds);
        graphics.dispose();
        return image;
    }

    /** Whether the device pixel at the cell's local point (x, y) is painted. */
    private static boolean painted(BufferedImage image, double localX, double localY) {
        return (image.getRGB((int) ((CELL.getX() + localX) * SCALE), (int) ((CELL.getY() + localY) * SCALE)) >>> 24)
                != 0;
    }

    private static int paintedOutside(BufferedImage image, Rectangle2D allowed) {
        int count = 0;
        for (int x = 0; x < IMAGE_SIZE; x++) {
            for (int y = 0; y < IMAGE_SIZE; y++) {
                if ((image.getRGB(x, y) >>> 24) != 0 && !allowed.contains(x / (double) SCALE, y / (double) SCALE)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void everyStillShapeDrawsInsideItsCell() {
        for (TerminalCursorShape shape : TerminalCursorShape.values()) {
            if (shape == TerminalCursorShape.AUTOMAN) {
                continue;
            }
            BufferedImage image = draw(shape, Double.NaN);
            assertThat(paintedOutside(image, CELL)).as(shape.name()).isZero();
        }
    }

    @Test
    void eachShapeHasItsOwnLook() {
        BufferedImage block = draw(TerminalCursorShape.BLOCK, Double.NaN);
        assertThat(painted(block, 4, 8)).as("block: the middle").isTrue();

        BufferedImage hollow = draw(TerminalCursorShape.HOLLOW, Double.NaN);
        assertThat(painted(hollow, 0.2, 8)).as("hollow: the left side").isTrue();
        assertThat(painted(hollow, 4, 0.2)).as("hollow: the top").isTrue();
        assertThat(painted(hollow, 4, 8)).as("hollow: an empty middle").isFalse();

        BufferedImage underline = draw(TerminalCursorShape.UNDERLINE, Double.NaN);
        assertThat(painted(underline, 4, 15.5)).as("underline: the bottom").isTrue();
        assertThat(painted(underline, 4, 8)).isFalse();

        BufferedImage bar = draw(TerminalCursorShape.BAR, Double.NaN);
        assertThat(painted(bar, 1, 8)).as("bar: the left").isTrue();
        assertThat(painted(bar, 5, 8)).isFalse();

        BufferedImage ibeam = draw(TerminalCursorShape.IBEAM, Double.NaN);
        assertThat(painted(ibeam, 3, 8)).as("I-beam: the bar").isTrue();
        assertThat(painted(ibeam, 0.5, 0.5)).as("I-beam: the top serif").isTrue();
        assertThat(painted(ibeam, 5, 15.5)).as("I-beam: the bottom serif").isTrue();
        assertThat(painted(ibeam, 0.5, 8)).isFalse();

        BufferedImage none = draw(TerminalCursorShape.NONE, Double.NaN);
        assertThat(paintedOutside(none, new Rectangle2D.Double())).isZero();
    }

    @Test
    void automansStarMovesWithTimeAndStaysStillWithoutIt() {
        BufferedImage first = draw(TerminalCursorShape.AUTOMAN, 1.0);
        BufferedImage later = draw(TerminalCursorShape.AUTOMAN, 1.3);
        assertThat(PainterTesting.differentPixels(first, later)).as("frames differ over time").isGreaterThan(20);

        BufferedImage still = draw(TerminalCursorShape.AUTOMAN, Double.NaN);
        BufferedImage stillAgain = draw(TerminalCursorShape.AUTOMAN, Double.NaN);
        assertThat(PainterTesting.differentPixels(still, stillAgain)).isZero();
        assertThat(painted(still, 4, 8)).as("the star sits on the cell").isTrue();
    }

    @Test
    void automansStarAndHaloStayNearTheCell() {
        Rectangle2D near = new Rectangle2D.Double(CELL.getX() - 8, CELL.getY() - 8, CELL.getWidth() + 16,
                CELL.getHeight() + 16);
        for (double seconds = 0; seconds < 3; seconds += 0.37) {
            assertThat(paintedOutside(draw(TerminalCursorShape.AUTOMAN, seconds), near)).as("at " + seconds)
                    .isZero();
        }
        assertThat(TerminalCursorPainter.reach(CELL)).isEqualTo(near);
    }
}
