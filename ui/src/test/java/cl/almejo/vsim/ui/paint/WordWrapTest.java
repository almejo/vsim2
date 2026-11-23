package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class WordWrapTest {

    private final Graphics2D graphics = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private final FontMetrics metrics = graphics.getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 10));

    @Test
    void wordsFillEachLineAndAWordWiderThanTheWidthStaysWholeOnItsOwnLine() {
        int width = metrics.stringWidth("uno dos");

        assertThat(WordWrap.lines(new String[] {"uno", "dos", "tres", "anchísimapalabra", "x"}, metrics, width))
                .containsExactly("uno dos", "tres", "anchísimapalabra", "x");
    }

    @Test
    void noWordsMakeNoLines() {
        assertThat(WordWrap.lines(new String[] {""}, metrics, 100)).isEmpty();
    }
}
