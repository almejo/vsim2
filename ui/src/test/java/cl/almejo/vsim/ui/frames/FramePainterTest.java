package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FramePainterTest {

    private OpenDocument openDocument;

    @AfterEach
    void close() {
        if (openDocument != null) {
            openDocument.close();
        }
    }

    private Frame addFrame(OpenDocument source, int x, int y, int width, int height, String title, FrameTint tint) {
        Frame frame = new Frame(source.document().newFrameId(), new GridPoint(x, y), width, height, title, tint,
                false);
        source.execute(new AddElementsCommand(List.of(frame)));
        return frame;
    }

    private BufferedImage render(OpenDocument source, ColorScheme scheme, double scale, CircuitImage[] holder) {
        openDocument = source;
        CircuitImage image = CircuitImage.of(source.document(), source.netlist(), source.netlistToken(),
                source.latestSnapshot(), source.registry(), PainterRegistry.standard(), scheme, Optional.empty())
                .orElseThrow();
        holder[0] = image;
        BufferedImage result = new BufferedImage(image.width(scale), image.height(scale),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        image.paint(graphics, scale);
        graphics.dispose();
        return result;
    }

    private static int pixelX(CircuitImage image, double gridX) {
        return (int) Math.round((gridX - image.gridBounds().x()) * 8);
    }

    private static int pixelY(CircuitImage image, double gridY) {
        return (int) Math.round((gridY - image.gridBounds().y()) * 8);
    }

    @Test
    void everySchemeHasAColorForEveryTintAndTheTintsAreSoft() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            assertThat(scheme.frameBorder()).isNotNull();
            assertThat(scheme.frameTitle()).isNotNull();
            assertThat(scheme.frameVeil()).isNotNull();
            for (FrameTint tint : FrameTint.values()) {
                Color color = scheme.frameTint(tint);
                assertThat(color).as("%s %s", id, tint).isNotNull();
                if (tint == FrameTint.NONE) {
                    assertThat(color.getAlpha()).isZero();
                } else {
                    assertThat(color.getAlpha()).as("%s %s", id, tint).isBetween(20, 90);
                }
            }
        }
    }

    @Test
    void lightAndDarkSchemesUseDifferentVariantsOfATint() {
        assertThat(ColorScheme.LIGHT.frameTint(FrameTint.BLUE)).isNotEqualTo(ColorSchemes.DARK.frameTint(FrameTint.BLUE));
    }

    @Test
    void aFrameIsPaintedUnderAComponent() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        addFrame(source, 10, 10, 30, 20, "Etapa", FrameTint.BLUE);
        Component led = UiTestSupport.place(source, StandardComponentTypes.LED, 20, 15, Map.of());
        CircuitImage[] holder = new CircuitImage[1];
        BufferedImage pixels = render(source, ColorScheme.LIGHT, 1, holder);
        // inside the frame but away from the component: tint over the background
        Color tinted = new Color(pixels.getRGB(pixelX(holder[0], 35), pixelY(holder[0], 25)), true);
        assertThat(tinted.getRGB()).isNotEqualTo(ColorScheme.LIGHT.background().getRGB());
        assertThat(tinted.getBlue()).isGreaterThan(tinted.getRed());
        // the component keeps its own fill where it overlaps the frame
        cl.almejo.vsim.core.geometry.Bounds ledBounds = source.registry().typeOf(led).bounds(led);
        int centerX = pixelX(holder[0], ledBounds.x() + ledBounds.width() / 2.0);
        int centerY = pixelY(holder[0], ledBounds.y() + ledBounds.height() / 2.0);
        assertThat(pixels.getRGB(centerX, centerY)).isNotEqualTo(pixels.getRGB(pixelX(holder[0], 35), pixelY(holder[0], 25)));
    }

    @Test
    void aNestedFrameTintShowsOverItsParent() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        addFrame(source, 10, 10, 60, 40, "", FrameTint.BLUE);
        addFrame(source, 20, 20, 20, 15, "", FrameTint.RED);
        CircuitImage[] holder = new CircuitImage[1];
        BufferedImage pixels = render(source, ColorScheme.LIGHT, 1, holder);
        Color parentOnly = new Color(pixels.getRGB(pixelX(holder[0], 60), pixelY(holder[0], 40)), true);
        Color nested = new Color(pixels.getRGB(pixelX(holder[0], 30), pixelY(holder[0], 28)), true);
        assertThat(nested.getRed()).isGreaterThan(parentOnly.getRed());
        assertThat(nested.getBlue()).isLessThan(parentOnly.getBlue());
    }

    @Test
    void paintOrderDoesNotDependOnInsertionOrder() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        // the small frame is added first, so it sits below the big one in draw order; the big one must still go first
        addFrame(source, 20, 20, 20, 15, "", FrameTint.RED);
        addFrame(source, 10, 10, 60, 40, "", FrameTint.BLUE);
        CircuitImage[] holder = new CircuitImage[1];
        BufferedImage pixels = render(source, ColorScheme.LIGHT, 1, holder);
        Color parentOnly = new Color(pixels.getRGB(pixelX(holder[0], 60), pixelY(holder[0], 40)), true);
        Color nested = new Color(pixels.getRGB(pixelX(holder[0], 30), pixelY(holder[0], 28)), true);
        assertThat(nested.getRed()).isGreaterThan(parentOnly.getRed());
    }

    @Test
    void theTitleBarIsTheTopTwoGridUnitsOfTheFrame() {
        Frame frame = new Frame(new cl.almejo.vsim.core.document.ElementId("f1"), new GridPoint(10, 20), 30, 20,
                "x", FrameTint.NONE, false);
        Rectangle2D bar = FramePainter.titleBarBounds(frame);
        assertThat(bar.getX()).isEqualTo(80);
        assertThat(bar.getY()).isEqualTo(160);
        assertThat(bar.getWidth()).isEqualTo(240);
        assertThat(bar.getHeight()).isEqualTo(16);
    }

    @Test
    void exportingAFramedExampleHasTheBorderPixelsAndNoControls() throws Exception {
        OpenDocument source = new OpenDocument(BundledExamples.open(BundledExamples.ids().get(0),
                StandardComponentTypes.registry()), StandardComponentTypes.registry(), null, false);
        addFrame(source, -5, -5, 200, 150, "Marco", FrameTint.NONE);
        CircuitImage[] holder = new CircuitImage[1];
        BufferedImage pixels = render(source, ColorScheme.LIGHT, 1, holder);
        Color border = ColorScheme.LIGHT.frameBorder();
        int middleY = pixelY(holder[0], 70);
        boolean found = false;
        for (int offset = -1; offset <= 1; offset++) {
            Color color = new Color(pixels.getRGB(pixelX(holder[0], -5) + offset, middleY), true);
            found |= color.getRed() < 0xE8;
        }
        assertThat(found).as("the left border at %s", border).isTrue();
        // no lock icon or menu button at the right end of the title bar: it looks like the middle of the bar
        int barY = pixelY(holder[0], 2);
        assertThat(pixels.getRGB(pixelX(holder[0], 190), barY)).isEqualTo(pixels.getRGB(pixelX(holder[0], 100), barY));
    }

    @Test
    void aFrameGhostPaintsTheBorderTranslucently() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        openDocument = source;
        cl.almejo.vsim.ui.canvas.CircuitRenderer renderer = new cl.almejo.vsim.ui.canvas.CircuitRenderer(
                source.registry(), PainterRegistry.standard(), ColorScheme.LIGHT);
        BufferedImage pixels = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pixels.createGraphics();
        renderer.paintGhost(graphics, new Frame(new cl.almejo.vsim.core.document.ElementId("g"), new GridPoint(2, 2),
                10, 8, "Fantasma", FrameTint.NONE, false));
        graphics.dispose();
        int alpha = new Color(pixels.getRGB(16, 40), true).getAlpha();
        assertThat(alpha).isBetween(1, 150);
    }
}
