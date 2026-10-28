package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PaintersTest {
    /** The categories whose parts are drawn as blocks (the D flip-flop, a gate-sized part, is the exception). */
    private static final List<ComponentCategory> BLOCK_CATEGORIES = List.of(ComponentCategory.ARITHMETIC,
            ComponentCategory.PLEXERS, ComponentCategory.SEQUENTIAL);


    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;

    static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final int MARGIN = 8;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final PainterRegistry painters = PainterRegistry.standard();

    /** A representative "on" state per type, shared with the gallery. */
    static VisibleState sampleState(String typeId) {
        if (typeId.equals(InterfacePins.INPUT_PIN)) {
            return new VisibleState.SwitchState(Value.ONE);
        }
        if (typeId.equals(InterfacePins.OUTPUT_PIN)) {
            return new VisibleState.LedState(Value.ONE);
        }
        return switch (typeId) {
            case StandardComponentTypes.LED -> new VisibleState.LedState(Value.ONE);
            case StandardComponentTypes.SWITCH -> new VisibleState.SwitchState(Value.ONE);
            case StandardComponentTypes.CLOCK -> new VisibleState.ClockState(Value.ONE);
            case StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY -> new VisibleState.SevenSegmentsState(Value.of(4, 0x7));
            case StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE -> new VisibleState.SevenSegmentsState(Value.of(8, 0x5A));
            case StandardComponentTypes.ALU -> new VisibleState.AluState(1);
            case StandardComponentTypes.RAM, StandardComponentTypes.ROM -> new VisibleState.MemoryState(true,
                    VisibleState.MemoryAccess.READ, 2, 0, List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
            case StandardComponentTypes.TIME_DIAGRAM -> fullTimeDiagramHistory(StandardComponentTypes.registry().require(typeId));
            default -> VisibleState.NONE;
        };
    }

    /**
     * A full history for the time diagram: {@link TimeDiagramPainter#capacity} samples plus a few, four channels, with 0/1
     * transitions on all of them, one stretch of Z and one conflict sample, so the gallery shows the real look.
     */
    private static VisibleState fullTimeDiagramHistory(ComponentType type) {
        int count = TimeDiagramPainter.capacity(type) + 4;
        long interval = type.parameters().stream()
                .filter(parameter -> parameter.name().equals("sample-interval"))
                .map(parameter -> parameter.defaultValue())
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .findFirst()
                .orElse(150);
        List<TimeDiagramSample> samples = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Value first = index / 3 % 2 == 0 ? Value.ZERO : Value.ONE;
            Value second = index / 5 % 2 == 0 ? Value.ONE : Value.ZERO;
            Value third = index / 2 % 2 == 0 ? Value.ZERO : Value.ONE;
            Value fourth = index % 2 == 0 ? Value.ZERO : Value.ONE;
            // Z over samples count-12..count-8, the conflict at count-5: all inside the visible window
            if (index >= count - 12 && index < count - 7) {
                second = Value.HIGH_IMPEDANCE;
            }
            if (index == count - 5) {
                third = Value.ONE.withConflict();
            }
            samples.add(new TimeDiagramSample(index * interval, List.of(first, second, third, fourth)));
        }
        return new VisibleState.TimeDiagramState(samples);
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
    void busSplittersAndJoinersOfAnyWidthDrawNearTheirBounds() {
        for (String typeId : List.of(StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER)) {
            for (int width : new int[]{2, 32}) {
                Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, width);
                ComponentType type = registry.require(typeId).specializedFor(parameters);
                for (Rotation rotation : Rotation.values()) {
                    assertDrawsNearItsBounds(type, rotation, VisibleState.NONE, 400, parameters);
                }
            }
        }
    }

    @Test
    void blocksOfAnyWidthDrawNearTheirBoundsInEveryRotation() {
        List<ComponentType> blocks = registry.all().stream()
                .filter(type -> BLOCK_CATEGORIES.contains(type.category()))
                .filter(type -> !type.id().equals(StandardComponentTypes.FLIP_FLOP_DATA)).toList();
        assertThat(blocks).extracting(ComponentType::id).contains(StandardComponentTypes.ADDER);
        for (ComponentType block : blocks) {
            assertThat(painters.painterFor(block.id())).as(block.id()).isInstanceOf(BlockPainter.class);
            for (int width : new int[]{1, 32}) {
                Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, width);
                ComponentType type = block.specializedFor(parameters);
                VisibleState state = switch (block.id()) {
                    // the stored value, all ones: the widest text a register or a counter writes
                    case StandardComponentTypes.REGISTER, StandardComponentTypes.COUNTER ->
                            new VisibleState.StoredValueState(Value.of(width, Value.maskFor(width)));
                    default -> sampleState(block.id());
                };
                for (Rotation rotation : Rotation.values()) {
                    assertDrawsNearItsBounds(type, rotation, state, 320, parameters);
                }
            }
        }
    }

    @Test
    void theGrownMuxAndDecoderDrawNearTheirBoundsInEveryRotation() {
        List<Map<String, Object>> muxes = List.of(Map.of("inputs", "4", StandardComponentTypes.WIDTH, 1),
                Map.of("inputs", "4", StandardComponentTypes.WIDTH, 32));
        List<Map<String, Object>> decoders = List.of(Map.of("inputBits", 1), Map.of("inputBits", 4));
        for (Map<String, Object> parameters : muxes) {
            assertGrownBlockDrawsNearItsBounds(StandardComponentTypes.MUX, parameters);
        }
        for (Map<String, Object> parameters : decoders) {
            assertGrownBlockDrawsNearItsBounds(StandardComponentTypes.DECODER, parameters);
        }
        assertThat(registry.require(StandardComponentTypes.DECODER).specializedFor(decoders.get(1)).size().height())
                .isGreaterThan(registry.require(StandardComponentTypes.DECODER).size().height());
    }

    private void assertGrownBlockDrawsNearItsBounds(String typeId, Map<String, Object> parameters) {
        ComponentType type = registry.require(typeId).specializedFor(parameters);
        assertThat(painters.painterFor(typeId)).isInstanceOf(BlockPainter.class);
        for (Rotation rotation : Rotation.values()) {
            assertDrawsNearItsBounds(type, rotation, VisibleState.NONE, 420, parameters);
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
        assertDrawsNearItsBounds(type, rotation, state, imageSize, Map.of());
    }

    private void assertDrawsNearItsBounds(ComponentType type, Rotation rotation, VisibleState state, int imageSize,
                                          Map<String, Object> parameters) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(12, 12), rotation, parameters, "");
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
        ComponentType type = registry.require(StandardComponentTypes.AND);
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
        // the 1 is black on the light green of a 1 (SwitchPainter.textColor): some pixels are far darker than the fill
        assertThat(SwitchPainter.textColor(SCHEME.wireOne())).isEqualTo(Color.BLACK);
        assertThat(Arrays.stream(upright).filter(rgb -> new Color(rgb).getGreen() < 100).count()).isPositive();
        assertThat(patchAround(StandardComponentTypes.SWITCH, Rotation.R180, 13.5, 8)).isEqualTo(upright);
        assertThat(patchAround(StandardComponentTypes.SWITCH, Rotation.R270, 13.5, 8))
                .isEqualTo(patchAround(StandardComponentTypes.SWITCH, Rotation.R90, 13.5, 8));
    }

    @Test
    void paintersDrawTextOnlyThroughTheUprightHelper() throws IOException {
        try (Stream<Path> sources = Files.list(Path.of("src/main/java/cl/almejo/vsim/ui/paint"))) {
            List<String> offenders = sources
                    .filter(path -> !path.getFileName().toString().equals("PainterSupport.java"))
                    // a Graphics2D that passes text on, not a painter
                    .filter(path -> !path.getFileName().toString().equals("MonochromeGraphics.java"))
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

    /** {@code typeId} placed at grid (12, 12), R0, with {@code parameters}, drawn with the type they give. */
    private BufferedImage drawWith(String typeId, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(12, 12),
                Rotation.R0, parameters, "");
        BufferedImage image = blankImage(200, 200);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, registry.typeOf(component), component, VisibleState.NONE, SCHEME, painters);
        graphics.dispose();
        return image;
    }

    @Test
    void busPinsGetASquareMarkAndOneBitPinsARoundDot() {
        // the input pin's pin is local (16, 8) = screen (112, 104); pixel (113, 105) is fully inside a square mark only
        assertThat(new Color(drawWith(InterfacePins.INPUT_PIN, Map.of(InterfacePins.WIDTH, 8)).getRGB(113, 105)))
                .isEqualTo(SCHEME.componentStroke());
        assertThat(new Color(drawWith(InterfacePins.INPUT_PIN, Map.of()).getRGB(113, 105)))
                .isNotEqualTo(SCHEME.componentStroke());
    }

    /** «Placa» (spec §4): the pin marks are gold pads, of gates, tunnels and bus pins alike. */
    @Test
    void pinMarksOnThePlacaBoardArePaintedInThePadColour() {
        List<Map.Entry<String, Map<String, Object>>> parts = List.of(
                Map.entry(StandardComponentTypes.AND, Map.of()),
                Map.entry(StandardComponentTypes.TUNNEL_ENTRANCE, Map.of()),
                Map.entry(StandardComponentTypes.TUNNEL_EXIT, Map.of()),
                Map.entry(InterfacePins.INPUT_PIN, Map.of(InterfacePins.WIDTH, 8)));
        for (Map.Entry<String, Map<String, Object>> part : parts) {
            Component component = placed(part.getKey(), part.getValue());
            BufferedImage image = drawOn(ColorSchemes.PLACA, component);
            for (int pinIndex = 0; pinIndex < registry.typeOf(component).pinCount(); pinIndex++) {
                GridPoint pin = registry.typeOf(component).pinLocation(component, pinIndex);
                assertThat(new Color(image.getRGB(pin.x() * UNIT, pin.y() * UNIT)))
                        .as("%s pin %d", part.getKey(), pinIndex).isEqualTo(ColorSchemes.PLACA.pad());
            }
        }
    }

    /** Only the mark takes the pad colour: the lead next to it stays in the outline colour. */
    @Test
    void theLeadsKeepTheOutlineColourWhenThePinsHaveTheirOwn() {
        ColorScheme magentaPins = ColorScheme.LIGHT.with(ColorRole.PAD, Color.MAGENTA)
                .with(ColorRole.COMPONENT_STROKE, Color.BLACK).with(ColorRole.BACKGROUND, Color.WHITE);
        Component gate = placed(StandardComponentTypes.AND, Map.of());
        BufferedImage image = drawOn(magentaPins, gate);
        GridPoint input = registry.typeOf(gate).pinLocation(gate, 0);
        Color lead = new Color(image.getRGB(input.x() * UNIT + 4, input.y() * UNIT));
        assertThat(lead.getRed()).as("a grey between black and white, no magenta").isEqualTo(lead.getGreen());
        assertThat(lead).isNotEqualTo(Color.WHITE);
    }

    private Component placed(String typeId, Map<String, Object> parameters) {
        return registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(12, 12), Rotation.R0,
                parameters, "");
    }

    private BufferedImage drawOn(ColorScheme scheme, Component component) {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 200, 200);
        ComponentDrawing.paint(graphics, registry.typeOf(component), component, VisibleState.NONE, scheme, painters);
        graphics.dispose();
        return image;
    }

    @Test
    void wideInterfacePinsWriteTheirWidthInsideTheTag() {
        BufferedImage oneBit = drawWith(InterfacePins.INPUT_PIN, Map.of());
        BufferedImage eightBits = drawWith(InterfacePins.INPUT_PIN, Map.of(InterfacePins.WIDTH, 8));
        int changed = 0;
        for (int x = 98; x < 108; x++) {
            for (int y = 99; y < 109; y++) {
                if (eightBits.getRGB(x, y) != oneBit.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        assertThat(changed).isGreaterThan(5);
    }

    @Test
    void widePinsOfAUserComponentCarryTheirWidthInTheName() {
        assertThat(SubcircuitPainter.pinText(PinSpec.input("a", 0, 2, 8))).isEqualTo("a[8]");
        assertThat(SubcircuitPainter.pinText(PinSpec.input("a", 0, 2))).isEqualTo("a");
        ComponentType byteBuffer = SubcircuitTypes.typeOf(ExampleDefinitions.byteBuffer());
        for (Rotation rotation : Rotation.values()) {
            assertDrawsNearItsBounds(byteBuffer, rotation, VisibleState.NONE, 320);
        }
    }

    @Test
    void widePinNamesAreDrawnWithTheirWidthSuffix() {
        ComponentType wide = SubcircuitTypes.typeOf(ExampleDefinitions.byteBuffer());
        // the same box with 1-bit pins whose names already carry the suffix must look the same away from the pin marks
        List<PinSpec> literalPins = wide.pins().stream()
                .map(pin -> new PinSpec(SubcircuitPainter.pinText(pin), pin.position(), pin.direction(), 1)).toList();
        ComponentType literal = new ComponentType(wide.id(), wide.category(), wide.size(), literalPins,
                wide.parameters(), wide.behaviorFactory());
        BufferedImage wideImage = drawType(wide);
        BufferedImage literalImage = drawType(literal);
        int markHalfSide = 6;
        for (int x = 0; x < wideImage.getWidth(); x++) {
            for (int y = 0; y < wideImage.getHeight(); y++) {
                boolean nearPin = false;
                for (PinSpec pin : wide.pins()) {
                    nearPin |= Math.abs(x - (96 + pin.position().x() * PainterSupport.UNIT)) <= markHalfSide
                            && Math.abs(y - (96 + pin.position().y() * PainterSupport.UNIT)) <= markHalfSide;
                }
                if (!nearPin) {
                    assertThat(wideImage.getRGB(x, y)).as("pixel " + x + "," + y).isEqualTo(literalImage.getRGB(x, y));
                }
            }
        }
    }

    private BufferedImage drawType(ComponentType type) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(12, 12), Rotation.R0, Map.of(), "");
        BufferedImage image = blankImage(320, 320);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type, component, VisibleState.NONE, SCHEME, painters);
        graphics.dispose();
        return image;
    }
}
