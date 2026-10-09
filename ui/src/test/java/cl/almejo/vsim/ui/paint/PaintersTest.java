package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class PaintersTest {

    static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final int MARGIN = 8;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final PainterRegistry painters = PainterRegistry.standard();

    /** A representative "on" state per type, shared with the gallery. */
    static VisibleState sampleState(String typeId) {
        return switch (typeId) {
            case StandardComponentTypes.LED -> new VisibleState.LedState(Value.ONE);
            case StandardComponentTypes.SWITCH -> new VisibleState.SwitchState(Value.ONE);
            case StandardComponentTypes.CLOCK -> new VisibleState.ClockState(Value.ONE);
            case StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY -> new VisibleState.SevenSegmentsState(Value.of(4, 0x7));
            case StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE -> new VisibleState.SevenSegmentsState(Value.of(8, 0x5A));
            case StandardComponentTypes.TIME_DIAGRAM -> new VisibleState.TimeDiagramState(List.of(
                    new TimeDiagramSample(0, List.of(Value.ZERO, Value.ONE, Value.HIGH_IMPEDANCE, Value.ZERO)),
                    new TimeDiagramSample(1, List.of(Value.ONE, Value.ONE, Value.ZERO, Value.ZERO)),
                    new TimeDiagramSample(2, List.of(Value.ONE, Value.ZERO, Value.ZERO, Value.ONE))));
            default -> VisibleState.NONE;
        };
    }

    static BufferedImage blankImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private BufferedImage draw(String typeId, Rotation rotation, Map<String, Object> parameters, VisibleState state) {
        ComponentType type = registry.require(typeId);
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(12, 12), rotation, parameters, "");
        BufferedImage image = blankImage(200, 200);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type, component, state, SCHEME, painters);
        graphics.dispose();
        return image;
    }

    @Test
    void everyStandardTypeHasADedicatedPainter() {
        for (ComponentType type : registry.all()) {
            assertThat(painters.hasDedicatedPainter(type.id())).as(type.id()).isTrue();
        }
        assertThat(painters.hasDedicatedPainter("not-a-type")).isFalse();
        assertThat(painters.painterFor("not-a-type")).isInstanceOf(GenericBoxPainter.class);
    }

    @Test
    void everyStandardTypeDrawsSomethingCloseToItsBoundsInEveryRotation() {
        for (ComponentType type : registry.all()) {
            for (Rotation rotation : Rotation.values()) {
                assertDrawsNearItsBounds(type, rotation, sampleState(type.id()), 200);
            }
        }
    }

    @Test
    void subcircuitTypesGetTheSubcircuitPainterAndDrawInsideTheirBox() {
        ComponentType type = SubcircuitTypes.typeOf(ExampleDefinitions.inverter());
        assertThat(painters.painterFor(type.id())).isInstanceOf(SubcircuitPainter.class);
        assertThat(painters.hasDedicatedPainter(type.id())).isFalse();
        for (Rotation rotation : Rotation.values()) {
            assertDrawsNearItsBounds(type, rotation, VisibleState.NONE, 320);
        }
    }

    private void assertDrawsNearItsBounds(ComponentType type, Rotation rotation, VisibleState state, int imageSize) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(12, 12), rotation, Map.of(), "");
        BufferedImage image = blankImage(imageSize, imageSize);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type, component, state, SCHEME, painters);
        graphics.dispose();
        Rectangle2D allowed = ComponentDrawing.documentBounds(type, component);
        allowed.setRect(allowed.getX() - MARGIN, allowed.getY() - MARGIN,
                allowed.getWidth() + 2 * MARGIN, allowed.getHeight() + 2 * MARGIN);
        int background = SCHEME.background().getRGB();
        int drawn = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) != background) {
                    drawn++;
                    assertThat(allowed.contains(x, y)).as(type.id() + " " + rotation + " pixel " + x + "," + y).isTrue();
                }
            }
        }
        assertThat(drawn).as(type.id() + " " + rotation).isGreaterThan(20);
    }

    @Test
    void litLedIsFilledWithItsConfiguredColor() {
        BufferedImage image = draw(StandardComponentTypes.LED, Rotation.R0, Map.of("color", "green"),
                new VisibleState.LedState(Value.ONE));
        assertThat(new Color(image.getRGB(104, 108))).isEqualTo(LedPainter.colorFor("green"));
    }

    @Test
    void switchIsFilledWithTheColorOfItsValue() {
        BufferedImage image = draw(StandardComponentTypes.SWITCH, Rotation.R0, Map.of(), new VisibleState.SwitchState(Value.ONE));
        assertThat(new Color(image.getRGB(101, 104))).isEqualTo(SCHEME.wireOne());
    }

    @Test
    void sevenSegmentGlyphsFollowTheHexadecimalTable() {
        assertThat(SevenSegmentGlyph.segments(0)).isEqualTo(0x3F);
        assertThat(SevenSegmentGlyph.segments(8)).isEqualTo(0x7F);
        assertThat(SevenSegmentGlyph.segments(0xF)).isEqualTo(0x71);
        assertThat(SevenSegmentGlyph.isLit(1, 0)).isFalse();
        assertThat(SevenSegmentGlyph.isLit(1, 1)).isTrue();
        assertThat(SevenSegmentGlyph.isLit(1, 2)).isTrue();
    }

    @Test
    void componentIconPaintsInsideItsSize() {
        ComponentType type = registry.require(StandardComponentTypes.AND2);
        ComponentIcon icon = new ComponentIcon(type, painters.painterFor(type.id()), SCHEME, 24);
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(null, graphics, 8, 8);
        graphics.dispose();
        int painted = 0;
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                    assertThat(x).isBetween(7, 32);
                    assertThat(y).isBetween(7, 32);
                }
            }
        }
        assertThat(icon.getIconWidth()).isEqualTo(24);
        assertThat(icon.getIconHeight()).isEqualTo(24);
        assertThat(painted).isGreaterThan(30);
    }

    /** Half the side of the pixel patch compared around a text. */
    private static final int PATCH = 5;

    /** The pixels around the screen point where local point (localX, localY) lands; the component is at (12, 12). */
    private int[] patchAround(String typeId, Rotation rotation, double localX, double localY) {
        BufferedImage image = draw(typeId, rotation, Map.of(), sampleState(typeId));
        AffineTransform placement = AffineTransform.getTranslateInstance(96, 96);
        placement.rotate(rotation.quarterTurns() * Math.PI / 2);
        Point2D center = placement.transform(new Point2D.Double(localX, localY), null);
        int left = (int) Math.floor(center.getX()) - PATCH;
        int top = (int) Math.floor(center.getY()) - PATCH;
        return image.getRGB(left, top, 2 * PATCH + 1, 2 * PATCH + 1, null, 0, 2 * PATCH + 1);
    }

    @Test
    void switchTextReadsTheSameWhenTheSwitchIsTurnedHalfway() {
        // (13.5, 8) is the middle of the switch body, where its 1/0/Z is centered. R0/R180 (and R90/R270) place
        // that point at the same sub-pixel offset, so upright text gives identical patches; upside-down text does not.
        int[] upright = patchAround(StandardComponentTypes.SWITCH, Rotation.R0, 13.5, 8);
        assertThat(Arrays.stream(upright).filter(rgb -> new Color(rgb).getBlue() > 200).count()).isPositive();
        assertThat(patchAround(StandardComponentTypes.SWITCH, Rotation.R180, 13.5, 8)).isEqualTo(upright);
        assertThat(patchAround(StandardComponentTypes.SWITCH, Rotation.R270, 13.5, 8))
                .isEqualTo(patchAround(StandardComponentTypes.SWITCH, Rotation.R90, 13.5, 8));
    }

    @Test
    void paintersDrawTextOnlyThroughTheUprightHelper() throws IOException {
        try (Stream<Path> sources = Files.list(Path.of("src/main/java/cl/almejo/vsim/ui/paint"))) {
            List<String> offenders = sources
                    .filter(path -> !path.getFileName().toString().equals("PainterSupport.java"))
                    .filter(path -> {
                        String source = readSource(path);
                        return source.contains("drawString(") || source.contains("drawGlyphVector(")
                                || source.contains("TextLayout");
                    })
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertThat(offenders).as("use PainterSupport.uprightText or centeredText").isEmpty();
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void ledToleratesAColorParameterThatIsNotText() {
        ComponentType type = registry.require(StandardComponentTypes.LED);
        BufferedImage image = blankImage(40, 40);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(10, 10);
        new LedPainter().paint(graphics, type, Map.of("color", 7), new VisibleState.LedState(Value.ONE), SCHEME);
        graphics.dispose();
        assertThat(new Color(image.getRGB(18, 22))).isEqualTo(LedPainter.colorFor("red"));
    }
}
