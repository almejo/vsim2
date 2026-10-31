package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Terminal's look (spec §4, §8.1–§8.5): the screen colours and ANSI palette of the part, cell styles, the seven
 * cursors, the screen effects, the last character's glow and the phosphor persistence; none of the moving or CRT parts
 * in an export, and an empty screen with its chip in the palette.
 */
class TerminalPainterTest {

    /** The image is drawn at this zoom so the glyph stems have whole pixels of the text colour. */
    private static final int ZOOM = 2;
    /** The screen's top-left corner inside the part: a margin right, a title bar and a margin down. */
    private static final int SCREEN_LEFT = 8;
    private static final int SCREEN_TOP = 24;
    private static final int CELL_WIDTH = 8;
    private static final int CELL_HEIGHT = 16;
    private static final Map<String, Object> SMALL = Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20);

    private final PainterRegistry painters = PainterRegistry.standard();
    private final TerminalPainter painter = (TerminalPainter) painters.painterFor(StandardComponentTypes.TERMINAL);

    private static Map<String, Object> small(Object... keysAndValues) {
        Map<String, Object> parameters = new HashMap<>(SMALL);
        for (int index = 0; index < keysAndValues.length; index += 2) {
            parameters.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return parameters;
    }

    private static ComponentType terminal(Map<String, Object> parameters) {
        return StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL).specializedFor(parameters);
    }

    /** The state of a 4 × 20 VT screen that received {@code text}. */
    static VisibleState.TerminalState screenWith(String text) {
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        return new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
    }

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state, ColorScheme scheme,
                               TerminalMoment moment) {
        BufferedImage image = PaintersTest.blankImage(220 * ZOOM, 120 * ZOOM);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(ZOOM, ZOOM);
        painter.paintLive(graphics, terminal(parameters), parameters, state, scheme, Optional.empty(), moment);
        graphics.dispose();
        return image;
    }

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state) {
        return draw(parameters, state, PaintersTest.SCHEME, TerminalMoment.EXPORTED);
    }

    /** The colour at the cell's local point, in the zoomed image. */
    private static int pixel(BufferedImage image, int row, int column, double offsetX, double offsetY) {
        return image.getRGB((int) ((SCREEN_LEFT + column * CELL_WIDTH + offsetX) * ZOOM),
                (int) ((SCREEN_TOP + row * CELL_HEIGHT + offsetY) * ZOOM));
    }

    /** How many pixels of one cell are exactly {@code color}. */
    private static int cellPixelsOf(BufferedImage image, int row, int column, Color color) {
        int count = 0;
        for (int offsetX = 0; offsetX < CELL_WIDTH * ZOOM; offsetX++) {
            for (int offsetY = 0; offsetY < CELL_HEIGHT * ZOOM; offsetY++) {
                int x = (SCREEN_LEFT + column * CELL_WIDTH) * ZOOM + offsetX;
                int y = (SCREEN_TOP + row * CELL_HEIGHT) * ZOOM + offsetY;
                if (image.getRGB(x, y) == color.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void theWindowsScreenHasThePartsLookScaledToTheSpaceItIsGiven() {
        Map<String, Object> green = terminal(small(TerminalTypes.SCREEN_COLORS, "green"))
                .resolveParameters(small(TerminalTypes.SCREEN_COLORS, "green"));
        BufferedImage image = PaintersTest.blankImage(400, 200);
        Graphics2D graphics = image.createGraphics();
        TerminalPainter.paintScreen(graphics, new Rectangle2D.Double(0, 0, 320, 128),
                screenWith("HHHH").frame(), green, PaintersTest.SCHEME);
        graphics.dispose();

        assertThat(image.getRGB(300, 120)).as("the background fills the whole space")
                .isEqualTo(new Color(0x0B130D).getRGB());
        assertThat(image.getRGB(350, 150)).as("nothing outside it").isNotEqualTo(new Color(0x0B130D).getRGB());
        int text = 0;
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                if (image.getRGB(x, y) == new Color(0x33FF66).getRGB()) {
                    text++;
                }
            }
        }
        assertThat(text).as("the first H, twice as large as on the canvas").isGreaterThan(20);
    }

    @Test
    void eachProfileShowsItsScreenColoursAndText() {
        record Expected(String profile, int text, int background) {
        }
        for (Expected expected : new Expected[] {new Expected(TerminalTypes.PROFILE_LOGISIM, 0x33FF66, 0x0B130D),
                new Expected(TerminalTypes.PROFILE_VT, 0xE8E8E8, 0x101010),
                new Expected(TerminalTypes.PROFILE_TELETYPE, 0x2A2A2A, 0xF6F1E3)}) {
            Map<String, Object> parameters = resolvedProfile(expected.profile());
            BufferedImage image = draw(parameters, screenWith("HHHH"));
            assertThat(new Color(pixel(image, 2, 10, 4, 8))).as(expected.profile() + " background")
                    .isEqualTo(new Color(expected.background()));
            assertThat(cellPixelsOf(image, 0, 0, new Color(expected.text()))).as(expected.profile() + " text")
                    .isGreaterThan(10);
        }
    }

    /** The profile's values on a 4 × 20 screen. */
    private static Map<String, Object> resolvedProfile(String profile) {
        ComponentType standard = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL);
        Map<String, Object> parameters = new HashMap<>(standard.profile(profile).orElseThrow().values());
        parameters.put(TerminalTypes.PROFILE, profile);
        parameters.putAll(SMALL);
        return standard.resolveParameters(parameters);
    }

    @Test
    void ansiColoursComeFromThePartsPaletteAndBoldBrightensThem() {
        VisibleState red = screenWith("\u001b[31mHHH\u001b[1mHHH");
        BufferedImage xterm = draw(small(TerminalTypes.ANSI_PALETTE, "xterm"), red);
        assertThat(cellPixelsOf(xterm, 0, 0, new Color(0xCD0000))).isGreaterThan(10);
        assertThat(cellPixelsOf(xterm, 0, 4, new Color(0xFF0000))).as("bold red is bright red").isGreaterThan(10);

        BufferedImage vga = draw(small(TerminalTypes.ANSI_PALETTE, "vga"), red);
        assertThat(cellPixelsOf(vga, 0, 0, new Color(0xAA0000))).isGreaterThan(10);
        assertThat(cellPixelsOf(vga, 0, 0, new Color(0xCD0000))).isZero();
    }

    @Test
    void backgroundsReverseAndUnderlineAreDrawn() {
        BufferedImage image = draw(SMALL, screenWith("\u001b[44m \u001b[0m\u001b[7m \u001b[0m\u001b[4m_\u001b[0m"));
        assertThat(new Color(pixel(image, 0, 0, 4, 8))).as("a blue background").isEqualTo(new Color(0x0000EE));
        assertThat(new Color(pixel(image, 0, 1, 4, 8))).as("reverse: the text colour behind")
                .isEqualTo(new Color(0xE8E8E8));
        assertThat(cellPixelsOf(image, 0, 2, new Color(0xE8E8E8))).as("an underline").isGreaterThan(CELL_WIDTH);
    }

    @Test
    void theThemeColoursFollowTheSchemeAtPaintTime() {
        Map<String, Object> theme = small(TerminalTypes.SCREEN_COLORS, "theme");
        BufferedImage light = draw(theme, screenWith(""), ColorScheme.LIGHT, TerminalMoment.EXPORTED);
        BufferedImage dark = draw(theme, screenWith(""), ColorSchemes.DARK, TerminalMoment.EXPORTED);
        assertThat(new Color(pixel(light, 2, 10, 4, 8))).isEqualTo(ColorScheme.LIGHT.background());
        assertThat(new Color(pixel(dark, 2, 10, 4, 8))).isEqualTo(ColorSchemes.DARK.background());
    }

    @Test
    void theCursorTakesTheChosenShapeAtTheFramesCursorAndAHiddenOneIsNotDrawn() {
        Color text = new Color(0xE8E8E8);
        BufferedImage block = draw(small(TerminalTypes.CURSOR, "block"), screenWith("ab"));
        assertThat(cellPixelsOf(block, 0, 2, text)).isEqualTo(CELL_WIDTH * CELL_HEIGHT * ZOOM * ZOOM);

        BufferedImage bar = draw(small(TerminalTypes.CURSOR, "bar"), screenWith("ab"));
        assertThat(new Color(pixel(bar, 0, 2, 1, 8))).isEqualTo(text);
        assertThat(new Color(pixel(bar, 0, 2, 5, 8))).isNotEqualTo(text);

        BufferedImage hidden = draw(small(TerminalTypes.CURSOR, "block"), screenWith("ab\u001b[?25l"));
        assertThat(cellPixelsOf(hidden, 0, 2, text)).isZero();

        BufferedImage none = draw(small(TerminalTypes.CURSOR, "none"), screenWith("ab"));
        assertThat(cellPixelsOf(none, 0, 2, text)).isZero();
    }

    @Test
    void aBlinkingCursorInItsOffHalfIsNotDrawn() {
        Map<String, Object> block = small(TerminalTypes.CURSOR, "block");
        TerminalMoment off = new TerminalMoment(TerminalAnimation.LIVE, Double.NaN, false, 0, Optional.empty(), 0,
                TerminalMoment.NEVER);
        assertThat(cellPixelsOf(draw(block, screenWith("ab"), PaintersTest.SCHEME, off), 0, 2,
                new Color(0xE8E8E8))).isZero();
    }

    @Test
    void theEffectsShowOnTheCanvasButNeverInAnExport() {
        VisibleState state = screenWith("HELLO WORLD\r\nscan lines");
        TerminalMoment canvas = TerminalMoment.still(TerminalAnimation.STILL);
        BufferedImage none = draw(small(TerminalTypes.EFFECT, "none"), state, PaintersTest.SCHEME, canvas);
        BufferedImage soft = draw(small(TerminalTypes.EFFECT, "soft"), state, PaintersTest.SCHEME, canvas);
        BufferedImage strong = draw(small(TerminalTypes.EFFECT, "strong"), state, PaintersTest.SCHEME, canvas);
        assertThat(PainterTesting.differentPixels(none, soft)).isGreaterThan(500);
        assertThat(PainterTesting.differentPixels(soft, strong)).isGreaterThan(500);

        BufferedImage exported = draw(small(TerminalTypes.EFFECT, "strong"), state);
        BufferedImage plain = draw(small(TerminalTypes.EFFECT, "none"), state);
        assertThat(PainterTesting.differentPixels(exported, plain)).isZero();
    }

    @Test
    void theLastCharacterGlowsPaleYellow() {
        VisibleState state = screenWith("ab");
        TerminalMoment glowing = new TerminalMoment(TerminalAnimation.LIVE, Double.NaN, true,
                TerminalMoment.LAST_GLOW_ALPHA, Optional.empty(), 0, 0);
        BufferedImage image = draw(SMALL, state, PaintersTest.SCHEME, glowing);
        Color corner = new Color(pixel(image, 0, 1, 0.5, 0.5));
        assertThat(corner.getRed()).as("yellowish over the dark screen").isGreaterThan(100);
        assertThat(corner.getBlue()).isLessThan(corner.getRed());
        assertThat(new Color(pixel(image, 0, 0, 0.5, 0.5))).isEqualTo(new Color(0x101010));
    }

    @Test
    void theOldPictureFadesOverTheNewOne() {
        VisibleState.TerminalState old = screenWith("OLD TEXT");
        VisibleState cleared = screenWith("\u000c");
        TerminalMoment fading = new TerminalMoment(TerminalAnimation.LIVE, Double.NaN, true, 0,
                Optional.of(old.frame()), TerminalMoment.GHOST_OPACITY, 0);
        Map<String, Object> soft = small(TerminalTypes.EFFECT, "soft");
        BufferedImage withGhost = draw(soft, cleared, PaintersTest.SCHEME, fading);
        BufferedImage without = draw(soft, cleared, PaintersTest.SCHEME, TerminalMoment.still(TerminalAnimation.STILL));
        assertThat(PainterTesting.differentPixels(withGhost, without)).isGreaterThan(50);
    }

    @Test
    void inBlackAndWhiteTheScreenIsPaperWithInk() {
        ColorScheme monochrome = MonochromeScheme.of(ColorScheme.LIGHT);
        BufferedImage image = draw(small(TerminalTypes.SCREEN_COLORS, "green"),
                screenWith("\u001b[41mHH\u001b[0mHH"), monochrome, TerminalMoment.EXPORTED);
        assertThat(new Color(pixel(image, 2, 10, 4, 8))).isEqualTo(monochrome.background());
        assertThat(cellPixelsOf(image, 0, 2, monochrome.text())).isGreaterThan(10);
        assertThat(new Color(pixel(image, 0, 0, 0.5, 0.5))).as("a coloured background turns to ink")
                .isEqualTo(monochrome.text());
    }

    @Test
    void thePaletteIconShowsAnEmptyScreenWithTheCursorAtHomeAndTheChip() {
        ComponentType standard = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL);
        BufferedImage image = new BufferedImage(700, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        painter.paint(graphics, standard, Map.of(), VisibleState.NONE, PaintersTest.SCHEME);
        graphics.dispose();

        assertThat(new Color(image.getRGB(SCREEN_LEFT + 1, SCREEN_TOP + 8))).as("the bar cursor at home")
                .isEqualTo(new Color(0xE8E8E8));
        assertThat(new Color(image.getRGB(SCREEN_LEFT + 3 * CELL_WIDTH, SCREEN_TOP + 8)))
                .isEqualTo(new Color(0x101010));
    }

    @Test
    void theChipNamesTheModeAndTheSize() {
        assertThat(TerminalPainter.chip(resolvedProfile(TerminalTypes.PROFILE_LOGISIM))).isEqualTo("Logisim · 4×20");
        assertThat(TerminalPainter.chip(resolvedProfile(TerminalTypes.PROFILE_VT))).isEqualTo("VT100 · 4×20");
        assertThat(TerminalPainter.chip(resolvedProfile(TerminalTypes.PROFILE_TELETYPE)))
                .isEqualTo("Teletipo · 4×20");
    }

    @Test
    void theFontAndTheFontSizeChangeTheDrawing() {
        VisibleState state = screenWith("Wg@#");
        BufferedImage plexImage = draw(SMALL, state);
        BufferedImage vt323Image = draw(small(TerminalTypes.FONT, TerminalTypes.FONT_VT323), state);
        assertThat(PainterTesting.differentPixels(plexImage, vt323Image)).isGreaterThan(10);

        BufferedImage largeImage = draw(small(TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_LARGE,
                TerminalTypes.CURSOR, "block"), state);
        int largeCellWidth = TerminalFontSize.LARGE.cellWidth();
        assertThat(new Color(largeImage.getRGB((SCREEN_LEFT + 4 * largeCellWidth + 5) * ZOOM,
                (SCREEN_TOP + 10) * ZOOM))).as("the cursor in a large cell").isEqualTo(new Color(0xE8E8E8));
    }

    @Test
    void itDrawsInsideItsBoundsWhenTurned() {
        ComponentType type = terminal(SMALL);
        for (Rotation rotation : Rotation.values()) {
            Component component = type.newComponent(new ElementId("c1"), new GridPoint(2, 2), rotation, SMALL, "");
            BufferedImage image = PaintersTest.blankImage(300, 300);
            Graphics2D graphics = image.createGraphics();
            ComponentDrawing.paint(graphics, type, component, screenWith("turned"), PaintersTest.SCHEME, painters);
            graphics.dispose();
            Rectangle2D allowed = ComponentDrawing.documentBounds(type, component);
            allowed.setRect(allowed.getX() - 8, allowed.getY() - 8, allowed.getWidth() + 16, allowed.getHeight() + 16);
            int background = PaintersTest.SCHEME.background().getRGB();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (image.getRGB(x, y) != background) {
                        assertThat(allowed.contains(x, y)).as(rotation + " pixel " + x + "," + y).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void theMovingAreaIsTheCursorsReachOrTheWholeScreenWhileTheOldPictureFades() {
        Map<String, Object> automan = small(TerminalTypes.CURSOR, "automan");
        ComponentType type = terminal(automan);
        TerminalMoment moving = new TerminalMoment(TerminalAnimation.LIVE, 1.0, true, 0, Optional.empty(), 0, 0);
        Rectangle2D cursorCell = new Rectangle2D.Double(SCREEN_LEFT + 2 * CELL_WIDTH, SCREEN_TOP, CELL_WIDTH,
                CELL_HEIGHT);
        assertThat(painter.movingArea(type, automan, screenWith("ab"), moving))
                .contains(TerminalCursorPainter.reach(cursorCell));

        TerminalMoment fading = new TerminalMoment(TerminalAnimation.LIVE, Double.NaN, true, 0,
                Optional.of(screenWith("x").frame()), TerminalMoment.GHOST_OPACITY, 0);
        Rectangle2D area = painter.movingArea(type, automan, screenWith("ab"), fading).orElseThrow();
        assertThat(area.getWidth()).isGreaterThanOrEqualTo(20 * CELL_WIDTH);

        assertThat(painter.movingArea(type, automan, screenWith("ab"), TerminalMoment.EXPORTED)).isEmpty();
    }

    /** A state of a 4 × 20 VT screen that received {@code text}, with a character ROM that gave {@code glyphs}. */
    private static VisibleState.TerminalState screenWithGlyphs(String text, GlyphCache glyphs) {
        VisibleState.TerminalState plain = screenWith(text);
        return new VisibleState.TerminalState(plain.owner(), plain.version(), plain.frame(), true, glyphs);
    }

    @Test
    void aCodeTheCharacterRomGaveIsDrawnAsItsBitmapScaledToTheCell() {
        // the left column of the 8 × 8 glyph lit: bit 7 of every row
        GlyphCache leftStem = GlyphCache.EMPTY.with('H', 0x8080_8080_8080_8080L);
        Map<String, Object> external = small(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL);
        BufferedImage image = draw(external, screenWithGlyphs("HO", leftStem));
        Color text = new Color(0xE8E8E8);
        Color background = new Color(0x101010);

        assertThat(cellPixelsOf(image, 0, 0, text)).as("8 rows of a 1 × 2 px pixel at a zoom of 2")
                .isEqualTo(8 * 2 * ZOOM * ZOOM);
        assertThat(new Color(pixel(image, 0, 0, 0.5, 1))).as("bit 7 is the leftmost pixel").isEqualTo(text);
        assertThat(new Color(pixel(image, 0, 0, 0.5, 15))).as("the stem reaches the bottom row").isEqualTo(text);
        assertThat(new Color(pixel(image, 0, 0, 4, 8))).as("no font H crossbar").isEqualTo(background);
        assertThat(cellPixelsOf(image, 0, 1, text)).as("a code not read yet keeps the internal font")
                .isGreaterThan(10);
    }

    @Test
    void anInternalGeneratorIgnoresGlyphs() {
        GlyphCache full = GlyphCache.EMPTY.with('H', -1L);
        BufferedImage image = draw(SMALL, new VisibleState.TerminalState(1, 1, screenWith("H").frame(), false, full));

        assertThat(cellPixelsOf(image, 0, 0, new Color(0xE8E8E8))).as("the font H, not a full block")
                .isLessThan(CELL_WIDTH * CELL_HEIGHT * ZOOM * ZOOM / 2);
    }

    @Test
    void theWindowDrawsTheRomsGlyphsToo() {
        Map<String, Object> resolved = terminal(small(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL))
                .resolveParameters(small(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL));
        BufferedImage image = PaintersTest.blankImage(400, 200);
        Graphics2D graphics = image.createGraphics();
        TerminalPainter.paintScreen(graphics, new Rectangle2D.Double(0, 0, 160, 64), screenWith("H").frame(),
                GlyphCache.EMPTY.with('H', -1L), resolved, PaintersTest.SCHEME);
        graphics.dispose();

        assertThat(new Color(image.getRGB(4, 1))).as("the full block of the ROM's H, where a font H is empty")
                .isEqualTo(new Color(0xE8E8E8));
    }

    @Test
    void aFullVt323ScreenWithTheStrongEffectPaintsQuickly() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(TerminalTypes.FONT, TerminalTypes.FONT_VT323);
        parameters.put(TerminalTypes.EFFECT, "strong");
        ComponentType type = terminal(parameters);
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(24, 80));
        for (int index = 0; index < 24 * 80 - 1; index++) {
            screen.write('A' + index % 26, 0);
        }
        VisibleState state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
        TerminalMoment live = new TerminalMoment(TerminalAnimation.LIVE, 1.0, true, TerminalMoment.LAST_GLOW_ALPHA,
                Optional.of(screen.frame()), TerminalMoment.GHOST_OPACITY, 0);
        BufferedImage image = new BufferedImage(700, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        painter.paintLive(graphics, type, parameters, state, PaintersTest.SCHEME, Optional.of("consola"), live);
        long start = System.nanoTime();
        int paints = 10;
        for (int paint = 0; paint < paints; paint++) {
            painter.paintLive(graphics, type, parameters, state, PaintersTest.SCHEME, Optional.of("consola"), live);
        }
        long averageMilliseconds = (System.nanoTime() - start) / 1_000_000 / paints;
        graphics.dispose();
        assertThat(averageMilliseconds).as("a loose budget per paint").isLessThan(250);
    }
}
