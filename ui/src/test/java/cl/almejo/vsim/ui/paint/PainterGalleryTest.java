package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Writes {@code ui/build/painter-gallery.png}: every type in every rotation plus an "on" state, for visual review. */
class PainterGalleryTest {

    private static final double SCALE = 2.5;
    private static final int LABEL_HEIGHT = 20;
    private static final int PADDING = 10;
    /** Painters may draw this far outside their bounds (leads, shadows, glows); see PaintersTest. */
    private static final int MARGIN = 8;

    /** Types shown, one per row. */
    static List<ComponentType> galleryTypes() {
        return new ArrayList<>(StandardComponentTypes.registry().all());
    }

    /** Side of the square area a type needs below its label, in image pixels, in any rotation. */
    static int drawingSide(ComponentType type) {
        double largest = Math.max(type.size().width(), type.size().height()) * PainterSupport.UNIT + 2 * MARGIN;
        return (int) Math.ceil(largest * SCALE) + 2 * PADDING;
    }

    @Test
    void writesAGalleryOfEveryComponentForVisualReview() throws Exception {
        PainterRegistry painters = PainterRegistry.standard();
        List<ComponentType> types = galleryTypes();
        int columns = Rotation.values().length + 1;
        int cellWidth = types.stream().mapToInt(PainterGalleryTest::drawingSide).max().orElseThrow();
        int[] rowTops = new int[types.size() + 1];
        for (int row = 0; row < types.size(); row++) {
            rowTops[row + 1] = rowTops[row] + LABEL_HEIGHT + drawingSide(types.get(row));
        }
        BufferedImage image = PaintersTest.blankImage(cellWidth * columns, rowTops[types.size()]);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int row = 0; row < types.size(); row++) {
            ComponentType type = types.get(row);
            for (int column = 0; column < columns; column++) {
                boolean stateColumn = column == Rotation.values().length;
                Rotation rotation = stateColumn ? Rotation.R0 : Rotation.values()[column];
                VisibleState state = stateColumn ? PaintersTest.sampleState(type.id()) : VisibleState.NONE;
                graphics.setColor(PaintersTest.SCHEME.text());
                graphics.drawString(type.id() + " " + (stateColumn ? "on" : rotation.name()),
                        column * cellWidth + 6, rowTops[row] + 14);
                Rectangle2D drawingArea = new Rectangle2D.Double(column * cellWidth, rowTops[row] + LABEL_HEIGHT,
                        cellWidth, drawingSide(type));
                Component component = type.newComponent(new ElementId("c1"), GridPoint.ORIGIN, rotation, Map.of(), "");
                Rectangle2D bounds = ComponentDrawing.documentBounds(type, component);
                Rectangle2D reach = new Rectangle2D.Double(
                        drawingArea.getCenterX() - (bounds.getWidth() / 2 + MARGIN) * SCALE,
                        drawingArea.getCenterY() - (bounds.getHeight() / 2 + MARGIN) * SCALE,
                        (bounds.getWidth() + 2 * MARGIN) * SCALE, (bounds.getHeight() + 2 * MARGIN) * SCALE);
                assertThat(drawingArea.contains(reach)).as(type.id() + " " + rotation + " fits its cell").isTrue();
                Graphics2D cell = (Graphics2D) graphics.create();
                cell.translate(drawingArea.getCenterX(), drawingArea.getCenterY());
                cell.scale(SCALE, SCALE);
                cell.translate(-bounds.getCenterX(), -bounds.getCenterY());
                ComponentDrawing.paint(cell, type, component, state, PaintersTest.SCHEME, painters);
                cell.dispose();
            }
        }
        graphics.dispose();
        Path output = Path.of("build/painter-gallery.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        assertThat(output).exists();
    }
}
