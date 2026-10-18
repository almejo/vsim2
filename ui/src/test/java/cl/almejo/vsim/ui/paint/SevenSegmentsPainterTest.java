package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SevenSegmentsPainterTest {

    /** Components are drawn at grid (12, 12): 96 document pixels from the image origin. */
    private static final int OFFSET = 96;
    private static final Color MODULE = new Color(0x101010);
    private static final Color ON = new Color(0xFF8800);
    private static final Color OFF = new Color(0x303030);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ColorScheme scheme = SchemeVariants.withDisplay(ColorScheme.LIGHT, MODULE, ON, OFF);
    private final ComponentType single = registry.require(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY);
    private final ComponentType twoDigits = registry.require(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE);

    private BufferedImage draw(ComponentType type, VisibleState state) {
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(12, 12), Rotation.R0, Map.of(), "");
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ComponentDrawing.paint(graphics, type, component, state, scheme, PainterRegistry.standard());
        graphics.dispose();
        return image;
    }

    /** The color of the pixel under the middle of a shape given in local coordinates. */
    private static Color colorAt(BufferedImage image, Shape localShape) {
        Rectangle2D bounds = localShape.getBounds2D();
        return colorAt(image, bounds.getCenterX(), bounds.getCenterY());
    }

    private static Color colorAt(BufferedImage image, double localX, double localY) {
        return new Color(image.getRGB((int) Math.floor(OFFSET + localX), (int) Math.floor(OFFSET + localY)), true);
    }

    private static VisibleState.SevenSegmentsState showing(Value value) {
        return new VisibleState.SevenSegmentsState(value);
    }

    @Test
    void litSegmentsFollowTheNibbleOrShowADash() {
        assertThat(SevenSegmentsPainter.litSegments(null, 0)).isZero();
        assertThat(SevenSegmentsPainter.litSegments(Value.of(4, 8), 0)).isEqualTo(0x7F);
        assertThat(SevenSegmentsPainter.litSegments(Value.highImpedance(4), 0)).isEqualTo(SevenSegmentsPainter.DASH);
        assertThat(SevenSegmentsPainter.litSegments(Value.of(4, 3).withConflict(), 0))
                .isEqualTo(SevenSegmentsPainter.DASH);
        Value upperNibbleUndriven = new Value(8, 0x0A, 0xF0, false);
        assertThat(SevenSegmentsPainter.litSegments(upperNibbleUndriven, 0)).isEqualTo(SevenSegmentGlyph.segments(0xA));
        assertThat(SevenSegmentsPainter.litSegments(upperNibbleUndriven, 1)).isEqualTo(SevenSegmentsPainter.DASH);
    }

    @Test
    void aValueNarrowerThanTheDisplayShowsADash() {
        assertThat(SevenSegmentsPainter.litSegments(Value.of(2, 1), 0)).isEqualTo(SevenSegmentsPainter.DASH);
        assertThat(SevenSegmentsPainter.litSegments(Value.of(4, 5), 1)).isEqualTo(SevenSegmentsPainter.DASH);
        BufferedImage image = draw(twoDigits, showing(Value.of(4, 5)));
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 1, 6))).isEqualTo(ON);
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 1, 0))).isEqualTo(OFF);
    }

    @Test
    void segmentsAreBeveledBarsShortOfTheirEnds() {
        Rectangle2D bounds = SevenSegmentsPainter.beveledBar(0, 0, 12, 0).getBounds2D();
        assertThat(bounds.getMinX()).isCloseTo(SevenSegmentsPainter.SEGMENT_GAP, within(1e-9));
        assertThat(bounds.getMaxX()).isCloseTo(12 - SevenSegmentsPainter.SEGMENT_GAP, within(1e-9));
        assertThat(bounds.getHeight()).isCloseTo(SevenSegmentsPainter.SEGMENT_THICKNESS, within(1e-9));
        Rectangle2D vertical = SevenSegmentsPainter.beveledBar(5, 0, 5, 10).getBounds2D();
        assertThat(vertical.getWidth()).isCloseTo(SevenSegmentsPainter.SEGMENT_THICKNESS, within(1e-9));
        assertThat(vertical.getHeight()).isCloseTo(10 - 2 * SevenSegmentsPainter.SEGMENT_GAP, within(1e-9));
    }

    @Test
    void eightLightsEverySegmentOnTheSchemeModule() {
        BufferedImage image = draw(single, showing(Value.of(4, 8)));
        for (int segment = 0; segment < SevenSegmentsPainter.SEGMENT_COUNT; segment++) {
            assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(single, 0, segment))).as("segment " + segment)
                    .isEqualTo(ON);
        }
        assertThat(colorAt(image, 6, 40)).isEqualTo(MODULE);
    }

    @Test
    void oneLightsOnlyTheRightSegments() {
        BufferedImage image = draw(single, showing(Value.of(4, 1)));
        for (int segment = 0; segment < SevenSegmentsPainter.SEGMENT_COUNT; segment++) {
            Color expected = segment == 1 || segment == 2 ? ON : OFF;
            assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(single, 0, segment))).as("segment " + segment)
                    .isEqualTo(expected);
        }
    }

    @Test
    void aZOrConflictingDigitShowsACenteredDash() {
        for (Value unreadable : new Value[]{Value.highImpedance(4), Value.of(4, 3).withConflict()}) {
            BufferedImage image = draw(single, showing(unreadable));
            for (int segment = 0; segment < SevenSegmentsPainter.SEGMENT_COUNT; segment++) {
                Color expected = segment == 6 ? ON : OFF;
                assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(single, 0, segment)))
                        .as(unreadable + " segment " + segment).isEqualTo(expected);
            }
        }
    }

    @Test
    void withoutAStateEverySegmentIsUnlit() {
        BufferedImage image = draw(single, VisibleState.NONE);
        for (int segment = 0; segment < SevenSegmentsPainter.SEGMENT_COUNT; segment++) {
            assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(single, 0, segment))).isEqualTo(OFF);
        }
    }

    @Test
    void theDoubleDisplayDashesOnlyTheUndrivenDigit() {
        BufferedImage image = draw(twoDigits, showing(new Value(8, 0x0A, 0xF0, false)));
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 1, 6))).isEqualTo(ON);
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 1, 0))).isEqualTo(OFF);
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 0, 0))).isEqualTo(ON);
        assertThat(colorAt(image, SevenSegmentsPainter.segmentShape(twoDigits, 0, 3))).isEqualTo(OFF);
        Rectangle2D left = SevenSegmentsPainter.segmentShape(twoDigits, 1, 6).getBounds2D();
        Rectangle2D right = SevenSegmentsPainter.segmentShape(twoDigits, 0, 6).getBounds2D();
        assertThat(colorAt(image, (left.getMaxX() + right.getMinX()) / 2, left.getCenterY())).isEqualTo(MODULE);
    }

    @Test
    void theBusInputHasAWideLeadBelowTheModuleAndASquareDot() {
        for (ComponentType type : new ComponentType[]{single, twoDigits}) {
            int busPin = StandardComponentTypes.sevenSegmentsBusPin(type);
            double busX = type.pin(busPin).position().x() * 8.0;
            double bottom = type.size().height() * 8.0;
            BufferedImage image = draw(type, showing(Value.of(type.pin(busPin).width(), 1)));
            Color stroke = ColorScheme.LIGHT.componentStroke();
            assertThat(colorAt(image, busX, bottom - 0.5)).as(type.id() + " the square on the pin").isEqualTo(stroke);
            assertThat(colorAt(image, busX, bottom - 4)).as(type.id() + " the lead").isEqualTo(stroke);
            assertThat(colorAt(image, busX - 1, bottom - 4)).as(type.id() + " a bus lead is wide").isEqualTo(stroke);
            assertThat(colorAt(image, busX + 6, bottom - 4)).as(type.id() + " beside the lead")
                    .isNotEqualTo(MODULE);
            assertThat(colorAt(image, busX, bottom - 10)).as(type.id() + " the module above").isEqualTo(MODULE);
        }
        assertThat(SevenSegmentsPainter.digitCount(single)).isEqualTo(1);
        assertThat(SevenSegmentsPainter.digitCount(twoDigits)).isEqualTo(2);
    }
}
