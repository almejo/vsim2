package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Teclado's look: a row of keys, the head key in the read colour, «llena», and the capture look. */
class KeyboardPainterTest {

    private static final int SCALE = 4;
    private static final int TITLE_HEIGHT = 16;
    /** A title-bar point above the text and left of the chip, in local pixels. */
    private static final int EMPTY_TITLE_X = 80;
    private static final int EMPTY_TITLE_Y = 3;
    private static final int COLOUR_TOLERANCE = 40;

    private final ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
    private final KeyboardPainter painter = new KeyboardPainter();

    private static VisibleState.KeyboardState queue(String text) {
        List<Integer> bytes = new ArrayList<>();
        for (char character : text.toCharArray()) {
            bytes.add((int) character);
        }
        return new VisibleState.KeyboardState(1, bytes, bytes.size(), bytes.size());
    }

    private BufferedImage draw(VisibleState state, ColorScheme scheme, Map<String, Object> parameters,
                               boolean capturing) {
        BufferedImage image = new BufferedImage(16 * 8 * SCALE + 40, 6 * 8 * SCALE + 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.translate(20, 20);
        graphics.scale(SCALE, SCALE);
        painter.paintWithCapture(graphics, type, parameters, state, scheme, null, capturing);
        graphics.dispose();
        return image;
    }

    private static boolean near(int rgb, Color color) {
        return Math.abs((rgb >> 16 & 0xFF) - color.getRed()) < COLOUR_TOLERANCE
                && Math.abs((rgb >> 8 & 0xFF) - color.getGreen()) < COLOUR_TOLERANCE
                && Math.abs((rgb & 0xFF) - color.getBlue()) < COLOUR_TOLERANCE;
    }

    /** How many pixels near {@code color} lie in the local rectangle (in part pixels). */
    private static int count(BufferedImage image, Color color, int left, int top, int width, int height) {
        int count = 0;
        for (int x = 20 + left * SCALE; x < 20 + (left + width) * SCALE; x++) {
            for (int y = 20 + top * SCALE; y < 20 + (top + height) * SCALE; y++) {
                if (near(image.getRGB(x, y), color)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int pixelAt(BufferedImage image, int localX, int localY) {
        return image.getRGB(20 + localX * SCALE, 20 + localY * SCALE);
    }

    @Test
    void theHeadKeyIsInTheReadColourAndTheOthersAreNot() {
        ColorScheme scheme = ColorScheme.LIGHT.with(ColorRole.MEMORY_READ, new Color(0xCC00AA));
        BufferedImage image = draw(queue("hola"), scheme, Map.of(), false);

        int head = count(image, scheme.memoryRead(), 5, 20, 11, 13);
        int second = count(image, scheme.memoryRead(), 18, 20, 11, 13);

        assertThat(head).as("the first key").isGreaterThan(100);
        assertThat(second).as("the second key").isZero();
    }

    @Test
    void anEmptyQueueShowsNoKeyInTheReadColour() {
        ColorScheme scheme = ColorScheme.LIGHT.with(ColorRole.MEMORY_READ, new Color(0xCC00AA));
        BufferedImage image = draw(queue(""), scheme, Map.of(), false);

        assertThat(count(image, scheme.memoryRead(), 0, 16, 128, 32)).isZero();
    }

    @Test
    void theChipSaysFullInTheConflictColourOnlyWhenTheQueueIsFull() {
        ColorScheme scheme = ColorScheme.LIGHT;
        Map<String, Object> small = Map.of(KeyboardTypes.CAPACITY, 4);

        BufferedImage full = draw(queue("abcd"), scheme, small, false);
        BufferedImage notFull = draw(queue("abc"), scheme, small, false);

        assertThat(count(full, scheme.wireConflict(), 90, 0, 38, TITLE_HEIGHT)).isGreaterThan(5);
        assertThat(count(notFull, scheme.wireConflict(), 90, 0, 38, TITLE_HEIGHT)).isZero();
    }

    @Test
    void theFillBarTurnsWarningAtThreeQuartersAndConflictWhenFull() {
        ColorScheme scheme = ColorScheme.LIGHT;
        Map<String, Object> small = Map.of(KeyboardTypes.CAPACITY, 4);
        int barLeft = 26;
        int barTop = 16 + 4 + 13 + 7;

        BufferedImage half = draw(queue("ab"), scheme, small, false);
        BufferedImage threeQuarters = draw(queue("abc"), scheme, small, false);
        BufferedImage full = draw(queue("abcd"), scheme, small, false);

        assertThat(count(half, scheme.memoryWrite(), barLeft, barTop - 1, 90, 4)).isZero();
        assertThat(count(threeQuarters, scheme.memoryWrite(), barLeft, barTop - 1, 90, 4)).isGreaterThan(20);
        assertThat(count(full, scheme.wireConflict(), barLeft, barTop - 1, 90, 4)).isGreaterThan(20);
    }

    @Test
    void theCaptureColourFillsTheTitleBarOnlyWhileCapturing() {
        ColorScheme scheme = ColorScheme.LIGHT;

        BufferedImage capturing = draw(queue("hi"), scheme, Map.of(), true);
        BufferedImage idle = draw(queue("hi"), scheme, Map.of(), false);

        assertThat(pixelAt(capturing, EMPTY_TITLE_X, EMPTY_TITLE_Y)).isEqualTo(scheme.selection().getRGB());
        assertThat(pixelAt(idle, EMPTY_TITLE_X, EMPTY_TITLE_Y)).isEqualTo(scheme.componentShade().getRGB());
        assertThat(count(idle, scheme.selection(), 0, 0, 128, 15)).isZero();
    }

    @Test
    void theCaptureColourParameterChoosesTheToneOfTheSchemeInUse() {
        Map<String, Object> pink = Map.of(KeyboardTypes.CAPTURE_COLOR, "pink");

        BufferedImage light = draw(queue(""), ColorScheme.LIGHT, pink, true);
        BufferedImage dark = draw(queue(""), ColorSchemes.DARK, pink, true);

        assertThat(pixelAt(light, EMPTY_TITLE_X, EMPTY_TITLE_Y)).isEqualTo(0xFFD81B60);
        assertThat(pixelAt(dark, EMPTY_TITLE_X, EMPTY_TITLE_Y)).isEqualTo(0xFFFF7AAC);
        assertThat(KeyboardPainter.captureColor(ColorScheme.LIGHT, "automatic")).isEqualTo(ColorScheme.LIGHT.selection());
        assertThat(KeyboardPainter.captureColor(ColorSchemes.DARK, "automatic")).isEqualTo(ColorSchemes.DARK.selection());
    }

    @Test
    void everyChoiceOfTheParameterHasATone() {
        for (String choice : KeyboardTypes.CAPTURE_COLOR_CHOICES) {
            assertThat(KeyboardPainter.captureColor(ColorScheme.LIGHT, choice)).as(choice).isNotNull();
            assertThat(KeyboardPainter.captureColor(ColorSchemes.DARK, choice)).as(choice).isNotNull();
        }
    }

    @Test
    void theTextOnTheCaptureColourIsDarkOnlyWhenTheColourIsLight() {
        assertThat(KeyboardPainter.textOn(Color.WHITE)).isEqualTo(KeyboardPainter.NEAR_BLACK);
        assertThat(KeyboardPainter.textOn(new Color(0xFFD54F))).isEqualTo(KeyboardPainter.NEAR_BLACK);
        assertThat(KeyboardPainter.textOn(new Color(0x1E88E5))).isEqualTo(Color.WHITE);
        assertThat(KeyboardPainter.textOn(new Color(0xC79100))).isEqualTo(Color.WHITE);
        assertThat(KeyboardPainter.textOn(Color.BLACK)).isEqualTo(Color.WHITE);
    }

    @Test
    void controlCodesAndMultiByteCharactersBecomeOneKeyEach() {
        assertThat(KeyboardPainter.keyTexts(List.of(10, 13, 9, 8, 127, 27, 32, 3, (int) 'a'), true))
                .containsExactly("↵", "⏎", "⇥", "⌫", "⌦", "⎋", "␣", "^C", "a");
        assertThat(KeyboardPainter.keyTexts(List.of(0xC3, 0xB1, (int) 'x', 0xE2, 0x82, 0xAC), true))
                .containsExactly("ñ", "x", "€");
        assertThat(KeyboardPainter.keyTexts(List.of(0xF1), false)).containsExactly("ñ");
    }

    @Test
    void paintingNeverNeedsTheCaptureState() {
        // the palette icon and the ghost paint through the plain painter: the idle look
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        painter.paint(graphics, type, Map.of(), queue("a"), ColorScheme.LIGHT);
        graphics.dispose();

        assertThat(count(image, ColorScheme.LIGHT.selection(), 0, 0, 20, 12)).isZero();
    }
}
