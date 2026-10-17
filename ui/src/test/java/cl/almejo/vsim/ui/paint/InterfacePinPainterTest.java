package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** M7: input and output pins of more than one bit write their value, in their «Mostrar como», in a value box. */
class InterfacePinPainterTest {

    private static final int IMAGE_SIZE = 480;
    private static final int MARGIN = 2;
    private static final List<String> FORMATS = List.of("auto", "binary", "hex", "decimal", "signed");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final PainterRegistry painters = PainterRegistry.standard();

    private Component pin(String typeId, Rotation rotation, Map<String, Object> parameters) {
        return registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(30, 30), rotation,
                parameters, "");
    }

    private static VisibleState stateOf(String typeId, Value value) {
        return typeId.equals(StandardComponentTypes.INPUT_PIN)
                ? new VisibleState.SwitchState(value) : new VisibleState.LedState(value);
    }

    private BufferedImage draw(Component component, VisibleState state) {
        BufferedImage image = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, registry.typeOf(component), component, state, PaintersTest.SCHEME, painters);
        graphics.dispose();
        return image;
    }

    @Test
    void theValueTextFollowsTheFormat() {
        assertThat(InterfacePinPainter.shownText(Value.of(8, 0xFF), "auto")).isEqualTo("0xFF");
        assertThat(InterfacePinPainter.shownText(Value.of(8, 0xFF), "hex")).isEqualTo("0xFF");
        assertThat(InterfacePinPainter.shownText(Value.of(8, 0xFF), "decimal")).isEqualTo("255");
        assertThat(InterfacePinPainter.shownText(Value.of(8, 0xFF), "signed")).isEqualTo("-1");
        assertThat(InterfacePinPainter.shownText(Value.of(8, 0xFF), "binary")).isEqualTo("11111111");
        assertThat(InterfacePinPainter.shownText(Value.of(4, 5), "auto")).isEqualTo("0101");
        assertThat(InterfacePinPainter.shownText(Value.of(32, 0x80000000L), "signed")).isEqualTo("-2147483648");
    }

    @Test
    void anUndefinedValueShowsItsHighImpedanceBits() {
        assertThat(InterfacePinPainter.shownText(Value.highImpedance(8), "decimal")).isEqualTo("Z");
        assertThat(InterfacePinPainter.shownText(Value.highImpedance(8), "hex")).isEqualTo("0xZZ");
        Value lowNibbleOnly = new Value(8, 0x5, 0xF0, false);
        assertThat(InterfacePinPainter.shownText(lowNibbleOnly, "hex")).isEqualTo("0xZ5");
        assertThat(InterfacePinPainter.shownText(lowNibbleOnly, "binary")).isEqualTo("ZZZZ0101");
        assertThat(InterfacePinPainter.shownText(lowNibbleOnly, "signed")).isEqualTo("?");
        assertThat(InterfacePinPainter.shownText(Value.of(8, 3).withConflict(), "hex")).isEqualTo("?");
        assertThat(InterfacePinPainter.shownText(null, "hex")).isEmpty();
    }

    @Test
    void theValueBoxIsOnTheFreeSideOfTheTagInEveryRotation() {
        for (String typeId : List.of(StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN)) {
            boolean input = typeId.equals(StandardComponentTypes.INPUT_PIN);
            for (Rotation rotation : Rotation.values()) {
                Component component = pin(typeId, rotation, Map.of("width", 8));
                ComponentType type = registry.typeOf(component);
                Rectangle2D body = ComponentDrawing.documentBounds(type, component);
                Rectangle2D box = InterfacePinPainter.valueBox(type, component).orElseThrow();
                String context = typeId + " " + rotation;
                // the input pin's tag points away from its free side, the output pin's tag towards it
                int freeSideQuarterTurns = (rotation.quarterTurns() + (input ? 2 : 0)) % 4;
                switch (freeSideQuarterTurns) {
                    case 0 -> assertThat(box.getMinX()).as(context).isGreaterThanOrEqualTo(body.getMaxX());
                    case 1 -> assertThat(box.getMinY()).as(context).isGreaterThanOrEqualTo(body.getMaxY());
                    case 2 -> assertThat(box.getMaxX()).as(context).isLessThanOrEqualTo(body.getMinX());
                    default -> assertThat(box.getMaxY()).as(context).isLessThanOrEqualTo(body.getMinY());
                }
                // the box is upright: wider than tall
                assertThat(box.getWidth()).as(context).isGreaterThan(box.getHeight());
            }
        }
    }

    @Test
    void aOneBitPinHasNoValueBox() {
        Component component = pin(StandardComponentTypes.INPUT_PIN, Rotation.R0, Map.of());
        assertThat(InterfacePinPainter.valueBox(registry.typeOf(component), component)).isEmpty();
    }

    @Test
    void busPinsDrawOnlyInsideTheirBoundsOrTheirValueBox() {
        for (String typeId : List.of(StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN)) {
            for (Rotation rotation : Rotation.values()) {
                for (String format : FORMATS) {
                    for (int width : new int[]{2, 8, 32, 64}) {
                        Component component = pin(typeId, rotation, Map.of("width", width, "format", format));
                        ComponentType type = registry.typeOf(component);
                        BufferedImage image = draw(component,
                                stateOf(typeId, Value.of(width, Value.maskFor(width))));
                        Rectangle2D body = grown(ComponentDrawing.documentBounds(type, component), 8);
                        Rectangle2D box = grown(InterfacePinPainter.valueBox(type, component).orElseThrow(), MARGIN);
                        int background = PaintersTest.SCHEME.background().getRGB();
                        int insideTheBox = 0;
                        for (int x = 0; x < IMAGE_SIZE; x++) {
                            for (int y = 0; y < IMAGE_SIZE; y++) {
                                if (image.getRGB(x, y) == background) {
                                    continue;
                                }
                                assertThat(body.contains(x, y) || box.contains(x, y))
                                        .as("%s %s %s %d pixel %d,%d", typeId, rotation, format, width, x, y)
                                        .isTrue();
                                if (box.contains(x, y) && !body.contains(x, y)) {
                                    insideTheBox++;
                                }
                            }
                        }
                        assertThat(insideTheBox).as("%s %s %s %d", typeId, rotation, format, width)
                                .isGreaterThan(20);
                    }
                }
            }
        }
    }

    @Test
    void theDrawnValueChangesWithTheFormatAndTheValue() {
        for (String typeId : List.of(StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN)) {
            BufferedImage hex = draw(pin(typeId, Rotation.R0, Map.of("width", 8, "format", "hex")),
                    stateOf(typeId, Value.of(8, 0xFF)));
            BufferedImage signed = draw(pin(typeId, Rotation.R0, Map.of("width", 8, "format", "signed")),
                    stateOf(typeId, Value.of(8, 0xFF)));
            BufferedImage otherValue = draw(pin(typeId, Rotation.R0, Map.of("width", 8, "format", "hex")),
                    stateOf(typeId, Value.of(8, 0x12)));
            assertThat(differences(hex, signed)).as(typeId).isGreaterThan(10);
            assertThat(differences(hex, otherValue)).as(typeId).isGreaterThan(10);
        }
    }

    private static Rectangle2D grown(Rectangle2D rectangle, double margin) {
        return new Rectangle2D.Double(rectangle.getX() - margin, rectangle.getY() - margin,
                rectangle.getWidth() + 2 * margin, rectangle.getHeight() + 2 * margin);
    }

    private static int differences(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
