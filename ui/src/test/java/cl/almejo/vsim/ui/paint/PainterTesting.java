package cl.almejo.vsim.ui.paint;

import java.awt.image.BufferedImage;

/** Pixel counts shared by the painter tests. */
final class PainterTesting {

    private PainterTesting() {
    }

    /** How many pixels differ between two images of the same size. */
    static int differentPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** How many pixels of {@code image} are exactly {@code rgb}. */
    static int pixelsOf(BufferedImage image, int rgb) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == rgb) {
                    count++;
                }
            }
        }
        return count;
    }
}
