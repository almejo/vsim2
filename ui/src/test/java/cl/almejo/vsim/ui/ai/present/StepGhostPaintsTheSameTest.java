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
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.GhostChangePainter;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The step ghost paints through {@link GhostChangePainter}: the same pixels as the shared painter given what the step
 * removes or moves (as it is in the circuit: a moved part leaves its place) and what it adds or moves. Both renders are made in the same run, so the platform's
 * antialiasing and fonts never matter; a render without any overlay must differ, so an overlay that paints nothing
 * cannot pass.
 */
class StepGhostPaintsTheSameTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private EditorFixture fixture;
    private OpenDocument document;
    private CircuitCanvas canvas;
    private Component doomed;
    private Wire doomedWire;
    private Component added;
    private Component moved;
    private Component movedThere;
    private Wire addedWire;
    private StepPreview preview;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(AppPreferences.inMemory());
        document = fixture.openDocument();
        canvas = fixture.view().canvas();
        canvas.setSize(WIDTH, HEIGHT);
        canvas.applyViewState(new ViewState(1, 0, 0));
        doomed = place(document, StandardComponentTypes.BUFFER, -5, -2, Map.of());
        doomedWire = new Wire(new ElementId("w-doomed"), new GridPoint(0, 8), new GridPoint(10, 8));
        document.document().add(doomedWire);
        added = document.registry().require(StandardComponentTypes.AND).newComponent(
                new ElementId("g-new"), new GridPoint(5, -4), Rotation.R0, Map.of(), "");
        addedWire = new Wire(new ElementId("w-new"), new GridPoint(-10, -8), new GridPoint(0, -8));
        moved = place(document, StandardComponentTypes.AND, 15, 2, Map.of());
        movedThere = moved.withPosition(new GridPoint(15, 10));
        preview = new StepPreview("task-1", 0, 1, "Pongo la AND", Optional.empty(),
                List.of(added, addedWire, movedThere), Set.of(doomed.id(), doomedWire.id()), Optional.empty());
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void theStepGhostPaintsWhatTheSharedPainterPaints() {
        int[] stepGhost = pixelsWith(stepGhostOverlay());

        int[] sharedPainter = pixelsWith((graphics, state) -> new GhostChangePainter(canvas::scheme,
                document.registry()).paint(graphics, List.of(doomed, doomedWire, moved),
                List.of(added, addedWire, movedThere), canvas));

        assertThat(Arrays.equals(stepGhost, sharedPainter)).isTrue();
    }

    @Test
    void theStepGhostPaintsSomething() {
        int[] stepGhost = pixelsWith(stepGhostOverlay());

        int[] nothing = pixels(paint());

        assertThat(Arrays.equals(stepGhost, nothing)).isFalse();
    }

    private CanvasOverlay stepGhostOverlay() {
        StepGhostOverlay overlay = new StepGhostOverlay(() -> canvas.scheme(), document.registry(), () -> { });
        overlay.show(preview);
        return (graphics, state) -> overlay.paint(graphics, state, canvas);
    }

    /** The canvas painted with {@code overlay} on it, then the overlay taken off again. */
    private int[] pixelsWith(CanvasOverlay overlay) {
        canvas.addOverlay(overlay);
        try {
            return pixels(paint());
        } finally {
            canvas.removeOverlay(overlay);
        }
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
