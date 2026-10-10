package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class SelectToolHoverTest {

    private static final int PIN_PIXEL = 60;

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    /** The overlay painted with the grid point (10, 10) at pixel (60, 60). */
    private BufferedImage hoverImage() {
        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int shift = PIN_PIXEL - 10 * GridPoint.PIXELS_PER_UNIT;
        graphics.translate(shift, shift);
        tool.paintOverlay(graphics, context);
        graphics.dispose();
        return image;
    }

    private int alphaAt(BufferedImage image, int offsetX, int offsetY) {
        return image.getRGB(PIN_PIXEL + offsetX, PIN_PIXEL + offsetY) >>> 24;
    }

    @Test
    void hoveringABusPinDrawsASquareRingReachingTheCorner() {
        place(context.openDocument(), StandardComponentTypes.BUS_SPLITTER, 10, 10, Map.of());
        tool.mouseMoved(context, TestToolContext.at(10, 10));
        BufferedImage image = hoverImage();
        assertThat(alphaAt(image, 4, 4)).isPositive();
        assertThat(alphaAt(image, -5, -5)).isPositive();
    }

    @Test
    void hoveringABusPinDrawsTheWidthTagBesideIt() {
        place(context.openDocument(), StandardComponentTypes.BUS_SPLITTER, 10, 10, Map.of());
        tool.mouseMoved(context, TestToolContext.at(10, 10));
        BufferedImage image = hoverImage();
        boolean tagPainted = false;
        for (int offsetX = 7; offsetX < 20; offsetX++) {
            for (int offsetY = -16; offsetY < -2; offsetY++) {
                tagPainted |= alphaAt(image, offsetX, offsetY) > 0;
            }
        }
        assertThat(tagPainted).isTrue();
    }

    @Test
    void hoveringASingleBitPinKeepsTheCircleOnly() {
        place(context.openDocument(), StandardComponentTypes.NOT, 10, 8, Map.of());
        tool.mouseMoved(context, TestToolContext.at(10, 10));
        assertThat(tool.hoverPin()).isEqualTo(new GridPoint(10, 10));
        BufferedImage image = hoverImage();
        assertThat(alphaAt(image, 4, 0)).isPositive();
        assertThat(alphaAt(image, 4, 4)).isZero();
        assertThat(alphaAt(image, -5, -5)).isZero();
    }
}
