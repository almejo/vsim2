package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
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
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.tools.Tool;
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

/**
 * The alignment lines and the contact dots are editor chrome (plan, «Editor chrome, never on paper»): with a snapped
 * drag in progress on the canvas, guides and grid-line snapping on, or a drag showing a new contact, PNG, SVG and a
 * PDF sheet (colour and black and white) are the same as with no drag.
 */
class AlignmentLinesNeverOnPaperTest {

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
    private SnapFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        for (GridPoint position : List.of(new GridPoint(0, 0), new GridPoint(20, 10))) {
            Component gate = openDocument.registry().require(StandardComponentTypes.NOT)
                    .newComponent(openDocument.document().newComponentId(), position, Rotation.R0, Map.of(), "");
            openDocument.document().add(gate);
        }
        // under where the second gate's input lands when it is dragged eight units down
        openDocument.document().add(new Wire(openDocument.document().newWireId(), new GridPoint(20, 15),
                new GridPoint(20, 25)));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.canvas().setSize(400, 300);
        features = new SnapFeatures(view);
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
        PrintOptions defaults = PrintOptions.defaults("Ajustar");
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
    void aSnappedDragInProgressExportsTheSamePngSvgAndPdfSheets() throws IOException {
        preferences.setSnapToAlignments(false);
        byte[] plainPng = png("plain");
        String plainSvg = svg("plain");
        int[] plainSheet = pdfSheet(false);
        int[] plainMonochromeSheet = pdfSheet(true);

        preferences.setSnapToAlignments(true);
        preferences.setSnapToGridLines(true);
        openDocument.document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 21)));
        Tool tool = view.canvas().tool();
        tool.mousePressed(view.canvas(), TestToolContext.at(22, 12));
        tool.mouseDragged(view.canvas(), TestToolContext.at(22, 3)); // its top one unit off the other gate's
        BufferedImage screen = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = screen.createGraphics();
        view.canvas().paint(graphics); // the lines are on screen…
        graphics.dispose();

        assertThat(png("dragging")).isEqualTo(plainPng); // …and never on paper
        assertThat(svg("dragging")).isEqualTo(plainSvg);
        assertThat(pdfSheet(false)).isEqualTo(plainSheet);
        assertThat(pdfSheet(true)).isEqualTo(plainMonochromeSheet);
        assertThat(hasAlignmentColour(screen)).isTrue();
    }

    @Test
    void aDragShowingANewContactExportsTheSamePngSvgAndPdfSheets() throws IOException {
        preferences.setSnapToAlignments(false);
        byte[] plainPng = png("plain");
        String plainSvg = svg("plain");
        int[] plainSheet = pdfSheet(false);
        int[] plainMonochromeSheet = pdfSheet(true);

        Tool tool = view.canvas().tool();
        tool.mousePressed(view.canvas(), TestToolContext.at(22, 12));
        tool.mouseDragged(view.canvas(), TestToolContext.at(22, 20)); // the input pin onto the wire
        BufferedImage screen = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = screen.createGraphics();
        view.canvas().paint(graphics); // the contact dot is on screen…
        graphics.dispose();

        assertThat(png("dragging")).isEqualTo(plainPng); // …and never on paper
        assertThat(svg("dragging")).isEqualTo(plainSvg);
        assertThat(pdfSheet(false)).isEqualTo(plainSheet);
        assertThat(pdfSheet(true)).isEqualTo(plainMonochromeSheet);
        assertThat(hasColour(screen, ColorScheme.LIGHT.wireConflict().getRGB())).isTrue();
    }

    private static boolean hasAlignmentColour(BufferedImage image) {
        return hasColour(image, ColorScheme.LIGHT.alignmentGuide().getRGB());
    }

    private static boolean hasColour(BufferedImage image, int colour) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == colour) {
                    return true;
                }
            }
        }
        return false;
    }
}
