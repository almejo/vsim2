package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionCursorsTest {

    @Test
    void headlessTheCursorsAreTheArrow() {
        if (!GraphicsEnvironment.isHeadless()) {
            return;
        }
        assertThat(SelectionCursors.adding()).isEqualTo(Cursor.getDefaultCursor());
        assertThat(SelectionCursors.removing()).isEqualTo(Cursor.getDefaultCursor());
    }

    @Test
    void theImageHasADarkArrowAtTheTipAndADifferentBadgeForEachMode() {
        BufferedImage adding = SelectionCursors.image(32, false);
        BufferedImage removing = SelectionCursors.image(32, true);
        assertThat(adding.getWidth()).isEqualTo(32);
        int insideArrow = adding.getRGB(SelectionCursors.HOTSPOT + 2, SelectionCursors.HOTSPOT + 6);
        assertThat(insideArrow >>> 24).isEqualTo(255);
        assertThat(brightness(insideArrow)).isLessThan(80);
        assertThat(adding.getRGB(31, 0) >>> 24).isZero(); // the top right corner stays transparent
        boolean different = false;
        for (int y = 0; y < 32 && !different; y++) {
            for (int x = 0; x < 32 && !different; x++) {
                different = adding.getRGB(x, y) != removing.getRGB(x, y);
            }
        }
        assertThat(different).isTrue();
    }

    private static int brightness(int argb) {
        return (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
    }
}
