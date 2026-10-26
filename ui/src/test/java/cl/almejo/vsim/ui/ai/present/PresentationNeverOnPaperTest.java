package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The callout, the ghosts, the glow and the AI's subtitle of «Presentar» are editor chrome (spec §5): with a step
 * proposed (a ghost, a part dimmed, the callout), one applied (the glow) and a line said, PNG, SVG and a PDF sheet
 * (colour and black and white) are the same as with nothing shown.
 */
class PresentationNeverOnPaperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final EditorAutomationHost host = new EditorAutomationHost(List::of);
    private final List<PresentationListener> listeners = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;
    private PresentationOverlays overlays;

    @BeforeEach
    void create() {
        preferences.setAnimateViewMoves(false);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.canvas().setSize(400, 300);
        view.canvas().applyViewState(new ViewState(1, 0, 0));
        overlays = PresentationOverlays.install(view,
                listener -> {
                    listeners.add(listener);
                    return () -> listeners.remove(listener);
                },
                listener -> () -> { }, host::documentId, Runnable::run, System::nanoTime);
    }

    @AfterEach
    void close() {
        overlays.dispose();
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
    void aProposedAndAnAppliedStepExportTheSamePngSvgAndPdfSheets() throws IOException {
        Component kept = place(openDocument, StandardComponentTypes.NOT, -10, -5, Map.of());
        Component doomed = place(openDocument, StandardComponentTypes.NOT, -10, 5, Map.of());
        byte[] plainPng = png("plain");
        String plainSvg = svg("plain");
        int[] plainSheet = pdfSheet(false);
        int[] plainMonochromeSheet = pdfSheet(true);

        PresentationListener presentation = listeners.getFirst();
        presentation.planChanged(new TaskInfo("task-1", host.documentId(openDocument), "Sumador"),
                List.of(new TaskStage("Medio sumador", List.of("Pongo la AND", "Quito la NOT"))), 0, 0);
        presentation.stepApplied(new StepPreview("task-1", 0, 0, "Pongo la NOT", Optional.empty(), List.of(kept),
                Set.of(), Optional.of(DrawnBounds.of(kept, openDocument.registry()))));
        Component ghost = openDocument.registry().require(StandardComponentTypes.AND2)
                .newComponent(openDocument.document().newComponentId(), new GridPoint(5, 0), Rotation.R0, Map.of(),
                        "");
        presentation.stepProposed(new StepPreview("task-1", 0, 1, "Pongo la AND", Optional.of("para sumar"),
                List.of(ghost), Set.of(doomed.id()), Optional.empty()));
        preferences.setAiPresenting(true);
        presentation.narrated(new Narration("task-1", 0, "Ahora pongo la AND para sumar", true));
        assertThat(overlays.callout().content()).isPresent();
        assertThat(overlays.ghosts().shown()).isPresent();
        assertThat(overlays.glow().current()).isPresent();
        assertThat(overlays.caption().text()).isPresent(); // the subtitle (IA.3) is chrome too
        BufferedImage screen = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = screen.createGraphics();
        view.canvas().paint(graphics); // on screen…
        graphics.dispose();

        assertThat(png("presenting")).isEqualTo(plainPng); // …and never on paper
        assertThat(svg("presenting")).isEqualTo(plainSvg);
        assertThat(pdfSheet(false)).isEqualTo(plainSheet);
        assertThat(pdfSheet(true)).isEqualTo(plainMonochromeSheet);
        assertThat(hasColour(screen, ColorScheme.LIGHT.selection().getRGB())).isTrue();
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
