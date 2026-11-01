package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas keeps a Terminal's still screen (cells, glyphs and screen effect) as a picture at the device's resolution
 * and draws only the cursor, the last character's glow and the fading old picture on it, so a moving frame costs a blit
 * instead of every glyph again; exports and black and white keep drawing the screen directly.
 */
class TerminalScreenRasterTest {

    private static final double ZOOM = 1.5;
    private static final int IMAGE_WIDTH = 340;
    private static final int IMAGE_HEIGHT = 190;
    /** The screen's top-left corner inside the part and the size of a cell, in part pixels. */
    private static final int SCREEN_LEFT = 8;
    private static final int SCREEN_TOP = 24;
    private static final int CELL_WIDTH = 8;
    private static final int CELL_HEIGHT = 16;
    /** Antialiased edges may round one step apart between the cached picture and the direct drawing. */
    private static final int TOLERANCE = 2;

    private final TerminalPainter painter = (TerminalPainter) PainterRegistry.standard()
            .painterFor(StandardComponentTypes.TERMINAL);

    private static Map<String, Object> parameters(Object... keysAndValues) {
        Map<String, Object> parameters = new HashMap<>(Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20));
        for (int index = 0; index < keysAndValues.length; index += 2) {
            parameters.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return parameters;
    }

    private static ComponentType terminal(Map<String, Object> parameters) {
        return StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL).specializedFor(parameters);
    }

    private static Graphics2D canvasGraphics(BufferedImage image, double zoom) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.scale(zoom, zoom);
        return graphics;
    }

    private BufferedImage direct(Map<String, Object> parameters, VisibleState state, ColorScheme scheme,
                                 TerminalMoment moment) {
        BufferedImage image = PaintersTest.blankImage(IMAGE_WIDTH, IMAGE_HEIGHT);
        Graphics2D graphics = canvasGraphics(image, ZOOM);
        painter.paintLive(graphics, terminal(parameters), parameters, state, scheme, Optional.empty(), moment);
        graphics.dispose();
        return image;
    }

    private BufferedImage cached(Map<String, Object> parameters, VisibleState state, ColorScheme scheme,
                                 TerminalMoment moment, TerminalScreenRaster raster, double zoom) {
        BufferedImage image = PaintersTest.blankImage(IMAGE_WIDTH, IMAGE_HEIGHT);
        Graphics2D graphics = canvasGraphics(image, zoom);
        painter.paintLive(graphics, terminal(parameters), parameters, state, scheme, Optional.empty(), moment, raster);
        graphics.dispose();
        return image;
    }

    private BufferedImage cached(Map<String, Object> parameters, VisibleState state, TerminalMoment moment,
                                 TerminalScreenRaster raster) {
        return cached(parameters, state, PaintersTest.SCHEME, moment, raster, ZOOM);
    }

    /** A live moment: the cursor on, the last character glowing, no old picture. */
    private static TerminalMoment glowing() {
        return new TerminalMoment(TerminalAnimation.LIVE, 1.0, true, TerminalMoment.LAST_GLOW_ALPHA,
                Optional.empty(), 0, 0);
    }

    private static TerminalMoment fading(VisibleState.TerminalState old, double opacity) {
        return new TerminalMoment(TerminalAnimation.LIVE, Double.NaN, true, 0, Optional.of(old.frame()), opacity, 0);
    }

    /** How many pixels differ by more than {@code tolerance} in some channel. */
    private static int differingPixels(BufferedImage first, BufferedImage second, int tolerance) {
        int count = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                Color one = new Color(first.getRGB(x, y), true);
                Color other = new Color(second.getRGB(x, y), true);
                int difference = Math.max(Math.max(Math.abs(one.getRed() - other.getRed()),
                        Math.abs(one.getGreen() - other.getGreen())), Math.max(Math.abs(one.getBlue()
                        - other.getBlue()), Math.abs(one.getAlpha() - other.getAlpha())));
                if (difference > tolerance) {
                    count++;
                }
            }
        }
        return count;
    }

    /** How many pixels of the screen's {@code row} differ by more than the tolerance. */
    private static int differingPixelsInRow(BufferedImage first, BufferedImage second, int row) {
        int top = (int) Math.ceil((SCREEN_TOP + row * CELL_HEIGHT) * ZOOM) + 1;
        int bottom = (int) Math.floor((SCREEN_TOP + (row + 1) * CELL_HEIGHT) * ZOOM) - 1;
        int left = (int) Math.ceil(SCREEN_LEFT * ZOOM) + 1;
        int right = (int) Math.floor((SCREEN_LEFT + 20 * CELL_WIDTH) * ZOOM) - 1;
        return differingPixels(first.getSubimage(left, top, right - left, bottom - top),
                second.getSubimage(left, top, right - left, bottom - top), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "soft", "strong"})
    void theCachedScreenLooksLikeTheDirectDrawing(String effect) {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, effect, TerminalTypes.CURSOR, "block");
        VisibleState state = TerminalPainterTest.screenWith("HELLO\r\n\u001b[1;31mWORLD\u001b[0m\r\nab");
        BufferedImage expected = direct(parameters, state, PaintersTest.SCHEME, glowing());
        TerminalScreenRaster raster = new TerminalScreenRaster();
        BufferedImage first = cached(parameters, state, glowing(), raster);
        BufferedImage second = cached(parameters, state, glowing(), raster);
        assertThat(differingPixels(first, expected, TOLERANCE)).isZero();
        assertThat(differingPixels(second, expected, TOLERANCE)).isZero();
    }

    @Test
    void aRepaintOfTheSameFrameDrawsThePictureAgainWithoutItsGlyphs() {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, "strong");
        VisibleState state = TerminalPainterTest.screenWith("HELLO");
        TerminalScreenRaster raster = new TerminalScreenRaster();
        cached(parameters, state, glowing(), raster);
        cached(parameters, state, TerminalMoment.still(TerminalAnimation.LIVE), raster);
        cached(parameters, state, new TerminalMoment(TerminalAnimation.LIVE, 2.0, false, 0.1, Optional.empty(), 0, 0),
                raster);
        assertThat(raster.renders()).isEqualTo(1);
    }

    @Test
    void aNewFrameAnotherSchemeOrAnotherZoomDrawsThePictureAgain() {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, "soft", TerminalTypes.SCREEN_COLORS,
                "theme");
        TerminalScreenRaster raster = new TerminalScreenRaster();
        cached(parameters, TerminalPainterTest.screenWith("HELLO"), PaintersTest.SCHEME, glowing(), raster, ZOOM);
        cached(parameters, TerminalPainterTest.screenWith("HELLO!"), PaintersTest.SCHEME, glowing(), raster, ZOOM);
        assertThat(raster.renders()).isEqualTo(2);

        BufferedImage dark = cached(parameters, TerminalPainterTest.screenWith("HELLO!"), ColorSchemes.DARK,
                glowing(), raster, ZOOM);
        assertThat(raster.renders()).isEqualTo(3);
        assertThat(new Color(dark.getRGB((int) ((SCREEN_LEFT + 10 * CELL_WIDTH) * ZOOM),
                (int) ((SCREEN_TOP + 2 * CELL_HEIGHT + 4) * ZOOM)))).as("the dark theme's background, not the cached")
                .isNotEqualTo(new Color(PaintersTest.SCHEME.background().getRGB()));

        cached(parameters, TerminalPainterTest.screenWith("HELLO!"), ColorSchemes.DARK, glowing(), raster, 2);
        assertThat(raster.renders()).isEqualTo(4);
    }

    @Test
    void aPartialRepaintFirstStillCachesTheWholeScreen() {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, "soft");
        VisibleState state = TerminalPainterTest.screenWith("HELLO\r\nWORLD");
        TerminalScreenRaster raster = new TerminalScreenRaster();
        BufferedImage partial = PaintersTest.blankImage(IMAGE_WIDTH, IMAGE_HEIGHT);
        Graphics2D graphics = canvasGraphics(partial, ZOOM);
        graphics.clipRect(SCREEN_LEFT, SCREEN_TOP, CELL_WIDTH, CELL_HEIGHT);
        painter.paintLive(graphics, terminal(parameters), parameters, state, PaintersTest.SCHEME, Optional.empty(),
                glowing(), raster);
        graphics.dispose();

        BufferedImage whole = cached(parameters, state, glowing(), raster);
        assertThat(raster.renders()).isEqualTo(1);
        assertThat(differingPixels(whole, direct(parameters, state, PaintersTest.SCHEME, glowing()), TOLERANCE))
                .isZero();
    }

    @Test
    void theOldPictureFadesFromItsOwnPictureOnlyWhereTheScreenChanged() {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, "strong", TerminalTypes.CURSOR, "none");
        VisibleState.TerminalState old = TerminalPainterTest.screenWith("SAME\r\nOLD TEXT");
        VisibleState.TerminalState newer = TerminalPainterTest.screenWith("SAME\r\nnew");
        TerminalScreenRaster raster = new TerminalScreenRaster();
        BufferedImage without = cached(parameters, newer, TerminalMoment.still(TerminalAnimation.LIVE), raster);
        BufferedImage early = cached(parameters, newer, fading(old, TerminalMoment.GHOST_OPACITY), raster);
        BufferedImage late = cached(parameters, newer, fading(old, 0.1), raster);

        assertThat(raster.ghostRenders()).as("the old picture is drawn once for its whole fading").isEqualTo(1);
        assertThat(differingPixelsInRow(early, without, 0)).as("an unchanged row does not brighten").isZero();
        int earlyChange = differingPixelsInRow(early, without, 1);
        assertThat(earlyChange).as("the old text shows where the screen changed").isGreaterThan(30);
        assertThat(differingPixelsInRow(late, without, 1)).isLessThanOrEqualTo(earlyChange);
        assertThat(differingPixels(early, direct(parameters, newer, PaintersTest.SCHEME,
                fading(old, TerminalMoment.GHOST_OPACITY)), 40)).as("close to the direct drawing").isLessThan(60);
    }

    @Test
    void aRepaintOfAFullScreenWithTheStrongEffectCostsAFractionOfDrawingIt() {
        Map<String, Object> parameters = new HashMap<>(Map.of(TerminalTypes.EFFECT, "strong"));
        ComponentType type = terminal(parameters);
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(24, 80));
        for (int index = 0; index < 24 * 80 - 1; index++) {
            screen.write('A' + index % 26, 0);
        }
        VisibleState state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
        TerminalMoment moment = glowing();
        BufferedImage image = new BufferedImage(1100, 700, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvasGraphics(image, ZOOM);
        // what the canvas repaints while the old picture fades: the screen and a little around it
        graphics.clip(new Rectangle2D.Double(SCREEN_LEFT - 2, SCREEN_TOP - 2, 80 * CELL_WIDTH + 4,
                24 * CELL_HEIGHT + 4));
        TerminalScreenRaster raster = new TerminalScreenRaster();
        Runnable direct = () -> painter.paintLive(graphics, type, parameters, state, PaintersTest.SCHEME,
                Optional.empty(), moment);
        Runnable cached = () -> painter.paintLive(graphics, type, parameters, state, PaintersTest.SCHEME,
                Optional.empty(), moment, raster);
        double directMilliseconds = medianMilliseconds(direct);
        double cachedMilliseconds = medianMilliseconds(cached);
        graphics.dispose();
        assertThat(cachedMilliseconds).as("a blit and the cursor's cells, not every glyph and the body again")
                .isLessThan(directMilliseconds / 4);
    }

    private static double medianMilliseconds(Runnable painting) {
        int paints = 15;
        for (int paint = 0; paint < paints; paint++) {
            painting.run();
        }
        double[] milliseconds = new double[paints];
        for (int paint = 0; paint < paints; paint++) {
            long start = System.nanoTime();
            painting.run();
            milliseconds[paint] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(milliseconds);
        return milliseconds[paints / 2];
    }

    @Test
    void exportsAndBlackAndWhiteDrawTheScreenDirectly() {
        Map<String, Object> parameters = parameters(TerminalTypes.EFFECT, "strong");
        VisibleState state = TerminalPainterTest.screenWith("HELLO");
        TerminalScreenRaster raster = new TerminalScreenRaster();
        BufferedImage exported = cached(parameters, state, TerminalMoment.EXPORTED, raster);
        cached(parameters, state, MonochromeScheme.of(PaintersTest.SCHEME),
                TerminalMoment.still(TerminalAnimation.STILL), raster, ZOOM);
        assertThat(raster.renders()).isZero();
        assertThat(differingPixels(exported, direct(parameters, state, PaintersTest.SCHEME,
                TerminalMoment.EXPORTED), 0)).isZero();
    }
}
