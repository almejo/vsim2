package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LedPainterTest {

    private static final List<String> COLORS = List.of("red", "green", "yellow", "blue", "orange", "white", "purple",
            "cyan");
    /** Minimum RGB distance between lit and unlit, and between two lit colors. */
    private static final double DISTINCT = 80;

    private final ComponentType led = StandardComponentTypes.registry().require(StandardComponentTypes.LED);
    private final PainterRegistry painters = PainterRegistry.standard();

    /** The LED body center (local 8, 12) of a LED drawn at grid (12, 12) on {@code scheme}'s board. */
    private Color bodyColor(String colorName, Value value, ColorScheme scheme) {
        return pixelAt(colorName, value, scheme, 104, 108);
    }

    private Color pixelAt(String colorName, Value value, ColorScheme scheme, int pixelX, int pixelY) {
        Component component = led.newComponent(new ElementId("c1"), new GridPoint(12, 12), Rotation.R0,
                Map.of("color", colorName), "");
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 200, 200);
        ComponentDrawing.paint(graphics, led, component, new VisibleState.LedState(value), scheme, painters);
        graphics.dispose();
        return new Color(image.getRGB(pixelX, pixelY));
    }

    @Test
    void theColorParameterOffersEightColorsWithTheOldFourFirst() {
        ParameterSpec color = led.parameters().stream()
                .filter(parameter -> parameter.name().equals("color"))
                .findFirst().orElseThrow();
        assertThat(color.choices()).containsExactlyElementsOf(COLORS);
        assertThat(color.defaultValue()).isEqualTo("red");
    }

    @Test
    void litAndUnlitBodiesAreClearlyDifferentForEveryColorAndTheLitBodyIsPaintedAsConfigured() {
        // RGB distance >= 80 (of 441 max) is the same bar the lit colors must meet among themselves.
        for (String colorName : COLORS) {
            Color lit = bodyColor(colorName, Value.ONE, ColorSchemes.LIGHT);
            Color unlit = bodyColor(colorName, Value.ZERO, ColorSchemes.LIGHT);
            assertThat(lit).as(colorName).isEqualTo(LedPainter.colorFor(colorName));
            assertThat(unlit).as(colorName).isEqualTo(LedPainter.unlitColorFor(colorName));
            assertThat(distance(lit, unlit)).as("%s lit vs unlit", colorName).isGreaterThanOrEqualTo(DISTINCT);
        }
    }

    @Test
    void aLitLedStandsOutFromTheBoardOnEveryScheme() {
        // The body is opaque, so on a board close to its color (white on LIGHT, dark ones on DARK) what separates
        // it is the outline: scan the column through the top edge and require a pixel far from the background.
        for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK, ColorSchemes.CLASSIC)) {
            for (String colorName : COLORS) {
                for (Value value : List.of(Value.ONE, Value.ZERO)) {
                    double farthest = 0;
                    for (int pixelY = 94; pixelY <= 100; pixelY++) {
                        farthest = Math.max(farthest, distance(pixelAt(colorName, value, scheme, 104, pixelY),
                                scheme.background()));
                    }
                    assertThat(farthest).as("%s %s on %s", colorName, value, scheme).isGreaterThanOrEqualTo(DISTINCT);
                }
            }
        }
    }

    @Test
    void litColorsCanBeToldApart() {
        for (int first = 0; first < COLORS.size(); first++) {
            for (int second = first + 1; second < COLORS.size(); second++) {
                assertThat(distance(LedPainter.colorFor(COLORS.get(first)), LedPainter.colorFor(COLORS.get(second))))
                        .as("%s and %s", COLORS.get(first), COLORS.get(second))
                        .isGreaterThanOrEqualTo(DISTINCT);
            }
        }
    }

    @Test
    void oldColorsAreUnchangedAndUnknownNamesAreRed() {
        assertThat(LedPainter.colorFor("red")).isEqualTo(new Color(0xE53935));
        assertThat(LedPainter.colorFor("green")).isEqualTo(new Color(0x43A047));
        assertThat(LedPainter.colorFor("yellow")).isEqualTo(new Color(0xFDD835));
        assertThat(LedPainter.colorFor("blue")).isEqualTo(new Color(0x1E88E5));
        assertThat(LedPainter.colorFor("magenta")).isEqualTo(LedPainter.colorFor("red"));
        assertThat(LedPainter.colorFor(null)).isEqualTo(LedPainter.colorFor("red"));
        assertThat(LedPainter.unlitColorFor("magenta")).isEqualTo(LedPainter.unlitColorFor("red"));
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}