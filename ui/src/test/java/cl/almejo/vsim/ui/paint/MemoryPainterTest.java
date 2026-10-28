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
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
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
    /** How far a pixel of blue text on white is from white: 150 muted at most, 255 in the full colour. */
    private static final int STRONG_TEXT_STEP = 170;
    private static final int IMAGE_SIZE = 700;
    private static final double ZOOM = 3;
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
                if (isNear(new Color(image.getRGB(x, y)), color, tolerance)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isNear(Color pixel, Color color, int tolerance) {
        return Math.abs(pixel.getRed() - color.getRed()) <= tolerance
                && Math.abs(pixel.getGreen() - color.getGreen()) <= tolerance
                && Math.abs(pixel.getBlue() - color.getBlue()) <= tolerance;
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

    /** The local point of the image pixel ({@code x}, {@code y}) of a part drawn at {@link #ORIGIN}. */
    private static Point2D localOf(Rotation rotation, int x, int y) {
        AffineTransform placed = new AffineTransform();
        placed.translate(ORIGIN.x() * PainterSupport.UNIT, ORIGIN.y() * PainterSupport.UNIT);
        placed.rotate(rotation.quarterTurns() * Math.PI / 2);
        try {
            return placed.inverseTransform(new Point2D.Double(x + 0.5, y + 0.5), null);
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void theFoldSquareIsDrawnInsideTheAreaAClickFoldsFrom() {
        // Shown and folded differ in the window and in the square's ▾ / ▸; in the title bar only in the square.
        Map<String, Object> folded = Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO);
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .specializedFor(Map.of());
        for (Rotation rotation : List.of(Rotation.R0, Rotation.R180)) {
            Rectangle2D area = MemoryPainter.foldToggleArea(type, rotation).orElseThrow();
            Rectangle2D tolerated = new Rectangle2D.Double(area.getX() - 1, area.getY() - 1, area.getWidth() + 2,
                    area.getHeight() + 2);
            Rectangle2D titleBar = MemoryPainter.titleBar(type);
            BufferedImage shown = draw(StandardComponentTypes.RAM, rotation, Map.of(), VisibleState.NONE, SCHEME);
            BufferedImage hidden = draw(StandardComponentTypes.RAM, rotation, folded, VisibleState.NONE, SCHEME);
            int inTitle = 0;
            for (int x = 0; x < IMAGE_SIZE; x++) {
                for (int y = 0; y < IMAGE_SIZE; y++) {
                    Point2D local = localOf(rotation, x, y);
                    if (shown.getRGB(x, y) != hidden.getRGB(x, y) && titleBar.contains(local)) {
                        inTitle++;
                        assertThat(tolerated.contains(local)).as(rotation + " pixel " + x + "," + y).isTrue();
                    }
                }
            }
            assertThat(inTitle).as(rotation + " the glyph changes").isGreaterThan(3);
        }
    }

    @Test
    void aMemoryTurnedSidewaysHasNoFoldSquare() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.ROM)
                .specializedFor(Map.of());
        assertThat(MemoryPainter.foldToggleArea(type, Rotation.R90)).isEmpty();
        assertThat(MemoryPainter.foldToggleArea(type, Rotation.R270)).isEmpty();
        assertThat(MemoryPainter.foldToggleArea(type, Rotation.R0)).isPresent();
    }

    @Test
    void theBadgeSitsInTheTitleBarAndTheAccessedWordIsOutlinedInTheWindow() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .specializedFor(Map.of());
        Rectangle2D titleBar = MemoryPainter.titleBar(type);
        Component component = type.newComponent(new ElementId("m1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        for (Map.Entry<VisibleState, Color> access : Map.of(READING, READ, WRITING, WRITE).entrySet()) {
            // drawn three times larger, so the thin outline has pixels of its exact colour
            BufferedImage image = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
            Graphics2D graphics = image.createGraphics();
            graphics.scale(ZOOM, ZOOM);
            ComponentDrawing.paint(graphics, type, component, access.getKey(), SCHEME, painters);
            graphics.dispose();
            int inTitle = 0;
            int below = 0;
            for (int x = 0; x < IMAGE_SIZE; x++) {
                for (int y = 0; y < IMAGE_SIZE; y++) {
                    if (image.getRGB(x, y) != access.getValue().getRGB()) {
                        continue;
                    }
                    if (titleBar.contains((x + 0.5) / ZOOM, (y + 0.5) / ZOOM)) {
                        inTitle++;
                    } else {
                        below++;
                    }
                }
            }
            assertThat(inTitle).as(access.getKey() + ": the light and the RD / WR pill").isGreaterThan(300);
            assertThat(below).as(access.getKey() + ": the outline of the accessed word").isGreaterThan(300);
        }
    }

    @Test
    void theAddressIsDrawnMutedAndTheWordInTheFullTextColour() {
        Color text = new Color(0x0000FF);
        ColorScheme blueText = ColorScheme.LIGHT.with(ColorRole.TEXT, text);
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .specializedFor(Map.of());
        Component component = type.newComponent(new ElementId("m1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        VisibleState idle = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.NONE, 2, 0,
                List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
        BufferedImage image = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(ZOOM, ZOOM);
        ComponentDrawing.paint(graphics, type, component, idle, blueText, painters);
        graphics.dispose();
        int firstRow = (int) (MemoryPainter.titleBar(type).getMaxY() * ZOOM) + 2;
        int leftmostBluish = IMAGE_SIZE;
        int leftmostFull = IMAGE_SIZE;
        // from inside the window on: the pin names to its left are in the full text colour too
        int windowLeft = (int) ((MemoryPainter.titleBar(type).getX() + 12) * ZOOM);
        for (int x = windowLeft; x < IMAGE_SIZE; x++) {
            for (int y = firstRow; y < IMAGE_SIZE; y++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (pixel.getBlue() > pixel.getRed() + 40 && pixel.getBlue() > pixel.getGreen() + 40) {
                    leftmostBluish = Math.min(leftmostBluish, x);
                }
                if (pixel.getBlue() - pixel.getRed() >= STRONG_TEXT_STEP) {
                    leftmostFull = Math.min(leftmostFull, x);
                }
            }
        }
        assertThat(leftmostBluish).as("the address is drawn").isLessThan(IMAGE_SIZE);
        assertThat(leftmostFull).as("no address pixel is in the full text colour: only the word is")
                .isGreaterThan(leftmostBluish + (int) (3 * ZOOM));
    }

    @Test
    void aLinkedMemoryShowsAChainInItsTitleBarAndAnUnlinkedOneDoesNot() {
        Map<String, Object> shown = Map.of();
        Map<String, Object> shownLinked = Map.of(MemoryTypes.LINK, "/home/a/rom.hex");
        Map<String, Object> foldedLinked = Map.of(MemoryTypes.LINK, "/home/a/rom.hex",
                MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO);
        Map<String, Object> folded = Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO);
        Rectangle2D titleBar = MemoryPainter.titleBar(StandardComponentTypes.registry()
                .require(StandardComponentTypes.RAM).specializedFor(Map.of()));
        for (Rotation rotation : List.of(Rotation.R0, Rotation.R180)) {
            BufferedImage plain = draw(StandardComponentTypes.RAM, rotation, shown, READING, SCHEME);
            assertThat(countDifferences(plain, draw(StandardComponentTypes.RAM, rotation,
                    Map.of(MemoryTypes.LINK, ""), READING, SCHEME))).as(rotation + ": empty link is unlinked")
                    .isZero();
            assertThat(countDifferences(plain, draw(StandardComponentTypes.RAM, rotation, shownLinked, READING,
                    SCHEME))).as(rotation + ": the chain").isGreaterThan(30);
            assertThat(countDifferences(
                    draw(StandardComponentTypes.RAM, rotation, folded, READING, SCHEME),
                    draw(StandardComponentTypes.RAM, rotation, foldedLinked, READING, SCHEME)))
                    .as(rotation + ": the chain of a folded memory").isGreaterThan(30);
        }
        Rectangle2D glyph = MemoryPainter.linkGlyphArea(StandardComponentTypes.registry()
                .require(StandardComponentTypes.RAM).specializedFor(Map.of()));
        assertThat(titleBar.contains(glyph)).as("inside the title bar").isTrue();
        assertThat(MemoryPainter.foldToggleArea(StandardComponentTypes.registry()
                .require(StandardComponentTypes.RAM).specializedFor(Map.of()), Rotation.R0).orElseThrow()
                .intersects(glyph)).as("not over the square").isFalse();
    }

    @Test
    void aDeselectedMemorySaysCsIsZeroInItsTitle() {
        VisibleState idle = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.NONE, 2, 0,
                List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
        VisibleState deselected = new VisibleState.MemoryState(false, VisibleState.MemoryAccess.NONE, 2, 0,
                List.of(0x11L, 0x22L, 0x33L, 0x44L, 0x55L));
        Map<String, Object> folded = Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO);

        BufferedImage on = draw(StandardComponentTypes.RAM, Rotation.R0, folded, idle, SCHEME);
        BufferedImage off = draw(StandardComponentTypes.RAM, Rotation.R0, folded, deselected, SCHEME);

        assertThat(countDifferences(on, off)).as("«CS = 0» in the title of a folded memory").isGreaterThan(20);
    }

    @Test
    void aWordWrittenUndefinedShowsZInTheWindow() {
        VisibleState zero = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.WRITE, 2, 0,
                List.of(0x11L, 0x22L, 0L, 0x44L, 0x55L));
        VisibleState undefined = new VisibleState.MemoryState(true, VisibleState.MemoryAccess.WRITE, 2, 0,
                List.of(0x11L, 0x22L, 0L, 0x44L, 0x55L), 1L << 2);

        BufferedImage asZero = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), zero, SCHEME);
        BufferedImage asZ = draw(StandardComponentTypes.RAM, Rotation.R0, Map.of(), undefined, SCHEME);

        assertThat(countDifferences(asZero, asZ)).isGreaterThan(5);
        assertThat(MemoryPainter.wordText(0, 2, true)).isEqualTo("Z");
        assertThat(MemoryPainter.wordText(0x7, 2, false)).isEqualTo("07");
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
