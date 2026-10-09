package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class PainterSupportTest {

    private static final int SIZE = 60;
    private static final double CENTER = 30;

    private static BufferedImage renderText(int quarterTurns) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, SIZE, SIZE);
        graphics.rotate(quarterTurns * Math.PI / 2, CENTER, CENTER);
        // an asymmetric glyph makes mirroring and rotation visible
        PainterSupport.centeredText(graphics, "F", CENTER, CENTER, 24f, Color.WHITE);
        graphics.dispose();
        return image;
    }

    private static void assertSameImage(BufferedImage actual, BufferedImage expected) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                assertThat(actual.getRGB(x, y)).as("pixel %d,%d", x, y).isEqualTo(expected.getRGB(x, y));
            }
        }
    }

    @Test
    void textReadsUprightInEveryRotation() {
        BufferedImage upright = renderText(0);
        for (int quarterTurns = 1; quarterTurns < 4; quarterTurns++) {
            assertSameImage(renderText(quarterTurns), upright);
        }
    }
}
