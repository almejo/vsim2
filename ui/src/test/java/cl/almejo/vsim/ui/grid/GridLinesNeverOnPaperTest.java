package cl.almejo.vsim.ui.grid;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.print.PageGeometry;
import cl.almejo.vsim.ui.print.PageLayout;
import cl.almejo.vsim.ui.print.PagePainter;
import cl.almejo.vsim.ui.print.PrintArea;
import cl.almejo.vsim.ui.print.PrintInput;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScopes;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The grid of lines is editor chrome: PNG, SVG and a PDF sheet are the same with lines, dots or nothing. */
class GridLinesNeverOnPaperTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorView view;
    private GridFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
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
        features = new GridFeatures(view);
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

    private int[] pdfSheet(boolean blackAndWhite) {
        PrintInput input = PrintInput.capture(openDocument, Set.of(), view.services().appearance().scheme(),
                TextFont.DEFAULT, CLOCK);
        PrintOptions defaults = PrintOptions.defaults("Rejilla");
        PrintOptions options = new PrintOptions(defaults.scope(), defaults.paper(), defaults.orientation(),
                defaults.scaleMode(), true, blackAndWhite, false, defaults.title());
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(), options.headerFooter());
        PagePainter painter = new PagePainter(input, area, options,
                PageLayout.of(area.gridArea(), geometry, options.scaleMode()));
        BufferedImage sheet = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        painter.paint(graphics, 0);
        graphics.dispose();
        return ((DataBufferInt) sheet.getRaster().getDataBuffer()).getData();
    }

    @Test
    void linesDotsAndNothingExportTheSamePngSvgAndPdfSheet() throws IOException {
        preferences.setGridStyle(GridStyle.DOTS);
        byte[] dotsPng = png("dots");
        String dotsSvg = svg("dots");
        int[] dotsSheet = pdfSheet(false);
        int[] dotsMonochromeSheet = pdfSheet(true);

        for (GridStyle style : List.of(GridStyle.LINES, GridStyle.NONE)) {
            preferences.setGridStyle(style);
            assertThat(view.canvas().gridStyle()).isEqualTo(style);
            assertThat(png(style.name())).as(style + " png").isEqualTo(dotsPng);
            assertThat(svg(style.name())).as(style + " svg").isEqualTo(dotsSvg);
            assertThat(pdfSheet(false)).as(style + " sheet").isEqualTo(dotsSheet);
            assertThat(pdfSheet(true)).as(style + " black and white sheet").isEqualTo(dotsMonochromeSheet);
        }
    }
}
