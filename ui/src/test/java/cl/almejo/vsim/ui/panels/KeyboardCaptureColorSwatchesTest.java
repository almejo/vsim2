package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.KeyboardPainter;
import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Teclado's «Color al escribir» is picked from swatches that show each choice's colour in the scheme. */
class KeyboardCaptureColorSwatchesTest {

    private static ParameterForm form(Map<String, Object> current, ColorScheme scheme) {
        return new ParameterForm(StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD), current,
                () -> scheme);
    }

    @SuppressWarnings("unchecked")
    private static ColorSwatches<String> swatches(ParameterForm form) {
        return (ColorSwatches<String>) form.editor(KeyboardTypes.CAPTURE_COLOR);
    }

    @Test
    void theEightChoicesAreSwatchesNamedByTheirBundleNames() {
        ColorSwatches<String> colors = swatches(form(Map.of(), ColorScheme.LIGHT));

        assertThat(colors.values()).containsExactlyElementsOf(KeyboardTypes.CAPTURE_COLOR_CHOICES);
        for (int index = 0; index < colors.values().size(); index++) {
            assertThat(colors.swatch(index).getToolTipText())
                    .isEqualTo(Messages.choiceName(KeyboardTypes.CAPTURE_COLOR, colors.values().get(index)));
        }
        assertThat(colors.selectedValue()).isEqualTo("automatic");
    }

    @Test
    void eachSwatchShowsItsColourForTheSchemeAndAutomaticTheSelectionColour() {
        for (ColorScheme scheme : new ColorScheme[] {ColorScheme.LIGHT, ColorSchemes.DARK}) {
            ColorSwatches<String> colors = swatches(form(Map.of(), scheme));
            for (int index = 1; index < colors.values().size(); index++) {
                assertThat(SwatchPixels.inside(colors.swatch(index)))
                        .as(colors.values().get(index))
                        .isEqualTo(KeyboardPainter.captureColor(scheme, colors.values().get(index)).getRGB());
            }
            assertThat(SwatchPixels.inside(colors.swatch(0))).isEqualTo(scheme.selection().getRGB());
        }
    }

    @Test
    void onlyTheAutomaticSwatchCarriesAnAMark() {
        ColorSwatches<String> colors = swatches(form(Map.of(), ColorScheme.LIGHT));
        int markColour = KeyboardPainter.textOn(ColorScheme.LIGHT.selection()).getRGB();

        assertThat(SwatchPixels.count(colors.swatch(0), markColour)).isGreaterThan(0);
        for (int index = 1; index < colors.values().size(); index++) {
            JToggleButton swatch = colors.swatch(index);
            assertThat(SwatchPixels.count(swatch, KeyboardPainter.textOn(
                    KeyboardPainter.captureColor(ColorScheme.LIGHT, colors.values().get(index))).getRGB()))
                    .as(colors.values().get(index)).isZero();
        }
    }

    @Test
    void pickingASwatchIsOneEditAndTheValueStaysAString() {
        ParameterForm form = form(Map.of(KeyboardTypes.CAPTURE_COLOR, "green"), ColorScheme.LIGHT);
        ColorSwatches<String> colors = swatches(form);
        assertThat(colors.selectedValue()).isEqualTo("green");
        int[] commits = {0};
        form.addCommitListener(() -> commits[0]++);

        colors.swatch(colors.values().indexOf("pink")).doClick();

        assertThat(form.rawValues()).containsEntry(KeyboardTypes.CAPTURE_COLOR, "pink");
        assertThat(commits[0]).isEqualTo(1);
    }
}
