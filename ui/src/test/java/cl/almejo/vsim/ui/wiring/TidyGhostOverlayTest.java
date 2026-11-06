package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The preview of «Ordenar circuito» painted over the canvas: what changed is ghost, what it replaces is dimmed. */
class TidyGhostOverlayTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final int PIXELS_PER_UNIT = GridPoint.PIXELS_PER_UNIT;

    @TempDir
    Path directory;

    private EditorFixture fixture;
    private OpenDocument openDocument;
    private CircuitCanvas canvas;
    private Component gate;
    private Component keptGate;
    private TidyGhostOverlay overlay;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(AppPreferences.inMemory());
        openDocument = fixture.openDocument();
        canvas = fixture.view().canvas();
        canvas.setSize(WIDTH, HEIGHT);
        canvas.applyViewState(new ViewState(1, 0, 0)); // shows x -25..25, y -18.75..18.75
        gate = place(openDocument, StandardComponentTypes.AND, -15, -8, Map.of());
        keptGate = place(openDocument, StandardComponentTypes.AND, -15, 8, Map.of());
        overlay = new TidyGhostOverlay(fixture.view());
    }

    @AfterEach
    void close() {
        overlay.dispose();
        fixture.close();
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** The plan of moving {@code gate} to the right, as a tidy would deliver it. */
    private CircuitTidyPlan planMovingTheGateRight() {
        CircuitDocument after = openDocument.document().copy();
        after.replace(gate.withPosition(new GridPoint(10, -8)));
        return new CircuitTidyPlan(after, 1, 0, Set.of(), TidyPlan.EMPTY);
    }

    private static long differingPixels(BufferedImage first, BufferedImage second, int fromX, int toX) {
        long count = 0;
        for (int x = fromX; x < toX; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int screenX(int unit) {
        return WIDTH / 2 + unit * PIXELS_PER_UNIT;
    }

    @Test
    void theGhostShowsMovedPartsAtTheirNewPlace() {
        BufferedImage without = paint();

        overlay.show(openDocument.document().copy(), planMovingTheGateRight());
        BufferedImage with = paint();

        assertThat(differingPixels(without, with, screenX(10), WIDTH)).isPositive(); // the new place
        assertThat(differingPixels(without, with, 0, screenX(-20))).isZero(); // nothing else
        assertThat(overlay.ghostIds()).containsExactly(gate.id());
        assertThat(overlay.dimmedIds()).containsExactly(gate.id());
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(gate.position()); // a ghost only
    }

    @Test
    void theOldPlaceIsDimmedWithItsOutline() {
        BufferedImage without = paint();

        overlay.show(openDocument.document().copy(), planMovingTheGateRight());
        BufferedImage with = paint();

        assertThat(differingPixels(without, with, screenX(-16), screenX(-2))).isPositive();
    }

    @Test
    void newAndGoneElementsAreGhostAndDimmedAndUnchangedOnesNeither() {
        Wire gone = new Wire(new ElementId("gone"), new GridPoint(-20, 0), new GridPoint(-16, 0));
        openDocument.document().add(gone);
        CircuitDocument before = openDocument.document().copy();
        CircuitDocument after = before.copy();
        after.remove(gone.id());
        Wire created = new Wire(new ElementId("created"), new GridPoint(0, 0), new GridPoint(4, 0));
        after.add(created);

        overlay.show(before, new CircuitTidyPlan(after, 0, 0, Set.of(), TidyPlan.EMPTY));

        assertThat(overlay.ghostIds()).containsExactly(created.id());
        assertThat(overlay.dimmedIds()).containsExactly(gone.id());
        assertThat(overlay.ghostIds()).doesNotContain(keptGate.id());
    }

    @Test
    void clearTakesTheGhostAway() {
        BufferedImage without = paint();
        overlay.show(openDocument.document().copy(), planMovingTheGateRight());

        overlay.clear();

        assertThat(overlay.isShowing()).isFalse();
        assertThat(differingPixels(without, paint(), 0, WIDTH)).isZero();
    }

    @Test
    void theOverlayIsRemovedWhenTheViewIsDisposed() {
        int withOverlay = canvas.overlayCount();
        overlay.dispose();

        assertThat(canvas.overlayCount()).isEqualTo(withOverlay - 1);
    }

    @Test
    void theGhostNeverReachesAnExportedImage() throws IOException {
        byte[] plain = png("plain");

        overlay.show(openDocument.document().copy(), planMovingTheGateRight());

        assertThat(png("ghost")).isEqualTo(plain);
    }

    private byte[] png(String name) throws IOException {
        Path target = directory.resolve(name + ".png");
        CircuitImage image = CircuitImage.of(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), openDocument.latestSnapshot(), openDocument.registry(),
                PainterRegistry.standard(), fixture.view().services().appearance().scheme(), Optional.empty())
                .orElseThrow();
        ImageExport.writePng(target, image, 1);
        return Files.readAllBytes(target);
    }
}
