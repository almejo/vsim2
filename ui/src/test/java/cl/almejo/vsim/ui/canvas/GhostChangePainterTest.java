package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GhostChangePainterTest {

    private static final int SIZE = 200;
    private EditorFixture fixture;
    private GhostChangePainter painter;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(AppPreferences.inMemory());
        painter = new GhostChangePainter(() -> ColorScheme.LIGHT, fixture.openDocument().registry());
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Component gate(int x, int y) {
        return fixture.openDocument().registry().require(StandardComponentTypes.AND)
                .newComponent(new ElementId("gate"), new GridPoint(x, y), Rotation.R0, Map.of(), "");
    }

    private BufferedImage paint(List<DocumentElement> leaving, List<DocumentElement> arriving) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT.background());
        graphics.fillRect(0, 0, SIZE, SIZE);
        painter.paint(graphics, leaving, arriving, fixture.view().canvas());
        graphics.dispose();
        return image;
    }

    private static long differingPixels(BufferedImage image, int fromX, int toX) {
        long count = 0;
        for (int x = fromX; x < toX; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (image.getRGB(x, y) != ColorScheme.LIGHT.background().getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void anArrivingPartShowsAtItsNewPlaceOnly() {
        BufferedImage image = paint(List.of(), List.of(gate(10, 5)));

        assertThat(differingPixels(image, 80, SIZE)).isPositive();
        assertThat(differingPixels(image, 0, 70)).isZero();
    }

    @Test
    void aLeavingPartAndWireAreOutlinedWhereTheyWere() {
        Wire wire = new Wire(new ElementId("wire"), new GridPoint(0, 20), new GridPoint(5, 20));

        BufferedImage image = paint(List.of(gate(10, 5), wire), List.of());

        assertThat(differingPixels(image, 0, SIZE)).isPositive();
    }

    @Test
    void nothingToShowPaintsNothing() {
        assertThat(differingPixels(paint(List.of(), List.of()), 0, SIZE)).isZero();
    }
}
