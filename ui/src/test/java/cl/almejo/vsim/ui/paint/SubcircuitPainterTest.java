package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubcircuitPainterTest {

    private static ComponentType box(String inputName, String outputName) {
        return new ComponentType("subcircuit:box", ComponentCategory.CUSTOM, new GridSize(6, 4),
                List.of(PinSpec.input(inputName, 0, 2), PinSpec.output(outputName, 6, 2)), List.of(),
                parameters -> (context, pinIndex) -> {
                });
    }

    private static BufferedImage render(ComponentType type) {
        BufferedImage image = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT.background());
        graphics.fillRect(0, 0, 48, 32);
        new SubcircuitPainter().paint(graphics, type, Map.of(), VisibleState.NONE, ColorScheme.LIGHT);
        graphics.dispose();
        return image;
    }

    private static int differences(BufferedImage first, BufferedImage second, int fromX, int toX) {
        int differing = 0;
        for (int x = fromX; x < toX; x++) {
            for (int y = 13; y < 20; y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        return differing;
    }

    @Test
    void pinNamesAreWrittenInsideTheBoxBesideTheirPins() {
        BufferedImage named = render(box("entrada", "salida"));
        BufferedImage unnamed = render(box("", ""));

        assertThat(differences(named, unnamed, 3, 22)).as("left half").isGreaterThan(5);
        assertThat(differences(named, unnamed, 26, 45)).as("right half").isGreaterThan(5);
    }
}
