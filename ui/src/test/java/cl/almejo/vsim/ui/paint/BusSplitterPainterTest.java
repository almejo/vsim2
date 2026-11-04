package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;
import static org.assertj.core.api.Assertions.assertThat;

/** The labels of a bus splitter's or joiner's pins: the bit of each pin, or its group's ranges. */
class BusSplitterPainterTest {

    private static final int IMAGE_SIZE = 900;

    private static FontMetrics labelMetrics() {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            Font font = PainterSupport.BASE_FONT.deriveFont(BusSplitterPainter.LABEL_SIZE);
            return graphics.getFontMetrics(font);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void withoutGroupsEveryPinIsLabelledWithItsBit() {
        assertThat(BusSplitterPainter.labels(Map.of(StandardComponentTypes.WIDTH, 4,
                StandardComponentTypes.GROUPS, ""))).containsExactly("0", "1", "2", "3");
    }

    @Test
    void aGroupedPinIsLabelledWithItsRanges() {
        assertThat(BusSplitterPainter.labels(Map.of(StandardComponentTypes.WIDTH, 32,
                StandardComponentTypes.GROUPS, "0-3|7-11,25-31"))).isEqualTo(List.of("0-3", "7-11,25-31"));
    }

    @Test
    void theLabelsOfTheSameGroupsAreWorkedOutOnceForEveryPaint() {
        Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, 16, StandardComponentTypes.GROUPS,
                "0-7|8-15");

        assertThat(BusSplitterPainter.labels(parameters)).isSameAs(BusSplitterPainter.labels(Map.copyOf(parameters)));
    }

