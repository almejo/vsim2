package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The ghost painting moved to {@code GhostChangePainter}: the step ghost's pixels must not have changed. */
class StepGhostPaintsTheSameTest {

    /** Hash of the rendered canvas, taken from the code before the extraction. */
    private static final int PAINTED_BEFORE_THE_EXTRACTION = 166094862;

    private EditorFixture fixture;
    private CircuitCanvas canvas;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(cl.almejo.vsim.ui.app.AppPreferences.inMemory());
        canvas = fixture.view().canvas();
        canvas.setSize(400, 300);
        canvas.applyViewState(new ViewState(1, 0, 0));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void stepGhostStillPaintsTheSame() {
        var document = fixture.openDocument();
        Component doomed = place(document, StandardComponentTypes.BUFFER, -5, -2, Map.of());
        Wire doomedWire = new Wire(new ElementId("w-doomed"), new GridPoint(0, 8), new GridPoint(10, 8));
        document.document().add(doomedWire);
        Component added = document.registry().require(StandardComponentTypes.AND).newComponent(
                new ElementId("g-new"), new GridPoint(5, -4), Rotation.R0, Map.of(), "");
        Wire addedWire = new Wire(new ElementId("w-new"), new GridPoint(-10, -8), new GridPoint(0, -8));
        List<DocumentElement> ghosts = List.of(added, addedWire);
        StepPreview preview = new StepPreview("task-1", 0, 1, "Pongo la AND", Optional.empty(), ghosts,
                Set.of(doomed.id(), doomedWire.id()), Optional.empty());
        StepGhostOverlay overlay = new StepGhostOverlay(() -> canvas.scheme(), document.registry(), () -> { });
        canvas.addOverlay((graphics, state) -> overlay.paint(graphics, state, canvas));

        overlay.show(preview);

        assertThat(java.util.Arrays.hashCode(pixels(paint()))).isEqualTo(PAINTED_BEFORE_THE_EXTRACTION);
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
