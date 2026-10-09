package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
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
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PainterGalleryTest {

    private static final int CELL_WIDTH = 170;
    private static final int CELL_HEIGHT = 190;
    private static final double SCALE = 2.5;

    @Test
    void writesAGalleryOfEveryComponentForVisualReview() throws Exception {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        PainterRegistry painters = PainterRegistry.standard();
        List<ComponentType> types = registry.all();
        int columns = Rotation.values().length + 1;
        BufferedImage image = PaintersTest.blankImage(CELL_WIDTH * columns, CELL_HEIGHT * types.size());
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int row = 0; row < types.size(); row++) {
            ComponentType type = types.get(row);
            for (int column = 0; column < columns; column++) {
                Rotation rotation = column < Rotation.values().length ? Rotation.values()[column] : Rotation.R0;
                VisibleState state = column < Rotation.values().length ? VisibleState.NONE : PaintersTest.sampleState(type.id());
                graphics.setColor(PaintersTest.SCHEME.text());
                graphics.drawString(type.id() + " " + (column < Rotation.values().length ? rotation.name() : "on"),
                        column * CELL_WIDTH + 6, row * CELL_HEIGHT + 14);
                Graphics2D cell = (Graphics2D) graphics.create();
                cell.translate(column * CELL_WIDTH + CELL_WIDTH / 2.0, row * CELL_HEIGHT + CELL_HEIGHT / 2.0 + 10);
                cell.scale(SCALE, SCALE);
                Component component = type.newComponent(new ElementId("c1"), GridPoint.ORIGIN, rotation, Map.of(), "");
                cell.translate(-ComponentDrawing.documentBounds(type, component).getCenterX(),
                        -ComponentDrawing.documentBounds(type, component).getCenterY());
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