    @Test
    void everyGroupedLabelFitsWholeOverItsLead() {
        FontMetrics metrics = labelMetrics();
        for (String typeId : List.of(StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER)) {
            for (String groups : LONG_GROUPINGS) {
                Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, 32,
                        StandardComponentTypes.GROUPS, groups);
                ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
                for (String label : BusSplitterPainter.labels(parameters)) {
                    assertThat((double) metrics.stringWidth(label)).as("%s %s «%s»", typeId, groups, label)
                            .isLessThanOrEqualTo(BusSplitterPainter.labelRoom(type));
                }
            }
        }
    }

    @Test
    void aGroupedLabelSitsAboveItsOwnLeadClearOfTheSpineInEveryRotation() {
        ColorScheme scheme = ColorScheme.LIGHT.with(ColorRole.TEXT, Color.MAGENTA);
        for (String typeId : List.of(StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER)) {
            for (String groups : LONG_GROUPINGS) {
                Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, 32,
                        StandardComponentTypes.GROUPS, groups);
                ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
                for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
                    assertLabelsAboveTheirLeads(type, parameters, quarterTurns, scheme,
                            typeId + " " + groups + " turned " + quarterTurns);
                }
            }
        }
    }

    @Test
    void oneBitPerPinPartsLookExactlyAsBeforeGroups() {
        for (String typeId : List.of(StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER)) {
            for (int width : new int[]{2, 8, 32}) {
                Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, width);
                ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
                for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
                    BufferedImage today = render(type, parameters, quarterTurns, ColorScheme.LIGHT,
                            (graphics, scheme) -> paintAsBeforeGroups(graphics, type, scheme));
                    BufferedImage drawn = render(type, parameters, quarterTurns, ColorScheme.LIGHT,
                            (graphics, scheme) -> new BusSplitterPainter().paint(graphics, type,
                                    type.resolveParameters(parameters), VisibleState.NONE, scheme));
                    assertThat(PainterTesting.differentPixels(today, drawn))
                            .as("%s %d bits turned %d", typeId, width, quarterTurns).isZero();
                }
            }
        }
    }

    /** Groupings with long labels: the RISC-V fields, the {@code sw} immediate and every other bit. */
    private static final List<String> LONG_GROUPINGS = List.of("0-6|7-11|12-14|15-19|20-24|25-31",
            "7-11,25-31|0-6|12-24", "0-15|16-31", "0|1-31",
            "0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30|1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31");

    /** The text pixels of a drawing of the part, taken back to the part's own coordinates. */
    private static void assertLabelsAboveTheirLeads(ComponentType type, Map<String, Object> parameters,
                                                    int quarterTurns, ColorScheme scheme, String context) {
        BufferedImage image = render(type, parameters, quarterTurns, scheme,
                (graphics, colors) -> new BusSplitterPainter().paint(graphics, type,
                        type.resolveParameters(parameters), VisibleState.NONE, colors));
        AffineTransform transform = transform(quarterTurns);
        boolean joining = type.pin(0).position().x() > 0;
        double spineX = (joining ? type.pin(0).position().x() - 1 : 1) * UNIT;
        double halfStroke = 1.75;
        int textPixels = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) != Color.MAGENTA.getRGB()) {
                    continue;
                }
                textPixels++;
                Point2D local;
                try {
                    local = transform.inverseTransform(new Point2D.Double(x + 0.5, y + 0.5), null);
                } catch (NoninvertibleTransformException exception) {
                    throw new AssertionError(exception);
                }
                assertThat(Math.abs(local.getX() - spineX)).as("%s: clear of the spine at %s", context, local)
                        .isGreaterThan(halfStroke);
                assertThat(aboveSomeLead(type, local, spineX, halfStroke))
                        .as("%s: over its lead at %s", context, local).isTrue();
            }
        }
        assertThat(textPixels).as(context).isPositive();
    }

    /** True when {@code local} is between the spine and a pin, above that pin's lead and below the one before. */
    private static boolean aboveSomeLead(ComponentType type, Point2D local, double spineX, double halfStroke) {
        for (int index = 1; index < type.pinCount(); index++) {
            double pinX = type.pin(index).position().x() * UNIT;
            double pinY = type.pin(index).position().y() * UNIT;
            boolean alongTheLead = local.getX() > Math.min(spineX, pinX) && local.getX() < Math.max(spineX, pinX);
            boolean aboveIt = local.getY() < pinY - halfStroke && local.getY() > pinY - 2 * UNIT + halfStroke;
            if (alongTheLead && aboveIt) {
                return true;
            }
        }
        return false;
    }

    /** The drawing of a «Separador de bus» / «Unión de bus» before groups existed, written out independently. */
    private static void paintAsBeforeGroups(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        double spineX = UNIT;
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Double(type.pin(0).position().x() * UNIT, 0, spineX, 0));
        graphics.draw(new Line2D.Double(spineX, 0, spineX, (type.pinCount() - 1) * UNIT));
        for (int index = 1; index < type.pinCount(); index++) {
            double bitX = type.pin(index).position().x() * UNIT;
            double bitY = type.pin(index).position().y() * UNIT;
            PainterSupport.lead(graphics, spineX, bitY, bitX, bitY, scheme);
            PainterSupport.uprightText(graphics, String.valueOf(index - 1), (spineX + bitX) / 2, bitY - 2.5, 5f,
                    scheme.text(), PainterSupport.ALIGN_CENTER);
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** Four times bigger, turned {@code quarterTurns} about the part's origin at the middle of the image. */
    private static AffineTransform transform(int quarterTurns) {
        AffineTransform transform = new AffineTransform();
        transform.translate(IMAGE_SIZE / 2.0, IMAGE_SIZE / 2.0);
        transform.scale(4, 4);
        transform.quadrantRotate(quarterTurns);
        return transform;
    }

    private static BufferedImage render(ComponentType type, Map<String, Object> parameters, int quarterTurns,
                                        ColorScheme scheme, BiConsumer<Graphics2D, ColorScheme> painting) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.transform(transform(quarterTurns));
            painting.accept(graphics, scheme);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Test
    void aGroupedPinOfSeveralBitsIsDrawnThick() {
        ComponentType splitter = StandardComponentTypes.registry().require(StandardComponentTypes.BUS_SPLITTER);
        Map<String, Object> grouped = Map.of(StandardComponentTypes.WIDTH, 2, StandardComponentTypes.GROUPS, "0-1");
        Map<String, Object> single = Map.of(StandardComponentTypes.WIDTH, 2, StandardComponentTypes.GROUPS, "0");

        int groupedInk = ink(splitter.specializedFor(grouped), grouped);
        int singleInk = ink(splitter.specializedFor(single), single);

        assertThat(groupedInk).isGreaterThan(singleInk);
    }

    /** The pixels of the component stroke colour in a drawing of the part, four times bigger. */
    private static int ink(ComponentType type, Map<String, Object> parameters) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.translate(20, 20);
            graphics.scale(4, 4);
            new BusSplitterPainter().paint(graphics, type, type.resolveParameters(parameters), VisibleState.NONE,
                    ColorScheme.LIGHT);
        } finally {
            graphics.dispose();
        }
        return PainterTesting.pixelsOf(image, ColorScheme.LIGHT.componentStroke().getRGB());
    }
}
