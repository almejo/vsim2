package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.LedMatrixTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Matriz LED's painter: a 2 x 3 matrix at grid (12, 12) has its body from x = 104, each LED 8 px apart, the first
 * one centred at (120, 104) (rows 8 px apart, centred in a body 24 px tall).
 */
class LedMatrixPainterTest {

    private static final int ORIGIN = 96;
    private static final int FIRST_CENTER_X = ORIGIN + 16;
    private static final int FIRST_CENTER_Y = ORIGIN + 8;
    private static final int PITCH = 8;
    private static final double DISTINCT = 60;

    private final ComponentType matrix = StandardComponentTypes.registry().require(StandardComponentTypes.LED_MATRIX);
    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state, ColorScheme scheme) {
        Map<String, Object> all = new HashMap<>(Map.of("rows", 2, "columns", 3));
        all.putAll(parameters);
        Component component = matrix.newComponent(new ElementId("c1"), new GridPoint(12, 12), Rotation.R0, all, "");
        ComponentType shown = matrix.specializedFor(component.parameters());
        BufferedImage image = new BufferedImage(260, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 260, 200);
        ComponentDrawing.paint(graphics, shown, component, state, scheme, painters);
        graphics.dispose();
        return image;
    }

    private static VisibleState.LedMatrixState rows(Value... rows) {
        return new VisibleState.LedMatrixState(List.of(rows));
    }

    private static Color led(BufferedImage image, int row, int column) {
        return new Color(image.getRGB(FIRST_CENTER_X + column * PITCH, FIRST_CENTER_Y + row * PITCH));
    }

    private static double distance(Color first, Color second) {
        return Math.sqrt(Math.pow(first.getRed() - second.getRed(), 2) + Math.pow(first.getGreen() - second.getGreen(), 2)
                + Math.pow(first.getBlue() - second.getBlue(), 2));
    }

    @Test
    void thePainterIsTheMatrixOneNotTheGenericBox() {
        assertThat(painters.painterFor(StandardComponentTypes.LED_MATRIX)).isInstanceOf(LedMatrixPainter.class);
    }

    @Test
    void aLitPixelIsFilledWithTheChosenColorAndAnOffOneIsDimmerOnEveryScheme() {
        VisibleState.LedMatrixState state = rows(Value.of(3, 0b100), Value.of(3, 0b001));
        for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK, ColorSchemes.CLASSIC)) {
            BufferedImage image = draw(Map.of("style", "pixel", "color", "green"), state, scheme);
            assertThat(led(image, 0, 0)).isEqualTo(LedPainter.colorFor("green"));
            assertThat(led(image, 1, 2)).isEqualTo(LedPainter.colorFor("green"));
            assertThat(distance(led(image, 0, 1), led(image, 0, 0))).isGreaterThan(DISTINCT);
        }
    }

    @Test
    void everyStyleShowsALitLedDifferentFromAnOffOne() {
        VisibleState.LedMatrixState state = rows(Value.of(3, 0b100), Value.of(3, 0));
        for (String style : LedMatrixTypes.STYLES) {
            BufferedImage image = draw(Map.of("style", style), state, ColorSchemes.DARK);
            assertThat(distance(led(image, 0, 0), led(image, 1, 0))).as(style).isGreaterThan(DISTINCT);
        }
    }

    @Test
    void everyStyleDrawsABodyAndThePinLeadsWithoutFailingOnEverySize() {
        for (String style : LedMatrixTypes.STYLES) {
            for (String input : List.of("rows", "scan")) {
                for (int rows : new int[]{1, 8, 32}) {
                    BufferedImage image = draw(Map.of("style", style, "input", input, "rows", rows, "columns", 64),
                            VisibleState.NONE, ColorSchemes.LIGHT);
                    assertThat(image.getRGB(ORIGIN + 20, ORIGIN + 2)).as("%s %s %d", style, input, rows)
                            .isNotEqualTo(ColorSchemes.LIGHT.background().getRGB());
                }
            }
        }
    }

    @Test
    void theOffLedsCanBeHiddenAndThenTheBodyShowsInsteadOfThem() {
        VisibleState.LedMatrixState state = rows(Value.of(3, 0b100), Value.of(3, 0));
        BufferedImage shown = draw(Map.of("style", "pixel"), state, ColorSchemes.DARK);
        BufferedImage hidden = draw(Map.of("style", "pixel", "show-unlit", false), state, ColorSchemes.DARK);
        assertThat(led(hidden, 1, 0)).isNotEqualTo(led(shown, 1, 0));
        assertThat(led(hidden, 0, 0)).as("a lit LED stays").isEqualTo(led(shown, 0, 0));
    }

    @Test
    void anUnknownBitIsOffWithARingInTheSchemesUnknownColor() {
        ColorScheme scheme = ColorSchemes.DARK;
        VisibleState.LedMatrixState state = rows(new Value(3, 0b100, 0b001, false), Value.of(3, 0));
        BufferedImage image = draw(Map.of("style", "pixel"), state, scheme);
        assertThat(led(image, 0, 2)).as("the unknown bit is not lit").isNotEqualTo(LedPainter.colorFor("red"));
        double closestWithRing = Double.MAX_VALUE;
        double closestWithout = Double.MAX_VALUE;
        for (int offset = 3; offset <= 5; offset++) {
            Color ring = new Color(image.getRGB(FIRST_CENTER_X + 2 * PITCH + offset, FIRST_CENTER_Y));
            Color plain = new Color(image.getRGB(FIRST_CENTER_X + 2 * PITCH + offset, FIRST_CENTER_Y + PITCH));
            closestWithRing = Math.min(closestWithRing, distance(ring, scheme.wireUnknown()));
            closestWithout = Math.min(closestWithout, distance(plain, scheme.wireUnknown()));
        }
        assertThat(closestWithRing).as("the ring colours a pixel around the unknown LED, more than around a known one")
                .isLessThan(closestWithout - 20);
        assertThat(led(image, 1, 2)).as("a known off LED has no ring colour").isNotEqualTo(scheme.wireUnknown());
    }

    @Test
    void aWholeRowInConflictIsUnknownToo() {
        VisibleState.LedMatrixState state = rows(Value.of(3, 0b111).withConflict(), Value.of(3, 0));
        BufferedImage image = draw(Map.of("style", "pixel"), state, ColorSchemes.DARK);
        assertThat(led(image, 0, 0)).isNotEqualTo(LedPainter.colorFor("red"));
    }

    @Test
    void aStateOfAnotherSizeIsIgnoredInsteadOfFailing() {
        BufferedImage image = draw(Map.of("style", "pixel"), rows(Value.of(5, 31)), ColorSchemes.DARK);
        assertThat(led(image, 0, 0)).isNotEqualTo(LedPainter.colorFor("red"));
    }

    @Test
    void onPaperALitLedIsBlackAndAnOffOneIsWhite() {
        BufferedImage image = draw(Map.of("style", "pixel"), rows(Value.of(3, 0b100), Value.of(3, 0)),
                MonochromeScheme.of(ColorSchemes.LIGHT));
        assertThat(led(image, 0, 0)).isEqualTo(Color.BLACK);
        assertThat(led(image, 1, 0)).isEqualTo(Color.WHITE);
    }

    @Test
    void thePaletteIconIsDrawnByTheSamePainter() {
        ComponentIcon icon = new ComponentIcon(matrix.specializedFor(Map.of()), painters.painterFor(matrix.id()),
                ColorSchemes.LIGHT, 32);
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        boolean painted = false;
        for (int pixelY = 0; pixelY < 32 && !painted; pixelY++) {
            for (int pixelX = 0; pixelX < 32; pixelX++) {
                painted |= (image.getRGB(pixelX, pixelY) >>> 24) != 0;
            }
        }
        assertThat(painted).isTrue();
    }
}
