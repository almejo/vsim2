package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/** Hito 4, fix round 4 (N1): the machine's small texts and links read at 4.5:1 in every scheme and theme. */
class MachineReadableColoursTest {

    private static final double MINIMUM = ReadableColors.MINIMUM_TEXT_CONTRAST;

    @Test
    void theArrowLabelsAndTheInitialMarkReadOnTheDiagramInEveryScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            assertThat(ReadableColors.contrast(MachineColors.muted(scheme), scheme.background()))
                    .as("muted text in %s", id).isGreaterThanOrEqualTo(MINIMUM);
        }
    }

    @Test
    void theUnlitChipTextReadsInEveryScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            assertThat(ReadableColors.contrast(MachineKindChips.unlitText(scheme), scheme.background()))
                    .as("unlit chip in %s", id).isGreaterThanOrEqualTo(MINIMUM);
            assertThat(MachineKindChips.unlitText(scheme)).as("still looks off in %s", id)
                    .isNotEqualTo(scheme.text());
        }
    }

    @Test
    void theLinkReadsOnThePanelInEveryTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                assertThat(ReadableColors.contrast(new MachineLinkButton("Ver").getForeground(),
                        ReadableColors.panelBackground())).as(theme.getName()).isGreaterThanOrEqualTo(MINIMUM);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void readableOnMovesAColourOnlyAsFarAsNeeded() {
        Color background = new Color(70, 73, 75);
        Color faint = new Color(0x909090);
        Color fixed = ReadableColors.readableOn(faint, background);
        assertThat(ReadableColors.contrast(fixed, background)).isGreaterThanOrEqualTo(MINIMUM);
        Color fine = new Color(0xF0F0F0);
        assertThat(ReadableColors.readableOn(fine, background)).isEqualTo(fine);
    }
}
