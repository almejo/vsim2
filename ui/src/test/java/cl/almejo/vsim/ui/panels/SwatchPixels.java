package cl.almejo.vsim.ui.panels;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** For tests: the pixels a colour swatch paints. */
final class SwatchPixels {

    private SwatchPixels() {
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
