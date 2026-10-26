package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.guides.GuidesFeatures;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guides and rulers are editor chrome (plan, Global Constraints): a circuit with guides shown and a guide being pulled
 * exports the same PNG, SVG, PDF sheets (colour and black and white) and real PDF as the same circuit without guides.
 */
class GuidesNeverOnPaperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorView view;
    private GuidesFeatures features;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component gate = openDocument.registry().require(StandardComponentTypes.AND2)
                .newComponent(openDocument.document().newComponentId(), new GridPoint(4, 4), Rotation.R0, Map.of(),
                        "");
        openDocument.document().add(gate);
        openDocument.document().add(new Wire(openDocument.document().newWireId(), new GridPoint(0, 20),
                new GridPoint(30, 20)));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.canvas().setSize(400, 300);
        features = new GuidesFeatures(view);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private CircuitImage image() {
        return CircuitImage.of(openDocument.document(), openDocument.netlist(), openDocument.netlistToken(),
                openDocument.latestSnapshot(), openDocument.registry(), PainterRegistry.standard(),
                view.services().appearance().scheme(), Optional.empty()).orElseThrow();
    }

    private byte[] png(String name) throws IOException {
        Path target = directory.resolve(name + ".png");
        ImageExport.writePng(target, image(), 1);
        return Files.readAllBytes(target);
    }

    /** The SVG text; the gradient ids are numbered from the clock, so they are left out. */
    private String svg(String name) throws IOException {
        Path target = directory.resolve(name + ".svg");
        ImageExport.writeSvg(target, image());
        return Files.readString(target).replaceAll("_[0-9]+gp", "_gp");
    }

    private PagePainter painter(boolean blackAndWhite) {
        PrintInput input = PrintInput.capture(openDocument, Set.of(), view.services().appearance().scheme(),
                TextFont.DEFAULT, CLOCK);
        PrintOptions defaults = PrintOptions.defaults("Guías");
        PrintOptions options = new PrintOptions(defaults.scope(), defaults.paper(), defaults.orientation(),
                defaults.scaleMode(), true, blackAndWhite, false, defaults.title());
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(), options.headerFooter());
        return new PagePainter(input, area, options, PageLayout.of(area.gridArea(), geometry, options.scaleMode()));
    }

    private int[] sheet(boolean blackAndWhite) {
        BufferedImage sheet = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        painter(blackAndWhite).paint(graphics, 0);
        graphics.dispose();
        return ((DataBufferInt) sheet.getRaster().getDataBuffer()).getData();
    }

    private Path pdf(String name) throws IOException {
        PagePainter painter = painter(false);
        Path file = directory.resolve(name + ".pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, painter.pages().stream().map(PagePainter.Page::size).toList(),
                    (pageIndex, graphics) -> painter.paint(graphics, pageIndex));
        }
        return file;
    }

    @Test
    void guidesShownAndAGuideBeingPulledExportExactlyWhatNoGuidesExport() throws IOException {
        byte[] plainPng = png("plain");
        String plainSvg = svg("plain");
        int[] plainSheet = sheet(false);
        int[] plainMonochromeSheet = sheet(true);
        Path plainPdf = pdf("plain");

        openDocument.document().setGuides(List.of(new Guide(Orientation.HORIZONTAL, 5),
                new Guide(Orientation.VERTICAL, 6), new Guide(Orientation.HORIZONTAL, 20)));
        view.canvas().getMouseListeners()[0].mousePressed(new MouseEvent(view.canvas(), MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, 100, 5, 1, false, MouseEvent.BUTTON1));
        view.canvas().getMouseMotionListeners()[0].mouseDragged(new MouseEvent(view.canvas(),
                MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, 100, 96, 1, false, MouseEvent.NOBUTTON));
        assertThat(view.canvas().isEdgeGestureActive()).isTrue();
        assertThat(preferences.showGuides()).isTrue();

        assertThat(png("guides")).isEqualTo(plainPng);
        assertThat(svg("guides")).isEqualTo(plainSvg);
        assertThat(sheet(false)).isEqualTo(plainSheet);
        assertThat(sheet(true)).isEqualTo(plainMonochromeSheet);
        Path guidesPdf = pdf("guides");
        assertThat(PdfReading.operators(guidesPdf, 0)).isEqualTo(PdfReading.operators(plainPdf, 0));
        BufferedImage plainPage = PdfReading.render(plainPdf, 0, 1);
        BufferedImage guidesPage = PdfReading.render(guidesPdf, 0, 1);
        assertThat(((DataBufferInt) toRgb(guidesPage).getRaster().getDataBuffer()).getData())
                .isEqualTo(((DataBufferInt) toRgb(plainPage).getRaster().getDataBuffer()).getData());
    }

    private static BufferedImage toRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }
}
