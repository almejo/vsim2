package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine's small texts and links read at {@link ReadableColors#COMFORTABLE_TEXT_CONTRAST}:1 in every scheme and
 * theme, clear of the 4.5:1 edge that anti-aliased text falls under (final hand test of hito 4).
 */
class MachineReadableColoursTest {

    private static final double MINIMUM = ReadableColors.COMFORTABLE_TEXT_CONTRAST;

    @Test
    void theArrowLabelsAndTheInitialMarkReadOnTheDiagramInEveryScheme() {
        for (ColorScheme scheme : schemes()) {
            assertThat(ReadableColors.contrast(MachineColors.muted(scheme), scheme.background()))
                    .as("muted text in %s", scheme.background()).isGreaterThanOrEqualTo(MINIMUM);
        }
    }

    @Test
    void theUnlitChipTextReadsInEveryScheme() {
        for (ColorScheme scheme : schemes()) {
            assertThat(ReadableColors.contrast(MachineKindChips.unlitText(scheme), scheme.background()))
                    .as("unlit chip in %s", scheme.background()).isGreaterThanOrEqualTo(MINIMUM);
            assertThat(MachineKindChips.unlitText(scheme)).as("still looks off in %s", scheme.background())
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

    /** The default light scheme and every named one. */
    private static List<ColorScheme> schemes() {
        List<ColorScheme> schemes = new ArrayList<>();
        schemes.add(ColorScheme.LIGHT);
        for (String id : ColorSchemes.ids()) {
            schemes.add(ColorSchemes.byId(id).orElseThrow());
        }
        return schemes;
    }

    @Test
    void readableOnMovesAColourOnlyAsFarAsNeeded() {
        Color background = new Color(70, 73, 75);
        Color faint = new Color(0x909090);
        Color fixed = ReadableColors.readableOn(faint, background);
        assertThat(ReadableColors.contrast(fixed, background))
                .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
        Color fine = new Color(0xF0F0F0);
        assertThat(ReadableColors.readableOn(fine, background)).isEqualTo(fine);
        Color comfortable = ReadableColors.readableOn(faint, background, MINIMUM);
        assertThat(ReadableColors.contrast(comfortable, background)).isGreaterThanOrEqualTo(MINIMUM)
                .isLessThan(MINIMUM + 0.3);
    }
}
