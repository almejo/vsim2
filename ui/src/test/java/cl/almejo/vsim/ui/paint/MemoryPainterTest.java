package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryKind;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPainterTest {

    private static final Color READ = new Color(0x00CC11);
    private static final Color WRITE = new Color(0xCC00AA);
    private static final Color WARNING = new Color(0x11DDEE);
    private static final int MARGIN = 8;
    private static final int IMAGE_SIZE = 700;
    private static final GridPoint ORIGIN = new GridPoint(10, 10);

    /** A scheme whose two activity colours and warning colour are found nowhere else on the drawing. */
    private static final ColorScheme SCHEME = ColorScheme.LIGHT.with(ColorRole.MEMORY_READ, READ)
            .with(ColorRole.MEMORY_WRITE, WRITE).with(ColorRole.WIRE_CONFLICT, WARNING);

    private static final VisibleState READING = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.READ, 2,
            0, List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
    private static final VisibleState WRITING = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.WRITE,
            2, 0, List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
    private static final VisibleState DISABLED = new VisibleState.MemoryState(false, VisibleState.MemoryAccess.READ,
            2, 0, List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));

    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage draw(String typeId, Rotation rotation, Map<String, Object> parameters, VisibleState state,
                               ColorScheme scheme) {
        ComponentType type = StandardComponentTypes.registry().require(typeId);
        Component component = type.newComponent(new ElementId("m1"), ORIGIN, rotation, parameters, "");
        BufferedImage image = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type.specializedFor(parameters), component, state, scheme, painters);
        graphics.dispose();
        return image;
    }

    private static int countPixelsOf(BufferedImage image, Color color) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDifferences(BufferedImage first, BufferedImage second) {
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

    /** Pixels within {@code tolerance} of {@code color} on every channel (a thin dashed line is never fully covered). */
    private static int countPixelsNear(BufferedImage image, Color color, int tolerance) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (Math.abs(pixel.getRed() - color.getRed()) <= tolerance
                        && Math.abs(pixel.getGreen() - color.getGreen()) <= tolerance
                        && Math.abs(pixel.getBlue() - color.getBlue()) <= tolerance) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void ramAndRomAreDrawnByTheMemoryPainter() {
        assertThat(painters.painterFor(StandardComponentTypes.RAM)).isInstanceOf(MemoryPainter.class);
        assertThat(painters.painterFor(StandardComponentTypes.ROM)).isInstanceOf(MemoryPainter.class);
    }

    @Test
    void everyVariantDrawsInsideItsBoundsInEveryRotation() {
        List<Map<String, Object>> variants = List.of(
                Map.of(),
                Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO),
                Map.of(MemoryTypes.CONNECTION, MemoryTypes.CONNECTION_PINS),
                Map.of(MemoryTypes.CONNECTION, MemoryTypes.CONNECTION_PINS, MemoryTypes.SHOW_CONTENTS,
                        MemoryTypes.SHOW_CONTENTS_NO),
                Map.of(MemoryTypes.WIDTH, 64, MemoryTypes.WORDS, "16777216"),
                Map.of(MemoryTypes.WIDTH, 1, MemoryTypes.WORDS, "2"),
                Map.of(MemoryTypes.DATA, MemoryTypes.DATA_SHARED),
                Map.of(MemoryTypes.WRITE, MemoryTypes.WRITE_LEVEL));
        for (String typeId : List.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM)) {
            for (Map<String, Object> variant : variants) {
                Map<String, Object> parameters = new HashMap<>(variant);
                if (typeId.equals(StandardComponentTypes.ROM)) {
                    parameters.remove(MemoryTypes.DATA);
                    parameters.remove(MemoryTypes.WRITE);
                }
                for (Rotation rotation : Rotation.values()) {
                    for (VisibleState state : List.of(VisibleState.NONE, READING, WRITING, DISABLED)) {
                        assertDrawsInsideBounds(typeId, rotation, parameters, state);
                    }
                }
            }
        }
    }

    private void assertDrawsInsideBounds(String typeId, Rotation rotation, Map<String, Object> parameters,
                                         VisibleState state) {
        ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
        Component component = type.newComponent(new ElementId("m1"), ORIGIN, rotation, parameters, "");
        BufferedImage image = draw(typeId, rotation, parameters, state, SCHEME);
        Rectangle2D allowed = ComponentDrawing.documentBounds(type, component);
        allowed.setRect(allowed.getX() - MARGIN, allowed.getY() - MARGIN, allowed.getWidth() + 2 * MARGIN,
                allowed.getHeight() + 2 * MARGIN);
        int background = SCHEME.background().getRGB();
        int drawn = 0;
        String description = typeId + " " + rotation + " " + parameters + " " + state;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) != background) {
                    drawn++;
                    assertThat(allowed.contains(x, y)).as(description + " pixel " + x + "," + y).isTrue();
                }
            }
        }
        assertThat(drawn).as(description).isGreaterThan(50);
    }

    @Test
    void thePaletteIconAndTheGhostHaveNoStateAndStillDrawTheBlock() {
        for (String typeId : List.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM)) {
            BufferedImage image = draw(typeId, Rotation.R0, Map.of(), VisibleState.NONE, SCHEME);
            assertThat(countPixelsOf(image, SCHEME.background())).isLessThan(IMAGE_SIZE * IMAGE_SIZE);
            assertThat(countPixelsOf(image, READ)).as(typeId + " light off").isZero();
            assertThat(countPixelsOf(image, WRITE)).as(typeId + " light off").isZero();
        }
    }

    @Test
    void aReadPaintsTheReadColourAndAWritePaintsTheWriteColour() {
        BufferedImage reading = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), READING, SCHEME);
        BufferedImage writing = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), WRITING, SCHEME);

        assertThat(countPixelsOf(reading, READ)).isGreaterThan(0);
        assertThat(countPixelsOf(reading, WRITE)).isZero();
        assertThat(countPixelsOf(writing, WRITE)).isGreaterThan(0);
        assertThat(countPixelsOf(writing, READ)).isZero();
    }

    @Test
    void aFoldedMemoryStillShowsItsLight() {
        Map<String, Object> folded = Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO);
        BufferedImage reading = draw(StandardComponentTypes.RAM, Rotation.R0, folded, READING, SCHEME);
        BufferedImage shown = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), READING, SCHEME);

        assertThat(countPixelsOf(reading, READ)).isGreaterThan(0);
        assertThat(countPixelsOf(shown, READ)).isGreaterThan(0);
        assertThat(countDifferences(reading, shown)).as("the window is not drawn when folded").isGreaterThan(200);
    }

    @Test
    void anIdleOrDeselectedMemoryShowsNoActivityColour() {
        BufferedImage idle = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(),
                new VisibleState.MemoryState(true, VisibleState.MemoryAccess.NONE, -1, 0, List.of(0L, 0L, 0L, 0L, 0L)),
                SCHEME);
        BufferedImage deselected = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), DISABLED, SCHEME);

        for (BufferedImage image : List.of(idle, deselected)) {
            assertThat(countPixelsOf(image, READ)).isZero();
            assertThat(countPixelsOf(image, WRITE)).isZero();
        }
    }

    @Test
    void theBlackAndWhitePrintSchemeHasNoActivityColour() {
        ColorScheme print = MonochromeScheme.of(SCHEME);
        assertThat(print.memoryRead()).isEqualTo(print.componentStroke());
        assertThat(print.memoryWrite()).isEqualTo(print.componentStroke());
        for (VisibleState state : List.of(READING, WRITING)) {
            BufferedImage image = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), state, print);
            assertThat(countPixelsOf(image, READ)).isZero();
            assertThat(countPixelsOf(image, WRITE)).isZero();
            assertThat(countPixelsOf(image, ColorScheme.LIGHT.memoryRead())).isZero();
            assertThat(countPixelsOf(image, ColorScheme.LIGHT.memoryWrite())).isZero();
        }
    }

    @Test
    void contentsThatDoNotFitGetAWarningOutline() {
        MemoryContents tooFar = MemoryContents.empty().with(2, 1).with(300, 9);
        MemoryContents tooWide = MemoryContents.empty().with(1, 0x1FF);
        MemoryContents fits = MemoryContents.empty().with(2, 1).with(255, 0xFF);

        assertThat(countPixelsNear(drawWith(tooFar), WARNING, 70)).as("a word past the last address").isGreaterThan(0);
        assertThat(countPixelsNear(drawWith(tooWide), WARNING, 70)).as("a value wider than the word").isGreaterThan(0);
        assertThat(countPixelsNear(drawWith(fits), WARNING, 70)).as("contents that fit").isZero();
    }

    private BufferedImage drawWith(MemoryContents contents) {
        return draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(MemoryTypes.CONTENTS, contents.encode()),
                VisibleState.NONE, SCHEME);
    }

    @Test
    void theContentWindowAddressFollowsTheAddressBits() {
        assertThat(MemoryPainter.addressText(0x0F, 8)).isEqualTo("0F");
        assertThat(MemoryPainter.addressText(0x0F, 12)).isEqualTo("00F");
        assertThat(MemoryPainter.addressText(1, 1)).isEqualTo("1");
        assertThat(MemoryPainter.addressText(0xABCDE, 24)).isEqualTo("0ABCDE");
    }

    @Test
    void aMemoryWithoutALabelIsCalledByItsKindAndPlace() {
        assertThat(MemoryPainter.displayName("", MemoryKind.RAM, 1)).isEqualTo("RAM1");
        assertThat(MemoryPainter.displayName("  ", MemoryKind.ROM, 2)).isEqualTo("ROM2");
        assertThat(MemoryPainter.displayName("Sprites", MemoryKind.ROM, 2)).isEqualTo("Sprites");
        assertThat(MemoryPainter.displayName("", MemoryKind.ROM, 0)).isEqualTo("ROM");
    }

    @Test
    void theNameIsDrawnInTheTitleBar() {
        // (the name of the memory is the only thing that differs between the two drawings)
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM);
        Component component = type.newComponent(new ElementId("m1"), ORIGIN, Rotation.R0, Map.of(), "");
        BufferedImage named = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        BufferedImage other = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        MemoryPainter painter = new MemoryPainter();
        for (Map.Entry<BufferedImage, String> drawing : Map.of(named, "WWWW", other, "i").entrySet()) {
            Graphics2D graphics = drawing.getKey().createGraphics();
            ComponentDrawing.paintPlaced(graphics, component, local -> painter.paintWithLabel(local, type,
                    component.parameters(), VisibleState.NONE, SCHEME, drawing.getValue()));
            graphics.dispose();
        }
        int differing = 0;
        for (int x = 0; x < IMAGE_SIZE; x++) {
            for (int y = 0; y < IMAGE_SIZE; y++) {
                if (named.getRGB(x, y) != other.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        assertThat(differing).isGreaterThan(20);
    }
}
