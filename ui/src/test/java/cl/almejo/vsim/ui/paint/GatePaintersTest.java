package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatePaintersTest {

    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final int ORIGIN = 40;
    private static final int IMAGE_SIZE = 160;
    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final List<String> GATES = List.of(StandardComponentTypes.AND, StandardComponentTypes.OR,
            StandardComponentTypes.XOR);

    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage render(String typeId, Map<String, Object> parameters) {
        ComponentType type = StandardComponentTypes.registry().require(typeId);
        BufferedImage image = PaintersTest.blankImage(IMAGE_SIZE, IMAGE_SIZE);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(ORIGIN, ORIGIN);
        painters.painterFor(typeId).paint(graphics, type, parameters, VisibleState.NONE, SCHEME);
        graphics.dispose();
        return image;
    }

    private static Map<String, Object> parameters(int inputs, boolean negate, int width) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(StandardComponentTypes.GATE_INPUTS, inputs);
        parameters.put(StandardComponentTypes.NEGATE, negate);
        parameters.put("width", width);
        return parameters;
    }

    private static long fingerprint(BufferedImage image) {
        return Arrays.hashCode(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    @Test
    void everyGateDrawsInsideItsBoxForEveryInputCountNegationAndWidth() {
        int background = SCHEME.background().getRGB();
        for (String typeId : GATES) {
            for (int inputs = 2; inputs <= 8; inputs++) {
                for (boolean negate : new boolean[] {false, true}) {
                    for (int width : new int[] {1, 8}) {
                        Map<String, Object> parameters = parameters(inputs, negate, width);
                        ComponentType type = StandardComponentTypes.registry().require(typeId)
                                .specializedFor(parameters);
                        BufferedImage image = render(typeId, parameters);
                        String label = typeId + " inputs=" + inputs + " negate=" + negate + " width=" + width;
                        for (PinSpec pin : type.pins()) {
                            assertThat(pin.position().x()).as(label).isBetween(0, type.size().width());
                            assertThat(pin.position().y()).as(label).isBetween(0, type.size().height());
                        }
                        int slack = 6;
                        for (int y = 0; y < IMAGE_SIZE; y++) {
                            for (int x = 0; x < IMAGE_SIZE; x++) {
                                boolean outside = x < ORIGIN - slack || y < ORIGIN - slack
                                        || x > ORIGIN + type.size().width() * UNIT + slack
                                        || y > ORIGIN + type.size().height() * UNIT + slack;
                                if (outside) {
                                    assertThat(image.getRGB(x, y)).as(label + " at " + x + "," + y)
                                            .isEqualTo(background);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void theBodyIsFourUnitsTallAndCentredOnTheOutputRowWhateverTheInputCount() {
        for (String typeId : GATES) {
            for (int inputs = 2; inputs <= 8; inputs++) {
                Map<String, Object> parameters = parameters(inputs, false, 1);
                ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
                Rectangle2D body = LogicGatePainter.bodyBounds(type);
                double outputY = StandardComponentTypes.gateOutputRow(inputs) * UNIT;
                String label = typeId + " inputs=" + inputs;
                assertThat(body.getHeight()).as(label).isEqualTo(4 * UNIT - 4);
                assertThat(body.getCenterY()).as(label).isEqualTo(outputY);
            }
        }
    }

    @Test
    void wideGatesDrawThickLeadsAndThinOnesDoNot() {
        BufferedImage thin = render(StandardComponentTypes.AND, parameters(2, false, 1));
        BufferedImage thick = render(StandardComponentTypes.AND, parameters(2, false, 8));
        int probeX = ORIGIN + 3;
        int background = SCHEME.background().getRGB();
        int thinRows = 0;
        int thickRows = 0;
        for (int offset = -4; offset <= 4; offset++) {
            if (thin.getRGB(probeX, ORIGIN + offset) != background) {
                thinRows++;
            }
            if (thick.getRGB(probeX, ORIGIN + offset) != background) {
                thickRows++;
            }
        }
        assertThat(thickRows).isGreaterThan(thinRows);
    }

    @Test
    void theBufferDrawsWithAndWithoutBubbleAndAsABus() {
        BufferedImage plain = render(StandardComponentTypes.BUFFER, Map.of(StandardComponentTypes.NEGATE, false));
        BufferedImage negated = render(StandardComponentTypes.BUFFER, Map.of(StandardComponentTypes.NEGATE, true));
        BufferedImage wide = render(StandardComponentTypes.BUFFER,
                Map.of(StandardComponentTypes.NEGATE, false, "width", 8));
        assertThat(fingerprint(plain)).isNotEqualTo(fingerprint(negated)).isNotEqualTo(fingerprint(wide));
    }

    @Test
    void oneBitGatesWithTwoToFourInputsLookAsTheyAlwaysDid() {
        Map<String, Long> actual = new LinkedHashMap<>();
        for (String typeId : GATES) {
            for (int inputs = 2; inputs <= 4; inputs++) {
                for (boolean negate : new boolean[] {false, true}) {
                    actual.put(typeId + inputs + negate, fingerprint(render(typeId, parameters(inputs, negate, 1))));
                }
            }
        }
        actual.put("buffer", fingerprint(render(StandardComponentTypes.BUFFER, Map.of())));
        actual.put("not", fingerprint(render(StandardComponentTypes.BUFFER,
                Map.of(StandardComponentTypes.NEGATE, true))));
        assertThat(actual).isEqualTo(GOLDEN);
    }

    /** Fingerprints of the renderings of the 1-bit gates from before the 5..8 input work. */
    private static final Map<String, Long> GOLDEN = Map.ofEntries(
            Map.entry("and2false", 719174782L), Map.entry("and2true", -698063267L),
            Map.entry("and3false", 1479925246L), Map.entry("and3true", 62687197L),
            Map.entry("and4false", -1004488384L), Map.entry("and4true", 1873240863L),
            Map.entry("buffer", 1074107120L), Map.entry("not", 1557544536L),
            Map.entry("or2false", 1941660063L), Map.entry("or2true", 1595454390L),
            Map.entry("or3false", -289352037L), Map.entry("or3true", -635557710L),
            Map.entry("or4false", -1204222053L), Map.entry("or4true", -1550427726L),
            Map.entry("xor2false", 2142382705L), Map.entry("xor2true", -1849791900L),
            Map.entry("xor3false", -88629395L), Map.entry("xor3true", 214163296L),
            Map.entry("xor4false", -1003499411L), Map.entry("xor4true", -700706720L));
}
