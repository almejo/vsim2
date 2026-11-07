package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeColorsTest {

    @Test
    void aKeyTheThemeDoesNotHaveGivesTheFallback() {
        assertThat(ThemeColors.ui("No.such.key", Color.MAGENTA)).isEqualTo(Color.MAGENTA);
    }

    @Test
    void theAccentIsTheThemesAccentColour() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            assertThat(ThemeColors.accent()).isEqualTo(UIManager.getColor("Component.accentColor"));
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void aRuleIsPaintedInTheBorderColour() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            try {
                ThemeColors.paintRule(graphics, new javax.swing.JLabel(), 0, 3, 4, 1);
            } finally {
                graphics.dispose();
            }
            assertThat(new Color(image.getRGB(2, 3), true)).isEqualTo(UIManager.getColor("Component.borderColor"));
            assertThat(image.getRGB(2, 0)).isZero();
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }
}
