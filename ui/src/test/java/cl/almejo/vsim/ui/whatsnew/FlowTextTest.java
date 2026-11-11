package cl.almejo.vsim.ui.whatsnew;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Words and keycaps flow with their spaces, also on a scaled screen (a HiDPI transform). */
class FlowTextTest {

    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final int SCALE = 2;
    private static final int WIDTH = 300;

    /** What {@code paint} draws at {@value #SCALE}x with the hints of the card, black on white. */
    private static BufferedImage painted(Consumer<Graphics2D> paint) {
        BufferedImage image = new BufferedImage(WIDTH * SCALE, 40 * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = image.createGraphics();
        painter.setColor(Color.WHITE);
        painter.fillRect(0, 0, image.getWidth(), image.getHeight());
        painter.scale(SCALE, SCALE);
        painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paint.accept(painter);
        painter.dispose();
        return image;
    }

    /** The rightmost column with ink. */
    private static int inkRight(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (new Color(image.getRGB(x, y)).getRed() < 128) {
                    return x;
                }
            }
        }
        return -1;
    }

    @Test
    void wordsSitWhereTheWholeLineWouldOnAScaledScreen() {
        String line = "Ventana ▸ Dividir ▸ A los lados te deja ver dos";
        BufferedImage flowed = painted(painter -> new FlowText(List.of(new FlowText.Words(line)))
                .paintOutlined(painter, FONT, 0, 0, WIDTH, Color.BLACK, Color.BLUE, Color.BLACK));
        BufferedImage whole = painted(painter -> {
            painter.setFont(FONT);
            painter.setColor(Color.BLACK);
            painter.drawString(line, 0, 20);
        });

        assertThat(inkRight(flowed)).as("where the last word ends")
                .isBetween(inkRight(whole) - SCALE, inkRight(whole) + SCALE);
    }
}
