package cl.almejo.vsim.ui.canvas;

import java.awt.Graphics2D;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives a drag out of a canvas headless: press, drag a little, switch to the drag out, take what it carries. */
final class CanvasDragSupport {

    private CanvasDragSupport() {
    }

    static Transferable dragOut(CircuitCanvas source, int x, int y) {
        source.getMouseListeners()[0].mousePressed(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        source.getMouseMotionListeners()[0].mouseDragged(new MouseEvent(source, MouseEvent.MOUSE_DRAGGED, 0,
                InputEvent.BUTTON1_DOWN_MASK, x + 16, y + 8, 1, false, MouseEvent.NOBUTTON));
        assertThat(source.beginDragOut()).isTrue();
        return handlerOf(source).createTransferable(source);
    }

    static CanvasTransferHandler handlerOf(CircuitCanvas canvas) {
        return (CanvasTransferHandler) canvas.getTransferHandler();
    }

    static BufferedImage paint(CircuitCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Pixels that differ between the two images inside [fromX, toX) × [fromY, toY). */
    static int differingPixels(BufferedImage first, BufferedImage second, int fromX, int fromY, int toX, int toY) {
        int count = 0;
        for (int x = fromX; x < toX; x++) {
            for (int y = fromY; y < toY; y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
