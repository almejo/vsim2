package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** IA.3: a note of several lines is drawn line by line in the PNG, the SVG and the PDF, as on the canvas. */
class NotesOfSeveralLinesOnPaperTest {

    private static final String THREE_LINES = "WWW\nWWW\nWWW";
    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        openDocument.close();
    }

    private CircuitImage imageWith(String text) {
        openDocument.execute(new AddElementsCommand(List.of(
                new TextLabel(openDocument.document().newLabelId(), new GridPoint(4, 4), text))));
        return CircuitImage.of(openDocument.document(), openDocument.netlist(), openDocument.netlistToken(),
                openDocument.latestSnapshot(), openDocument.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT, Optional.empty()).orElseThrow();
    }

    /** The rows of the image (scale 1) where each line's letters are, from its ascent down to its baseline. */
    private List<Rectangle2D> lineRows(CircuitImage image) {
        TextLabel note = openDocument.document().labels().iterator().next();
        LabelLayout.PlacedText placed = LabelLayout.freeText(note);
        double left = placed.left() - image.gridBounds().x() * GridPoint.PIXELS_PER_UNIT;
        double offsetY = -image.gridBounds().y() * GridPoint.PIXELS_PER_UNIT;
        return placed.lines().stream().map(line -> (Rectangle2D) new Rectangle2D.Double(left,
                line.baselineY() + offsetY - placed.ascent() * 0.7, placed.width(), placed.ascent() * 0.6)).toList();
    }

    private static int ink(BufferedImage image, Rectangle2D area, double scale) {
        int background = image.getRGB(0, 0);
        int count = 0;
        for (int x = (int) (area.getMinX() * scale); x < Math.min(image.getWidth(), area.getMaxX() * scale); x++) {
            for (int y = (int) (area.getMinY() * scale); y < Math.min(image.getHeight(), area.getMaxY() * scale);
                 y++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void thePngHasEveryLine() {
        CircuitImage image = imageWith(THREE_LINES);
        BufferedImage pixels = new BufferedImage(image.width(1), image.height(1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pixels.createGraphics();
        image.paint(graphics, 1);
        graphics.dispose();
        List<Rectangle2D> rows = lineRows(image);
        assertThat(rows).hasSize(3);
        for (Rectangle2D row : rows) {
            assertThat(ink(pixels, row, 1)).as("ink on the row at %s", row.getY()).isPositive();
        }
    }

    @Test
    void theSvgHasEveryLineAndIsTallEnough() {
        CircuitImage oneImage = imageWith("WWW");
        String one = ImageExport.svgDocument(oneImage);
        openDocument.document().labels().forEach(label -> openDocument.document().remove(label.id()));
        CircuitImage threeImage = imageWith(THREE_LINES);
        String three = ImageExport.svgDocument(threeImage);
        assertThat(paths(three)).isEqualTo(paths(one) + 2);
        assertThat(three).contains("height='" + threeImage.height(1));
        assertThat(threeImage.height(1)).isGreaterThan(oneImage.height(1));
    }

    private static int paths(String svg) {
        return svg.split("<path", -1).length - 1;
    }

    @Test
    void thePdfHasEveryLine(@TempDir Path folder) throws IOException {
        CircuitImage image = imageWith(THREE_LINES);
        Path file = folder.resolve("nota.pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, List.of(new PdfPages.PageSize(image.width(1), image.height(1))),
                    (pageIndex, graphics) -> image.paint(graphics, 1, PaintPolicy.print(false)));
        }
        float pixelsPerPoint = 2;
        BufferedImage pdf = PdfReading.render(file, 0, pixelsPerPoint);
        for (Rectangle2D row : lineRows(image)) {
            assertThat(ink(pdf, row, pixelsPerPoint)).as("ink on the row at %s", row.getY()).isPositive();
        }
    }
}
