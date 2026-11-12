package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The two primitives of the accessibility lens: screen overlays and the whole scene painted magnified. */
class ScreenOverlayTest {

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

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private void paintOnce() {
        canvas.setSize(300, 200);
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void aScreenOverlayPaintsInLogicalPixelsAfterTheCircuitUntilRemoved() {
        List<String> order = new ArrayList<>();
        List<AffineTransform> transforms = new ArrayList<>();
        canvas.addOverlay((graphics, state) -> order.add("circuit overlay"));
        Runnable remover = canvas.addScreenOverlay((screen, pane) -> {
            order.add("screen overlay");
            transforms.add(screen.getTransform());
            assertThat(pane).isSameAs(canvas);
        });
        canvas.viewport().panBy(30, 40);
        canvas.viewport().zoomAt(0, 0, 2);

        paintOnce();

        assertThat(order).containsExactly("circuit overlay", "screen overlay");
        assertThat(transforms.get(0).isIdentity()).isTrue();
        remover.run();
        order.clear();
        paintOnce();
        assertThat(order).containsExactly("circuit overlay");
    }

    @Test
    void theMagnifiedSceneGoesThroughTheSamePathScaledAroundTheCentreWithoutTheZoomClamp() {
        List<AffineTransform> transforms = new ArrayList<>();
        canvas.addOverlay((graphics, state) -> transforms.add(graphics.getTransform()));
        canvas.setSize(300, 200);
        canvas.viewport().panBy(-50, 25);
        canvas.viewport().zoomAt(0, 0, Viewport.MAXIMUM_ZOOM);
        Point2D centre = new Point2D.Double(120, 80);
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paintSceneMagnified(graphics, centre, 3);
        } finally {
            graphics.dispose();
        }

        AffineTransform expected = AffineTransform.getTranslateInstance(120, 80);
        expected.scale(3, 3);
        expected.translate(-120, -80);
        expected.concatenate(canvas.viewport().documentTransform());
        assertThat(transforms).hasSize(1);
        assertThat(transforms.get(0).getScaleX()).isCloseTo(Viewport.MAXIMUM_ZOOM * 3, within(1e-9));
        assertThat(transforms.get(0).getTranslateX()).isCloseTo(expected.getTranslateX(), within(1e-9));
        assertThat(transforms.get(0).getTranslateY()).isCloseTo(expected.getTranslateY(), within(1e-9));
    }

    @Test
    void theMagnifiedSceneDoesNotPaintTheScreenOverlays() {
        List<String> painted = new ArrayList<>();
        canvas.addScreenOverlay((screen, pane) -> painted.add("screen overlay"));
        canvas.setSize(300, 200);
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paintSceneMagnified(graphics, new Point2D.Double(10, 10), 2);
        } finally {
            graphics.dispose();
        }
        assertThat(painted).isEmpty();
    }
}
