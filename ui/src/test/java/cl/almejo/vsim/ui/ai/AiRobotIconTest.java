package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.window.tabs.TabMark;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The robot of the AI's mark: green while working, grey after, in the colours of the theme painted now. */
class AiRobotIconTest {

    private final LookAndFeel previous = UIManager.getLookAndFeel();

    @AfterEach
    void restoreTheTheme() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    /** The opaque colours the icon painted (antialiased edges left out). */
    private static Set<Integer> solidColours(AiRobotIcon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        Set<Integer> colours = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0xFF) {
                    colours.add(argb & 0xFFFFFF);
                }
            }
        }
        return colours;
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }

    @Test
    void theWorkingRobotIsTheAiGreenAndTheFinishedOneGreyInTheThemeOfTheMomentOfPainting() throws Exception {
        AiRobotIcon working = new AiRobotIcon(TabMark.Phase.WORKING, 16);
        AiRobotIcon finished = new AiRobotIcon(TabMark.Phase.FINISHED, 16);

        UIManager.setLookAndFeel(new FlatLightLaf());
        assertThat(solidColours(working)).containsOnly(rgb(AiColors.success()));
        assertThat(solidColours(finished)).containsOnly(rgb(AiColors.muted()));
        int lightGreen = rgb(AiColors.success());

        UIManager.setLookAndFeel(new FlatDarkLaf());
        assertThat(solidColours(working)).as("the same icon, repainted after the theme changed")
                .containsOnly(rgb(AiColors.success())).doesNotContain(lightGreen);
    }
}
