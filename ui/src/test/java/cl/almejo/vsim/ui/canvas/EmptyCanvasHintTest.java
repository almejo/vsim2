package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The quiet welcome text of a canvas with nothing in it: screen only, never part of the document. */
class EmptyCanvasHintTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                            Point2D screenPosition) {
                }

                @Override
                public void configure(Component component) {
                }
            });

    private BufferedImage paintCanvas() {
        canvas.setSize(WIDTH, HEIGHT);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Pixels of the middle band of the canvas that are not the plain background colour. */
    private int inkInTheMiddle(BufferedImage image) {
        int ink = 0;
        for (int row = HEIGHT / 2 - 30; row < HEIGHT / 2 + 30; row++) {
            for (int column = WIDTH / 4; column < 3 * WIDTH / 4; column++) {
                if (image.getRGB(column, row) != ColorScheme.LIGHT.background().getRGB()) {
                    ink++;
                }
            }
        }
        return ink;
    }

    private void placeAPartAwayFromTheMiddle() {
        openDocument.document().add(openDocument.registry().require(StandardComponentTypes.AND)
                .newComponent(openDocument.document().newComponentId(), new GridPoint(-30, -20), Rotation.R0,
                        Map.of(), ""));
    }

    @Test
    void anEmptyDocumentShowsTheHintAndAnyElementHidesIt() {
        assertThat(EmptyCanvasHint.isShownFor(openDocument.document())).isTrue();
        placeAPartAwayFromTheMiddle();
        assertThat(EmptyCanvasHint.isShownFor(openDocument.document())).isFalse();
    }

    @Test
    void theCanvasPaintsTheHintInTheMiddleOnlyWhileEmpty() {
        BufferedImage empty = paintCanvas();
        // the grid dots count as ink too, so the comparison is with the same canvas once it has a part
        placeAPartAwayFromTheMiddle();
        BufferedImage filled = paintCanvas();
        assertThat(inkInTheMiddle(empty)).isGreaterThan(inkInTheMiddle(filled) + 100);
    }

    @Test
    void theHintComesBackWhenEverythingIsRemoved() {
        BufferedImage before = paintCanvas();
        placeAPartAwayFromTheMiddle();
        openDocument.document().remove(openDocument.document().components().get(0).id());
        BufferedImage after = paintCanvas();
        assertThat(inkInTheMiddle(after)).isEqualTo(inkInTheMiddle(before));
    }

    @Test
    void aPaneTooSmallForTheWholeTextShowsNone() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        EmptyCanvasHint.paint(graphics, 40, 40, EmptyCanvasHint.lines(Optional.of("Ctrl+K")));
        graphics.dispose();
        for (int row = 0; row < 40; row++) {
            for (int column = 0; column < 40; column++) {
                assertThat(image.getRGB(column, row)).isEqualTo(0xFF000000);
            }
        }
    }

    @Test
    void theTextNamesTheRealMenus() {
        List<String> lines = EmptyCanvasHint.lines(Optional.of("Ctrl+K"));
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains(Messages.text("menu.file"), Messages.text("menu.file.examples"));
    }

    @Test
    void theThirdLineTellsTheDoubleClickAndTheKeyOfEveryAction() {
        assertThat(EmptyCanvasHint.lines(Optional.of("Ctrl+Alt+J")).get(2))
                .isEqualTo("Doble clic: insertar un componente · Ctrl+Alt+J: ejecutar cualquier acción");
    }

    @Test
    void withoutAKeyTheThirdLineNamesTheHelpMenuItem() {
        assertThat(EmptyCanvasHint.lines(Optional.empty()).get(2))
                .isEqualTo("Doble clic: insertar un componente · Ayuda ▸ Buscar una acción…");
    }

    @Test
    void theCanvasAsksForTheKeyEveryTimeItPaintsTheHint() {
        List<String> keys = new ArrayList<>(List.of("Ctrl+K", "F9"));
        canvas.setFindActionKey(() -> Optional.of(keys.removeFirst()));

        paintCanvas();
        assertThat(keys).containsExactly("F9");
        assertThat(canvas.emptyHintLines().get(2)).contains("F9");
    }

    @Test
    void anEmptyDocumentHasNoImageToExportSoTheHintCannotLeakIntoOne() {
        assertThat(CircuitImage.of(openDocument.document(), openDocument.netlist(), openDocument.netlistToken(),
                openDocument.latestSnapshot(), openDocument.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT, Optional.empty())).isEmpty();
    }
}
