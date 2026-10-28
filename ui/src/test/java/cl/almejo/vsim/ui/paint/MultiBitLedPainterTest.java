package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The row of lights of a multi-bit LED: one light per bit, lit only on 1. */
class MultiBitLedPainterTest {

    private static final int ORIGIN_UNITS = 4;
    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final int IMAGE_SIZE = 200;
    /** Right and below the light's centre, clear of the glint painted up and to the left of it. */
    private static final double SAMPLE_OFFSET_X = 1.5;
    private static final double SAMPLE_OFFSET_Y = 1;
    private static final List<String> COLORS = List.of("red", "green", "yellow", "blue", "orange", "white", "purple",
            "cyan");

    /**
     * Anti-aliasing and the glint leave a few levels of difference inside a small filled circle, so lens colours match within a few levels;
     * lit and unlit lenses are tens of levels apart.
     */
    private static final int LENS_TOLERANCE = 6;
    private static final Comparator<Color> SAME_LENS = (first, second) ->
            Math.max(Math.abs(first.getRed() - second.getRed()), Math.max(Math.abs(first.getGreen() - second.getGreen()),
                    Math.abs(first.getBlue() - second.getBlue()))) <= LENS_TOLERANCE ? 0 : 1;

    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state, ColorScheme scheme) {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.LED)
                .specializedFor(parameters);
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(ORIGIN_UNITS, ORIGIN_UNITS),
                Rotation.R0, parameters, "");
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        ComponentDrawing.paint(graphics, type, component, state, scheme, painters);
        graphics.dispose();
        return image;
    }

    /** The lens of {@code bit}'s light, with the body's corner at {@code bodyX}, {@code bodyY}. */
    private static Color lens(BufferedImage image, int width, int bit, int bodyX, int bodyY) {
        MultiBitLayout.BitCell cell = MultiBitLayout.cells(width).stream()
                .filter(candidate -> candidate.bit() == bit).findFirst().orElseThrow();
        return new Color(image.getRGB((int) (bodyX + cell.x() + cell.width() / 2 + SAMPLE_OFFSET_X),
                (int) (bodyY + cell.y() + cell.height() / 2 + SAMPLE_OFFSET_Y)));
    }

    /** The body of a LED with its pin on the left starts one lead to the right of the component's corner. */
    private static int bodyCorner() {
        return (ORIGIN_UNITS + MultiBitLayout.LEAD_UNITS) * UNIT;
    }

    private static Color lens(BufferedImage image, int width, int bit) {
        return lens(image, width, bit, bodyCorner(), ORIGIN_UNITS * UNIT);
    }

    @Test
    void eachLightIsLitOnlyWhereItsBitIsOneInTheColourOfTheParameter() {
        for (String color : COLORS) {
            BufferedImage image = draw(Map.of("width", 4, "color", color),
                    new VisibleState.LedState(Value.of(4, 0b1010)), ColorScheme.LIGHT);

            assertThat(lens(image, 4, 3)).as(color).usingComparator(SAME_LENS).isEqualTo(LedPainter.colorFor(color));
            assertThat(lens(image, 4, 2)).as(color).usingComparator(SAME_LENS).isEqualTo(LedPainter.unlitColorFor(color));
            assertThat(lens(image, 4, 1)).as(color).usingComparator(SAME_LENS).isEqualTo(LedPainter.colorFor(color));
            assertThat(lens(image, 4, 0)).as(color).usingComparator(SAME_LENS).isEqualTo(LedPainter.unlitColorFor(color));
        }
    }

    @Test
    void aZBitAndAConflictLeaveTheLightUnlit() {
        Value withZ = new Value(4, 0b1011, 0b0010, false);
        BufferedImage zImage = draw(Map.of("width", 4), new VisibleState.LedState(withZ), ColorScheme.LIGHT);

        assertThat(lens(zImage, 4, 3)).usingComparator(SAME_LENS).isEqualTo(LedPainter.colorFor("red"));
        assertThat(lens(zImage, 4, 1)).as("the bit in Z").usingComparator(SAME_LENS).isEqualTo(LedPainter.unlitColorFor("red"));
        assertThat(lens(zImage, 4, 0)).usingComparator(SAME_LENS).isEqualTo(LedPainter.colorFor("red"));

        BufferedImage conflictImage = draw(Map.of("width", 4),
                new VisibleState.LedState(Value.of(4, 0b1111).withConflict()), ColorScheme.LIGHT);
        for (int bit = 0; bit < 4; bit++) {
            assertThat(lens(conflictImage, 4, bit)).as("bit %d in conflict", bit)
                    .usingComparator(SAME_LENS).isEqualTo(LedPainter.unlitColorFor("red"));
        }
    }

    @Test
    void theMonochromeSchemePaintsLitBlackAndUnlitWhite() {
        ColorScheme paper = MonochromeScheme.of(ColorScheme.LIGHT);
        BufferedImage image = draw(Map.of("width", 4, "color", "green"),
                new VisibleState.LedState(Value.of(4, 0b1000)), paper);

        assertThat(lens(image, 4, 3)).usingComparator(SAME_LENS).isEqualTo(MonochromeScheme.BLACK);
        assertThat(lens(image, 4, 2)).usingComparator(SAME_LENS).isEqualTo(Color.WHITE);
    }

    @Test
    void theLedAndTheSwitchLineUpBitUnderBitWhenSideBySideOnTheSameSide() {
        for (int width : new int[]{4, 8, 9, 16, 32}) {
            Map<String, Object> parameters = Map.of("width", width, "side", "right");
            ComponentType led = StandardComponentTypes.registry().require(StandardComponentTypes.LED)
                    .specializedFor(parameters);
            ComponentType lever = StandardComponentTypes.registry().require(StandardComponentTypes.SWITCH)
                    .specializedFor(parameters);

            assertThat(led.size()).as("%d bits", width).isEqualTo(lever.size());
            assertThat(MultiBitLayout.boxFor(width)).as("%d bits", width)
                    .isEqualTo(MultiBitLayout.boxFor(MultiBitLayout.widthOf(led)));
        }
    }

    @Test
    void withoutASnapshotEveryLightIsUnlit() {
        BufferedImage image = draw(Map.of("width", 4), VisibleState.NONE, ColorScheme.LIGHT);

        for (int bit = 0; bit < 4; bit++) {
            assertThat(lens(image, 4, bit)).usingComparator(SAME_LENS).isEqualTo(LedPainter.unlitColorFor("red"));
        }
    }

    @Test
    void aOneBitLedIsStillTheRoundLightOfBefore() {
        BufferedImage image = draw(Map.of("color", "green"), new VisibleState.LedState(Value.ONE), ColorScheme.LIGHT);

        assertThat(new Color(image.getRGB(ORIGIN_UNITS * UNIT + 8, ORIGIN_UNITS * UNIT + 8)))
                .usingComparator(SAME_LENS).isEqualTo(LedPainter.colorFor("green"));
    }
}
