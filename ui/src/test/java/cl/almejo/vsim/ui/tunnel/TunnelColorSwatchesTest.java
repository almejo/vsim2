package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelColorSwatchesTest {

    @Test
    void theChosenSwatchHasAMarkedRingAndTheOthersDoNot() {
        TunnelColorSwatches swatches = new TunnelColorSwatches(2);

        Color ring = TunnelColorSwatches.selectionRingColor();
        assertThat(ringPixel(swatches.swatch(2))).isEqualTo(ring.getRGB());
        assertThat(ringPixel(swatches.swatch(3))).isNotEqualTo(ring.getRGB());
    }

    @Test
    void theRingFollowsTheChoice() {
        TunnelColorSwatches swatches = new TunnelColorSwatches(0);

        swatches.swatch(5).doClick();

        Color ring = TunnelColorSwatches.selectionRingColor();
        assertThat(ringPixel(swatches.swatch(5))).isEqualTo(ring.getRGB());
        assertThat(ringPixel(swatches.swatch(0))).isNotEqualTo(ring.getRGB());
    }

    @Test
    void theFirstColourIsCalledRedAndThePinkOneComesSeventh() {
        TunnelColorSwatches swatches = new TunnelColorSwatches(0);

        assertThat(swatches.swatch(0).getToolTipText()).isEqualTo(Messages.text("tunnel.color.red"));
        assertThat(swatches.swatch(6).getToolTipText()).isEqualTo(Messages.text("tunnel.color.pink"));
    }

    /** The colour on the middle of the ring's left side, where the swatch itself never paints. */
    private static int ringPixel(JToggleButton swatch) {
        Icon icon = swatch.getIcon();
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(swatch, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(1, icon.getIconHeight() / 2);
    }
}
