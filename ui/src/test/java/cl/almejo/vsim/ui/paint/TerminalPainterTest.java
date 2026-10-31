package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Terminal's look: without a simulation (palette, ghost) a dark screen with the prompt in the first cell and a still
 * block cursor in the third; while simulating, the frame's cells in the chosen font and a block cursor at its cursor.
 */
class TerminalPainterTest {

    private static final int ORIGIN_UNITS = 2;
    /** The part's origin in the image, in pixels. */
    private static final int ORIGIN = ORIGIN_UNITS * GridPoint.PIXELS_PER_UNIT;
    /** The screen's top-left corner inside the part: a margin right, a title bar and a margin down. */
    private static final int SCREEN_LEFT = 8;
    private static final int SCREEN_TOP = 24;
    private static final int CELL_WIDTH = 8;
    private static final int CELL_HEIGHT = 16;
    private static final int LIGHTER_THAN_SCREEN = 150;
    private static final Map<String, Object> SMALL = Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20);

    private final ComponentType terminal = StandardComponentTypes.registry()
            .require(StandardComponentTypes.TERMINAL).specializedFor(SMALL);
    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage draw(ColorScheme scheme, Rotation rotation) {
        return draw(scheme, rotation, terminal, SMALL, VisibleState.NONE);
    }

    private BufferedImage draw(ColorScheme scheme, Rotation rotation, ComponentType type,
                               Map<String, Object> parameters, VisibleState state) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(ORIGIN_UNITS, ORIGIN_UNITS),
                rotation, parameters, "");
        BufferedImage image = PaintersTest.blankImage(300, 300);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type, component, state, scheme, painters);
        graphics.dispose();
        return image;
    }

    /** The state of a 4 × 20 VT screen that received {@code text}. */
    private static VisibleState.TerminalState screenWith(String text) {
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        return new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
    }

    private static int lightPixels(BufferedImage image, int cellColumn, int cellWidth, int cellHeight) {
        int screen = TerminalPainter.SCREEN_BACKGROUND.getRGB();
        int count = 0;
        for (int offsetX = 0; offsetX < cellWidth; offsetX++) {
            for (int offsetY = 0; offsetY < cellHeight; offsetY++) {
                int rgb = image.getRGB(ORIGIN + SCREEN_LEFT + cellColumn * cellWidth + offsetX,
                        ORIGIN + SCREEN_TOP + offsetY);
                if (brightness(rgb) > brightness(screen) + LIGHTER_THAN_SCREEN) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void aSimulatedScreenShowsItsCellsAndTheBlockCursorWhereTheFrameSaysInsteadOfThePrompt() {
        BufferedImage image = draw(PaintersTest.SCHEME, Rotation.R0, terminal, SMALL, screenWith("Hi"));

        assertThat(lightPixels(image, 0, CELL_WIDTH, CELL_HEIGHT)).as("H").isGreaterThan(5);
        assertThat(lightPixels(image, 1, CELL_WIDTH, CELL_HEIGHT)).as("i").isGreaterThan(3);
        assertThat(pixel(image, 2, CELL_WIDTH / 2, CELL_HEIGHT / 2)).as("the cursor after the text")
                .isEqualTo(TerminalPainter.SCREEN_TEXT.getRGB());
        assertThat(pixel(image, 3, CELL_WIDTH / 2, CELL_HEIGHT / 2)).isEqualTo(TerminalPainter.SCREEN_BACKGROUND.getRGB());
    }

    @Test
    void anEmptySimulatedScreenHasNoPromptAndAHiddenCursorIsNotDrawn() {
        BufferedImage empty = draw(PaintersTest.SCHEME, Rotation.R0, terminal, SMALL, screenWith(""));
        assertThat(lightPixels(empty, 0, CELL_WIDTH, CELL_HEIGHT)).as("the cursor at home")
                .isEqualTo(CELL_WIDTH * CELL_HEIGHT);
        assertThat(lightPixels(empty, 2, CELL_WIDTH, CELL_HEIGHT)).as("no cursor after a prompt").isZero();

        BufferedImage hidden = draw(PaintersTest.SCHEME, Rotation.R0, terminal, SMALL, screenWith("\u001b[?25l"));
        assertThat(lightPixels(hidden, 0, CELL_WIDTH, CELL_HEIGHT)).isZero();
    }

    @Test
    void theFontAndTheFontSizeChangeTheDrawing() {
        VisibleState state = screenWith("Wg@#");
        Map<String, Object> vt323 = new HashMap<>(SMALL);
        vt323.put(TerminalTypes.FONT, TerminalTypes.FONT_VT323);
        BufferedImage plexImage = draw(PaintersTest.SCHEME, Rotation.R0, terminal, SMALL, state);
        BufferedImage vt323Image = draw(PaintersTest.SCHEME, Rotation.R0, terminal.specializedFor(vt323), vt323,
                state);
        assertThat(differentPixels(plexImage, vt323Image)).isGreaterThan(10);

        Map<String, Object> large = new HashMap<>(SMALL);
        large.put(TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_LARGE);
        int largeCellWidth = TerminalFontSize.LARGE.cellWidth();
        int largeCellHeight = TerminalFontSize.LARGE.cellHeight();
        BufferedImage largeImage = draw(PaintersTest.SCHEME, Rotation.R0, terminal.specializedFor(large), large,
                state);
        assertThat(lightPixels(largeImage, 4, largeCellWidth, largeCellHeight)).as("the cursor in a large cell")
                .isEqualTo(largeCellWidth * largeCellHeight);
    }

    private static int differentPixels(BufferedImage first, BufferedImage second) {
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

    private static int pixel(BufferedImage image, int cellColumn, int offsetX, int offsetY) {
        return image.getRGB(ORIGIN + SCREEN_LEFT + cellColumn * CELL_WIDTH + offsetX, ORIGIN + SCREEN_TOP + offsetY);
    }

    @Test
    void theScreenIsDarkAndTheCursorBlockSitsInTheThirdCellAfterThePrompt() {
        BufferedImage image = draw(PaintersTest.SCHEME, Rotation.R0);
        int screen = TerminalPainter.SCREEN_BACKGROUND.getRGB();
        int text = TerminalPainter.SCREEN_TEXT.getRGB();

        assertThat(pixel(image, 1, CELL_WIDTH / 2, CELL_HEIGHT / 2)).as("the cell after the prompt").isEqualTo(screen);
        assertThat(pixel(image, 2, CELL_WIDTH / 2, CELL_HEIGHT / 2)).as("the cursor block").isEqualTo(text);
        assertThat(pixel(image, 2, 1, 1)).isEqualTo(text);
        assertThat(pixel(image, 2, CELL_WIDTH - 1, CELL_HEIGHT - 1)).isEqualTo(text);
        assertThat(pixel(image, 3, CELL_WIDTH / 2, CELL_HEIGHT / 2)).as("nothing after the cursor").isEqualTo(screen);
        assertThat(pixel(image, 10, 0, CELL_HEIGHT * 2)).as("rows below are empty").isEqualTo(screen);
    }

    private static int brightness(int rgb) {
        return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
    }

    @Test
    void thePromptIsDrawnInTheTextColourInTheFirstCell() {
        BufferedImage image = draw(PaintersTest.SCHEME, Rotation.R0);
        int screen = TerminalPainter.SCREEN_BACKGROUND.getRGB();

        int textPixels = 0;
        for (int offsetX = 0; offsetX < CELL_WIDTH; offsetX++) {
            for (int offsetY = 0; offsetY < CELL_HEIGHT; offsetY++) {
                // antialiased strokes: any pixel clearly lighter than the screen belongs to the prompt
                if (brightness(pixel(image, 0, offsetX, offsetY)) > brightness(screen) + LIGHTER_THAN_SCREEN) {
                    textPixels++;
                }
            }
        }
        assertThat(textPixels).isGreaterThan(5);
    }

    @Test
    void theScreenKeepsItsColoursInTheDarkTheme() {
        BufferedImage image = draw(ColorSchemes.DARK, Rotation.R0);

        assertThat(pixel(image, 1, CELL_WIDTH / 2, CELL_HEIGHT / 2))
                .isEqualTo(TerminalPainter.SCREEN_BACKGROUND.getRGB());
        assertThat(pixel(image, 2, CELL_WIDTH / 2, CELL_HEIGHT / 2)).isEqualTo(TerminalPainter.SCREEN_TEXT.getRGB());
    }

    @Test
    void theChipShowsRowsByColumnsAndItDrawsInsideItsBoundsWhenTurned() {
        assertThat(TerminalPainter.chip(terminal.resolveParameters(SMALL))).isEqualTo("4×20");
        for (Rotation rotation : Rotation.values()) {
            BufferedImage image = draw(PaintersTest.SCHEME, rotation);
            Component component = terminal.newComponent(new ElementId("c1"),
                    new GridPoint(ORIGIN_UNITS, ORIGIN_UNITS), rotation, SMALL, "");
            java.awt.geom.Rectangle2D allowed = ComponentDrawing.documentBounds(terminal, component);
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
    void theDefaultTypeFromThePaletteDrawsAnEmptyScreenToo() {
        ComponentType standard = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL);
        BufferedImage image = new BufferedImage(700, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        painters.painterFor(StandardComponentTypes.TERMINAL).paint(graphics, standard, Map.of(), VisibleState.NONE,
                PaintersTest.SCHEME);
        graphics.dispose();

        assertThat(image.getRGB(SCREEN_LEFT + 2 * CELL_WIDTH + 4, SCREEN_TOP + 8))
                .isEqualTo(TerminalPainter.SCREEN_TEXT.getRGB());
    }
}
