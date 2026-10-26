package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contact dots (spec §3.7): a dot of radius 4.5 logical pixels ringed 1.5 px, the same size at any zoom; next to
 * the dot, with no pointer needed, the moving pin's name and «→ other» when it meets a named pin of another part, on
 * at most the four dots nearest the pointer.
 */
class ContactDotsTest {

    private static final NewContact NAMED = new NewContact(new GridPoint(10, 10), Optional.of(new ElementId("c1")),
            Optional.of("in"), Optional.of("in"), Optional.empty());
    private static final NewContact BETWEEN_WIRES = new NewContact(new GridPoint(20, 10), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    private static NewContact meeting(int x, String moving, String met) {
        return new NewContact(new GridPoint(x, 10), Optional.of(new ElementId("c2")), Optional.of(met),
                Optional.of(moving), Optional.of(met));
    }

    private final TestToolContext context = TestToolContext.withNewDocument();

    @AfterEach
    void close() {
        context.close();
    }

    /** Paints with the document transform on, as the canvas overlay does. */
    private BufferedImage paint(List<NewContact> contacts, Optional<Point2D> pointer) {
        BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.transform(context.viewport.documentTransform());
        ContactDots.paint(graphics, context, contacts, pointer);
        graphics.dispose();
        return image;
    }

    private boolean isDot(BufferedImage image, double screenX, double screenY) {
        return image.getRGB((int) screenX, (int) screenY) == ColorScheme.LIGHT.wireConflict().getRGB();
    }

    @Test
    void theDotKeepsItsLogicalSizeAtFourHundredPercent() {
        context.viewport.zoomAt(0, 0, 4);
        BufferedImage image = paint(List.of(NAMED), Optional.empty());
        Point2D centre = context.viewport.gridToScreen(10, 10);

        assertThat(isDot(image, centre.getX(), centre.getY())).isTrue();
        assertThat(isDot(image, centre.getX() + 2, centre.getY())).isTrue();
        assertThat(isDot(image, centre.getX() + 7, centre.getY())).isFalse();
    }

    @Test
    void theLabelIsTheMovingPinsNameAndThePinItMeets() {
        assertThat(ContactDots.label(NAMED)).contains("in");
        assertThat(ContactDots.label(meeting(10, "Cin", "S"))).contains("Cin → S");
        assertThat(ContactDots.label(new NewContact(new GridPoint(1, 3), Optional.of(new ElementId("c3")),
                Optional.of("in"), Optional.empty(), Optional.of("in")))).contains("→ in");
        assertThat(ContactDots.label(BETWEEN_WIRES)).isEmpty();
        assertThat(ContactDots.label(new NewContact(new GridPoint(1, 3), Optional.of(new ElementId("c3")),
                Optional.of(""), Optional.empty(), Optional.empty()))).isEmpty(); // an unnamed pin keeps the dot
    }

    @Test
    void atMostTheFourLabelledDotsNearestThePointerGetALabelPointerOrNot() {
        List<NewContact> contacts = List.of(meeting(0, "A", "X"), BETWEEN_WIRES, meeting(30, "B", "X"),
                meeting(40, "C", "X"), meeting(50, "D", "X"), meeting(60, "E", "X"));

        assertThat(ContactDots.labelled(contacts, Optional.of(new Point2D.Double(55, 10))))
                .extracting(contact -> contact.point().x()).containsExactly(50, 60, 40, 30);
        assertThat(ContactDots.labelled(contacts, Optional.empty()))
                .extracting(contact -> contact.point().x()).containsExactly(0, 30, 40, 50);
    }

    @Test
    void theNameIsWrittenNextToTheDotInTheSchemesTextWithoutThePointerNearIt() {
        BufferedImage unnamed = paint(List.of(BETWEEN_WIRES), Optional.of(new Point2D.Double(20, 10)));
        BufferedImage named = paint(List.of(NAMED), Optional.of(new Point2D.Double(60, 40)));

        assertThat(textPixels(unnamed)).isZero();
        assertThat(textPixels(named)).isPositive();
    }

    @Test
    void theLabelTakesTheCurrentSchemesColours() {
        context.scheme = ColorSchemes.DARK;
        BufferedImage image = paint(List.of(NAMED), Optional.empty());
        assertThat(textPixels(image, ColorSchemes.DARK.text())).isPositive();
    }

    private static int textPixels(BufferedImage image) {
        return textPixels(image, ColorScheme.LIGHT.text());
    }

    private static int textPixels(BufferedImage image, Color colour) {
        int text = colour.getRGB();
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == text) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void noContactsPaintNothing() {
        BufferedImage image = paint(List.of(), Optional.of(new Point2D.Double(10, 10)));
        for (int x = 0; x < image.getWidth(); x += 7) {
            for (int y = 0; y < image.getHeight(); y += 7) {
                assertThat(image.getRGB(x, y)).isZero();
            }
        }
    }
}
