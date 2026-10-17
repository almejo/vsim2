package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitImageTest {

    private OpenDocument openDocument;

    @AfterEach
    void close() {
        TextFonts.set(TextFont.DEFAULT);
        if (openDocument != null) {
            openDocument.close();
        }
    }

    private Optional<CircuitImage> imageOf(OpenDocument source, ColorScheme scheme, Optional<Set<ElementId>> only) {
        openDocument = source;
        return CircuitImage.of(source.document(), source.netlist(), source.netlistToken(), source.latestSnapshot(),
                source.registry(), PainterRegistry.standard(), scheme, only);
    }

    private static BufferedImage render(CircuitImage image, double scale) {
        BufferedImage result = new BufferedImage(image.width(scale), image.height(scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        image.paint(graphics, scale);
        graphics.dispose();
        return result;
    }

    @Test
    void anEmptyDocumentHasNothingToPaint() {
        assertThat(imageOf(OpenDocument.createEmpty(StandardComponentTypes.registry()), ColorScheme.LIGHT,
                Optional.empty())).isEmpty();
    }

    @Test
    void theBoundsAreTheCircuitBoundsPlusTwoUnitsOnEverySide() throws Exception {
        OpenDocument source = new OpenDocument(BundledExamples.open(BundledExamples.ids().get(0),
                StandardComponentTypes.registry()), StandardComponentTypes.registry(), null, false);
        Bounds circuit = CircuitBounds.of(source.document(), source.registry()).orElseThrow();
        CircuitImage image = imageOf(source, ColorScheme.LIGHT, Optional.empty()).orElseThrow();
        assertThat(image.gridBounds()).isEqualTo(
                new Bounds(circuit.x() - 2, circuit.y() - 2, circuit.width() + 4, circuit.height() + 4));
    }

    @Test
    void theSizeInPixelsIsTheBoundsTimesEightTimesTheScale() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        source.execute(new cl.almejo.vsim.core.command.AddElementsCommand(java.util.List.of(
                new Wire(source.document().newWireId(), new GridPoint(10, 10), new GridPoint(20, 10)))));
        CircuitImage image = imageOf(source, ColorScheme.LIGHT, Optional.empty()).orElseThrow();
        assertThat(image.gridBounds()).isEqualTo(new Bounds(8, 8, 14, 4));
        assertThat(image.width(1)).isEqualTo(14 * 8);
        assertThat(image.height(1)).isEqualTo(4 * 8);
        assertThat(image.width(2)).isEqualTo(14 * 16);
        assertThat(image.height(2)).isEqualTo(4 * 16);
    }

    @Test
    void theCornerTakesTheSchemeBackground() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        source.execute(new cl.almejo.vsim.core.command.AddElementsCommand(java.util.List.of(
                new Wire(source.document().newWireId(), new GridPoint(10, 10), new GridPoint(20, 10)))));
        BufferedImage image = render(imageOf(source, CircuitImage.printScheme(ColorScheme.LIGHT), Optional.empty())
                .orElseThrow(), 1);
        assertThat(image.getRGB(0, 0)).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void aSelectionHasItsOwnBoundsAndPaintsNothingElse() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component first = UiTestSupport.place(source, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Component second = UiTestSupport.place(source, StandardComponentTypes.LED, 60, 10, Map.of());
        CircuitImage selected = imageOf(source, ColorScheme.LIGHT, Optional.of(Set.of(second.id()))).orElseThrow();
        CircuitImage all = CircuitImage.of(source.document(), source.netlist(), source.netlistToken(),
                source.latestSnapshot(), source.registry(), PainterRegistry.standard(), ColorScheme.LIGHT,
                Optional.empty()).orElseThrow();
        assertThat(selected.gridBounds().x()).isGreaterThanOrEqualTo(60 - 2 - 2);
        assertThat(selected.width(1)).isLessThan(all.width(1));
        // the first component sits outside the selected bounds, so every pixel is the background or the LED's;
        // painting the whole circuit into the selection's pixels would show the switch at the same grid spot
        BufferedImage selectedPixels = render(selected, 1);
        BufferedImage fullPixels = render(all, 1);
        int offsetX = (selected.gridBounds().x() - all.gridBounds().x()) * 8;
        int offsetY = (selected.gridBounds().y() - all.gridBounds().y()) * 8;
        for (int y = 0; y < selectedPixels.getHeight(); y++) {
            for (int x = 0; x < selectedPixels.getWidth(); x++) {
                assertThat(selectedPixels.getRGB(x, y)).isEqualTo(fullPixels.getRGB(x + offsetX, y + offsetY));
            }
        }
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void aProblemWireIsExportedWithoutTheGlowBesideIt() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        // an LED pin (1 bit) and a 4-bit splitter pin on the same wire: a width mismatch, which the canvas glows
        UiTestSupport.place(source, StandardComponentTypes.LED, 10, 7, Map.of()); // its pin is at (11, 10)
        UiTestSupport.place(source, StandardComponentTypes.BUS_SPLITTER, 15, 10,
                Map.of(StandardComponentTypes.WIDTH, 4));
        source.execute(new cl.almejo.vsim.core.command.AddElementsCommand(java.util.List.of(
                new Wire(source.document().newWireId(), new GridPoint(11, 10), new GridPoint(15, 10)))));
        CircuitImage image = imageOf(source, ColorScheme.LIGHT, Optional.empty()).orElseThrow();
        BufferedImage pixels = render(image, 1);
        int besideX = (13 - image.gridBounds().x()) * 8;
        int besideY = (10 - image.gridBounds().y()) * 8 + 4;
        assertThat(pixels.getRGB(besideX, besideY)).isEqualTo(ColorScheme.LIGHT.background().getRGB());
    }

    @Test
    void anUnselectedComponentInsideTheSelectionBoundsStaysBackground() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component left = UiTestSupport.place(source, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Component middle = UiTestSupport.place(source, StandardComponentTypes.LED, 30, 10, Map.of());
        Component right = UiTestSupport.place(source, StandardComponentTypes.SWITCH, 50, 10, Map.of());
        CircuitImage image = imageOf(source, ColorScheme.LIGHT, Optional.of(Set.of(left.id(), right.id())))
                .orElseThrow();
        BufferedImage pixels = render(image, 1);
        int middleX = (middle.position().x() + 1 - image.gridBounds().x()) * 8;
        int middleY = (middle.position().y() - image.gridBounds().y()) * 8;
        assertThat(pixels.getRGB(middleX, middleY)).isEqualTo(ColorScheme.LIGHT.background().getRGB());
    }

    @Test
    void theExportMeasuresAndPaintsTextsWithTheCurrentFont() {
        OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry());
        source.execute(new cl.almejo.vsim.core.command.AddElementsCommand(java.util.List.of(
                new cl.almejo.vsim.core.document.TextLabel(source.document().newLabelId(), new GridPoint(10, 10),
                        "una nota bastante larga"))));
        CircuitImage small = imageOf(source, ColorScheme.LIGHT, Optional.empty()).orElseThrow();

        TextFonts.set(new TextFont(java.awt.Font.SANS_SERIF, 24));
        CircuitImage large = imageOf(source, ColorScheme.LIGHT, Optional.empty()).orElseThrow();

        assertThat(large.gridBounds().width()).isGreaterThan(small.gridBounds().width());
        assertThat(large.gridBounds().height()).isGreaterThan(small.gridBounds().height());
        BufferedImage pixels = render(large, 1);
        int background = pixels.getRGB(0, 0);
        int textEndsAtSmallSize = small.width(1) - 2 * 8;
        boolean paintedPastTheSmallText = false;
        for (int x = textEndsAtSmallSize; x < pixels.getWidth() && !paintedPastTheSmallText; x++) {
            for (int y = 0; y < pixels.getHeight(); y++) {
                if (pixels.getRGB(x, y) != background) {
                    paintedPastTheSmallText = true;
                    break;
                }
            }
        }
        assertThat(paintedPastTheSmallText).isTrue();
    }
}
