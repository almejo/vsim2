package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A click lands on the checkbox the user sees at every zoom: the note is painted on a graphics scaled like the canvas
 * (where font hinting changes the text widths), and each drawn checkbox is hit by a click on its centre.
 */
class StickyNoteZoomHitTest {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final int DARK = 0x80;

    private static StickyNote note() {
        return new StickyNote(new ElementId("s1"), new GridPoint(0, 0), 16, 12, StickyColor.YELLOW,
                new StickyChecklist(List.of(new StickyItem("probar el sumador", false), new StickyItem("dos", false),
                        new StickyItem("tres", false))), false);
    }

    private static boolean darkNear(BufferedImage image, double deviceX, double deviceY) {
        for (int y = (int) deviceY - 1; y <= (int) deviceY + 1; y++) {
            for (int x = (int) deviceX - 1; x <= (int) deviceX + 1; x++) {
                if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                    int green = (image.getRGB(x, y) >> 8) & 0xFF;
                    if (green < DARK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void aClickOnEachDrawnCheckboxTogglesThatItemAt75125And200PercentZoom() {
        for (double zoom : new double[]{0.75, 1.25, 2.0}) {
            StickyNote note = note();
            CircuitDocument document = new CircuitDocument();
            document.add(note);
            double pixelsPerGrid = UNIT * zoom;
            BufferedImage image = new BufferedImage((int) (200 * zoom), (int) (160 * zoom),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.scale(zoom, zoom);
            StickyNotePainter.paint(graphics, note, ColorScheme.LIGHT, pixelsPerGrid, PaintPolicy.CANVAS);
            graphics.dispose();

            List<Rectangle2D> boxes = StickyNoteLayout.of(note, pixelsPerGrid)
                    .checkboxes();
            assertThat(boxes).hasSize(3);
            for (int itemIndex = 0; itemIndex < boxes.size(); itemIndex++) {
                Rectangle2D box = boxes.get(itemIndex);
                assertThat(darkNear(image, box.getX() * zoom, box.getCenterY() * zoom))
                        .as("a checkbox is drawn at the hit box of item %d at zoom %s", itemIndex, zoom).isTrue();
                Optional<StickyNoteHit> hit = StickyNoteHitTester.hit(document,
                        new Point2D.Double(box.getCenterX() / UNIT, box.getCenterY() / UNIT), pixelsPerGrid);
                assertThat(hit).isPresent();
                assertThat(hit.get().part()).isEqualTo(StickyPart.CHECKBOX);
                assertThat(hit.get().itemIndex()).isEqualTo(itemIndex);
            }
        }
    }
}
