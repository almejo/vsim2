package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConstantPainterTest {

    private static final List<String> FORMATS = List.of("auto", "binary", "hex", "decimal", "signed");
    private static final List<Integer> WIDTHS = List.of(1, 4, 8, 16, 32);

    private final ComponentType constant = StandardComponentTypes.registry().require(StandardComponentTypes.CONSTANT);
    private final PainterRegistry painters = PainterRegistry.standard();

    private void render(int width, String value, String format, ColorScheme scheme) {
        Map<String, Object> parameters = Map.of("width", width, "value", value, "format", format);
        Component component = constant.newComponent(new ElementId("c1"), new GridPoint(2, 2), Rotation.R0, parameters,
                "");
        BufferedImage image = new BufferedImage(400, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 400, 80);
        ComponentDrawing.paint(graphics, constant.specializedFor(parameters), component, VisibleState.NONE, scheme,
                painters);
        graphics.dispose();
    }

    @Test
    void itDrawsAtEveryWidthAndFormatInBothSchemes() {
        for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorSchemes.DARK)) {
            for (int width : WIDTHS) {
                for (String format : FORMATS) {
                    assertThatCode(() -> render(width, "1", format, scheme)).doesNotThrowAnyException();
                }
            }
        }
    }

    @Test
    void theBoxShowsTheValueInTheChosenFormat() {
        assertThat(ConstantPainter.shownText(constant.resolveParameters(
                Map.of("width", 4, "value", "0x5", "format", "auto")))).isEqualTo("0101");
        assertThat(ConstantPainter.shownText(constant.resolveParameters(
                Map.of("width", 8, "value", "255", "format", "auto")))).isEqualTo("0xFF");
        assertThat(ConstantPainter.shownText(constant.resolveParameters(
                Map.of("width", 8, "value", "0xFF", "format", "decimal")))).isEqualTo("255");
        assertThat(ConstantPainter.shownText(constant.resolveParameters(
                Map.of("width", 8, "value", "0xFF", "format", "signed")))).isEqualTo("-1");
        assertThat(ConstantPainter.shownText(constant.resolveParameters(
                Map.of("width", 8, "value", "-3", "format", "hex")))).isEqualTo("0xFD");
    }

    @Test
    void theTextFitsTheBoxAtEveryWidthAndFormat() {
        for (int width = 1; width <= ConstantValue.MAXIMUM_WIDTH; width++) {
            for (String format : FORMATS) {
                assertThat(ConstantPainter.widestTextWidth(width, format) + 2 * ConstantPainter.TEXT_PADDING)
                        .as("width %d, %s", width, format).isLessThanOrEqualTo(boxWidthOf(width, format));
            }
        }
    }

    @Test
    void theBoxIsNotWastefullyWide() {
        for (int width = 1; width <= ConstantValue.MAXIMUM_WIDTH; width++) {
            for (String format : FORMATS) {
                double needed = ConstantPainter.widestTextWidth(width, format) + 2 * ConstantPainter.TEXT_PADDING;
                assertThat(boxWidthOf(width, format)).as("width %d, %s", width, format)
                        .isLessThanOrEqualTo(Math.max(4 * 8 - ConstantPainter.LEAD, needed * 1.1 + 16));
            }
        }
    }

    private double boxWidthOf(int width, String format) {
        return constant.specializedFor(Map.of("width", width, "format", format)).size().width() * 8.0
                - ConstantPainter.LEAD;
    }
}
