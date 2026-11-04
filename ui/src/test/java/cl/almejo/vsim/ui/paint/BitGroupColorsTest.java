package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The pin colours of the «Grupos» window: all different from each other and from «sin usar», none grey. */
class BitGroupColorsTest {

    private static final List<ColorScheme> SCHEMES = List.of(ColorSchemes.LIGHT, ColorSchemes.DARK,
            ColorSchemes.CLASSIC, ColorSchemes.PASTEL, ColorSchemes.CSIM, ColorSchemes.TWILIGHT, ColorSchemes.PLACA);

    private static double distance(Color first, Color second) {
        double red = first.getRed() - second.getRed();
        double green = first.getGreen() - second.getGreen();
        double blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    private static float saturation(Color color) {
        return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[1];
    }

    @Test
    void everyPinColourIsDistinctFromTheOthersAndFromUnused() {
        for (ColorScheme scheme : SCHEMES) {
            for (int first = 0; first < 8; first++) {
                assertThat(distance(scheme.bitGroupColor(first), scheme.bitGroupUnused()))
                        .as("pin %d vs unused, dark=%s", first, scheme.isDark()).isGreaterThan(60);
                for (int second = first + 1; second < 8; second++) {
                    assertThat(distance(scheme.bitGroupColor(first), scheme.bitGroupColor(second)))
                            .as("pins %d and %d, dark=%s", first, second, scheme.isDark()).isGreaterThan(55);
                }
            }
        }
    }

    @Test
    void noPinColourIsGrey() {
        for (ColorScheme scheme : SCHEMES) {
            for (int pin = 0; pin < 8; pin++) {
                assertThat(saturation(scheme.bitGroupColor(pin))).as("pin %d, dark=%s", pin, scheme.isDark())
                        .isGreaterThan(0.25f);
            }
        }
    }

    @Test
    void bitNumbersAreReadableOnEveryColour() {
        for (ColorScheme scheme : SCHEMES) {
            for (int pin = 0; pin < 8; pin++) {
                assertThat(ReadableColors.contrast(scheme.bitGroupText(), scheme.bitGroupColor(pin)))
                        .as("pin %d, dark=%s", pin, scheme.isDark()).isGreaterThanOrEqualTo(4.5);
            }
        }
    }
}
