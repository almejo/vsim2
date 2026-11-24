package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.icons.DrawnCursors;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ZoomCursorsTest {

    @Test
    void headlessTheCursorsAreTheCrosshair() {
        if (!GraphicsEnvironment.isHeadless()) {
            return;
        }
        assertThat(ZoomCursors.zoomIn().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
        assertThat(ZoomCursors.zoomOut().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void theMagnifierHasADarkRingAroundTheLensAndAPlusOrAMinus() {
        BufferedImage zoomIn = ZoomCursors.image(32, DrawnCursors.Sign.PLUS);
        BufferedImage zoomOut = ZoomCursors.image(32, DrawnCursors.Sign.MINUS);
        assertThat(zoomIn.getWidth()).isEqualTo(32);
        int lensCentre = ZoomCursors.HOTSPOT;
        int ring = zoomIn.getRGB(lensCentre + ZoomCursors.LENS_RADIUS, lensCentre);
        assertThat(ring >>> 24).isEqualTo(255);
        assertThat(brightness(ring)).isLessThan(80);
        assertThat(zoomIn.getRGB(31, 0) >>> 24).as("the top right corner stays transparent").isZero();
        int aboveTheCentre = zoomIn.getRGB(lensCentre, lensCentre - 3);
        assertThat(brightness(aboveTheCentre)).as("the «+» has a vertical arm").isLessThan(80);
        int aboveTheCentreOut = zoomOut.getRGB(lensCentre, lensCentre - 3);
        assertThat(brightness(aboveTheCentreOut)).as("the «−» has none").isGreaterThan(180);
        int handle = zoomIn.getRGB(26, 26);
        assertThat(handle >>> 24).as("the handle towards the bottom right").isEqualTo(255);
    }

    private static int brightness(int argb) {
        return (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
    }
}
