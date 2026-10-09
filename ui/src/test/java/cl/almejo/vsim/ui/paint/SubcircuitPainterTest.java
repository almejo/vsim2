package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** A 2-in/1-out box (6x4): inputs 2 units apart, as M4's rotated sub-circuit case. */
    private static ComponentType twoInputs(String id, String firstName, String secondName) {
        return new ComponentType(id, ComponentCategory.CUSTOM, new GridSize(6, 4),
                List.of(PinSpec.input(firstName, 0, 1), PinSpec.input(secondName, 0, 3), PinSpec.output("", 6, 2)),
                List.of(), parameters -> (context, pinIndex) -> {
                });
    }

    /** Rendered turned a quarter (R90): the 48x32 box becomes 32 wide and 48 tall, at x 48..80, y 20..68. */
    private static BufferedImage renderTurned(ComponentType type) {
        BufferedImage image = new BufferedImage(128, 96, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT.background());
        graphics.fillRect(0, 0, 128, 96);
        graphics.translate(80, 20);
        graphics.rotate(Math.PI / 2);
        new SubcircuitPainter().paint(graphics, type, Map.of(), VisibleState.NONE, ColorScheme.LIGHT);
        graphics.dispose();
        return image;
    }

    private static Set<Point> changed(BufferedImage first, BufferedImage second) {
        Set<Point> points = new HashSet<>();
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    @Test
    void turnedAQuarterTheNameStaysInsideTheBox() {
        BufferedImage shortName = renderTurned(twoInputs("subcircuit:x", "", ""));
        BufferedImage longName = renderTurned(twoInputs("subcircuit:sumador-completo-de-cuatro-bits", "", ""));
        Rectangle box = new Rectangle(48, 20, 32, 48);

        Set<Point> drawn = changed(shortName, longName);

        assertThat(drawn).as("the long name is drawn").isNotEmpty();
        assertThat(drawn).as("nothing outside the box").allMatch(box::contains);
    }

    @Test
    void turnedAQuarterThePinNamesOfNeighbouringPinsDoNotOverlap() {
        BufferedImage none = renderTurned(twoInputs("subcircuit:x", "", ""));
        Set<Point> first = changed(none, renderTurned(twoInputs("subcircuit:x", "entrada", "")));
        Set<Point> second = changed(none, renderTurned(twoInputs("subcircuit:x", "", "entrada")));

        assertThat(first).isNotEmpty();
        assertThat(second).isNotEmpty();
        Set<Point> shared = new HashSet<>(first);
        shared.retainAll(second);
        assertThat(shared).isEmpty();
    }
}
