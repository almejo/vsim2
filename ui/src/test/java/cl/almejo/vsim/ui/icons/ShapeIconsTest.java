package cl.almejo.vsim.ui.icons;

import cl.almejo.vsim.ui.appearance.ShapeEditorIcons;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeIconsTest {

    static final List<String> NAMES = ShapeEditorIcons.names();

    private final javax.swing.LookAndFeel originalLookAndFeel = UIManager.getLookAndFeel();

    @AfterEach
    void restoreTheLookAndFeel() throws Exception {
        UIManager.setLookAndFeel(originalLookAndFeel);
    }

    private static BufferedImage render(String name) {
        Icon icon = Icons.get(name, Icons.TOOLBAR_SIZE);
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    private static long brightnessOfPainted(BufferedImage image) {
        long sum = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) > 200) {
                    sum += ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
                }
            }
        }
        return sum;
    }

    @Test
    void everyShapeIconLoadsAndDrawsInBothThemes() throws Exception {
        for (boolean dark : new boolean[]{false, true}) {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            assertThat(FlatLaf.isLafDark()).isEqualTo(dark);
            for (String name : NAMES) {
                assertThat(brightnessOfPainted(render(name))).as(name + (dark ? " dark" : " light")).isPositive();
            }
        }
    }

    @Test
    void theGreyStrokesAreRecoloredInTheDarkTheme() throws Exception {
        UIManager.setLookAndFeel(new FlatLightLaf());
        long light = brightnessOfPainted(render("shape-rectangle"));
        UIManager.setLookAndFeel(new FlatDarkLaf());
        long dark = brightnessOfPainted(render("shape-rectangle"));
        assertThat(dark).isNotEqualTo(light);
    }

    @Test
    void nodesAndPolygonLookDifferent() {
        BufferedImage nodes = render("shape-nodes");
        BufferedImage polygon = render("shape-polygon");
        int different = 0;
        for (int x = 0; x < nodes.getWidth(); x++) {
            for (int y = 0; y < nodes.getHeight(); y++) {
                if (nodes.getRGB(x, y) != polygon.getRGB(x, y)) {
                    different++;
                }
            }
        }
        assertThat(different).isGreaterThan(40);
    }
}
