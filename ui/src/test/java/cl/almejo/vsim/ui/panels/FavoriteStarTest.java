package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.theme.ReadableColors;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteStarTest {

    @Test
    void theOutlineIsTheDisabledTextAndTheFilledStarAWarmColourThatReadsInLightAndDark() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel lookAndFeel : List.of(new FlatLightLaf(), new FlatDarkLaf())) {
                UIManager.setLookAndFeel(lookAndFeel);
                Color background = UIManager.getColor("Tree.background");
                Color filled = FavoriteStar.color(true, false);

                assertThat(FavoriteStar.color(false, false)).as(lookAndFeel.getName())
                        .isEqualTo(UIManager.getColor("Label.disabledForeground"));
                assertThat(filled.getRed()).as("warm").isGreaterThan(filled.getBlue() + 100);
                assertThat(ReadableColors.contrast(filled, background)).as(lookAndFeel.getName())
                        .isGreaterThanOrEqualTo(FavoriteStar.MINIMUM_CONTRAST);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void onASelectedRowTheStarTakesTheSelectionText() {
        Color selectionText = new Color(0x123456);
        Object previous = UIManager.get("Tree.selectionForeground");
        UIManager.put("Tree.selectionForeground", selectionText);
        try {
            assertThat(FavoriteStar.color(true, true)).isEqualTo(selectionText);
            assertThat(FavoriteStar.color(false, true)).isEqualTo(selectionText);
        } finally {
            UIManager.put("Tree.selectionForeground", previous);
        }
    }
}
