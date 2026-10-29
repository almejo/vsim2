package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal's look: a dark screen with the prompt in the first cell and a still block cursor in the third. */
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
        Component component = terminal.newComponent(new ElementId("c1"), new GridPoint(ORIGIN_UNITS, ORIGIN_UNITS),
                rotation, SMALL, "");
        BufferedImage image = PaintersTest.blankImage(300, 300);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, terminal, component, VisibleState.NONE, scheme, painters);
        graphics.dispose();
        return image;
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
