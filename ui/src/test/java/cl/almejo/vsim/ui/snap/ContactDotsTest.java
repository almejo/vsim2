package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contact dots (spec §3.7): a dot of radius 4.5 logical pixels ringed 1.5 px, the same size at any zoom; a named
 * pin's name only while the pointer is within the magnet's reach of it.
 */
class ContactDotsTest {

    private static final NewContact NAMED = new NewContact(new GridPoint(10, 10), Optional.of(new ElementId("c1")),
            Optional.of("in"));
    private static final NewContact BETWEEN_WIRES = new NewContact(new GridPoint(20, 10), Optional.empty(),
            Optional.empty());

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
    void aPinsNameShowsOnlyWhileThePointerIsWithinReach() {
        // at 100 % the reach is one grid unit
        assertThat(ContactDots.nameShown(context, NAMED, Optional.of(new Point2D.Double(10.6, 9.2)))).isTrue();
        assertThat(ContactDots.nameShown(context, NAMED, Optional.of(new Point2D.Double(12, 10)))).isFalse();
        assertThat(ContactDots.nameShown(context, NAMED, Optional.empty())).isFalse();
        assertThat(ContactDots.nameShown(context, BETWEEN_WIRES, Optional.of(new Point2D.Double(20, 10)))).isFalse();
        // zoomed out to 25 % the reach grows to six units
        context.viewport.zoomAt(0, 0, 0.25);
        assertThat(ContactDots.nameShown(context, NAMED, Optional.of(new Point2D.Double(15, 10)))).isTrue();
    }

    @Test
    void theNameIsWrittenNextToTheDotInTheSchemesText() {
        BufferedImage without = paint(List.of(NAMED), Optional.empty());
        BufferedImage with = paint(List.of(NAMED), Optional.of(new Point2D.Double(10, 10)));

        assertThat(textPixels(without)).isZero();
        assertThat(textPixels(with)).isPositive();
    }

    private static int textPixels(BufferedImage image) {
        int text = ColorScheme.LIGHT.text().getRGB();
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
