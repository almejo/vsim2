package cl.almejo.vsim.ui.panels;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** For tests: the pixels a colour swatch paints. */
final class SwatchPixels {

    private SwatchPixels() {
    }

    /** The colour near a corner of the swatch's square, inside it: its fill even when a mark is drawn in the middle. */
    static int inside(JToggleButton swatch) {
        return paint(swatch).getRGB(6, 6);
    }

    /** How many pixels of the swatch's icon are exactly {@code rgb}. */
    static int count(JToggleButton swatch, int rgb) {
        BufferedImage image = paint(swatch);
        int found = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                if (image.getRGB(column, row) == rgb) {
                    found++;
                }
            }
        }
        return found;
    }

    private static BufferedImage paint(JToggleButton swatch) {
        Icon icon = swatch.getIcon();
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(swatch, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** The colour at the middle of the swatch, which is its fill. */
    static int centre(JToggleButton swatch) {
        Icon icon = swatch.getIcon();
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(swatch, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2);
    }
}
