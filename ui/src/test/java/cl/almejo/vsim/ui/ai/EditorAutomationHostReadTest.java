package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.read.CircuitView;
import cl.almejo.vsim.automation.read.ComponentTypeView;
import cl.almejo.vsim.automation.read.ProblemView;
import cl.almejo.vsim.automation.read.ReadFilter;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The read side of the editor's host (IA.1 Task 2) over real open circuits and a view that is never shown. */
class EditorAutomationHostReadTest {

    private final List<OpenDocument> open = new ArrayList<>();
    private final List<ShownWindow> windows = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> windows.stream().filter(window -> window.view().openDocument() == document).toList());
    private final AutomationApi api = new AutomationApi(host);

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> views.forEach(EditorView::dispose));
        open.forEach(OpenDocument::close);
    }

    @Test
    void theCatalogueHasThePalettesNamesInBothLanguages() {
        OpenDocument document = document("Contador");
        DocumentId id = idOf(document);

        ComponentTypeView flipFlop = api.describeComponent(id, StandardComponentTypes.FLIP_FLOP_DATA, Map.of());

        assertThat(flipFlop.spanishName())
                .isEqualTo(Messages.bundle(Messages.SPANISH).getString("component.flip-flop-data"));
        assertThat(flipFlop.englishName()).isEqualTo("D Flip-Flop");
        assertThat(api.describeComponents(id, Optional.empty()).types()).isNotEmpty();
    }

    @Test
    void theCircuitIsReadAsItIsNow() throws Exception {
        OpenDocument document = document("Contador");
        Component flipFlop = onEditor(() -> place(document, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Map.of()));

        CircuitView view = api.readCircuit(idOf(document), ReadFilter.FULL, Optional.empty());

        assertThat(view.name()).isEqualTo("Contador");
        assertThat(view.version().value()).isEqualTo(document.contentVersion());
        assertThat(view.parts()).singleElement().satisfies(part -> assertThat(part.pins())
                .extracting(pin -> pin.pin()).contains(new PinReference(flipFlop.id().value(), "q", 0)));
    }

    @Test
    void aShortCircuitShowsUpInTheProblemsWithItsPins() throws Exception {
        OpenDocument document = document("Corto");
        Component high = onEditor(() -> place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1")));
        Component low = onEditor(() -> {
            Component component = document.registry().require(StandardComponentTypes.SWITCH).newComponent(
                    document.document().newComponentId(), new GridPoint(8, 2), Rotation.R180, Map.of("value", "0"),
                    "");
            document.execute(new AddElementsCommand(List.of(component)));
            return component;
        });
        DocumentId id = idOf(document);

        UiTestSupport.waitUntil(() -> api.readProblems(id).problems().stream()
                .anyMatch(problem -> problem.kind().equals("SHORT_CIRCUIT")));

        ProblemView problem = api.readProblems(id).problems().stream()
                .filter(found -> found.kind().equals("SHORT_CIRCUIT")).findFirst().orElseThrow();
        assertThat(problem.kind()).isEqualTo("SHORT_CIRCUIT");
        assertThat(problem.pins()).containsExactlyInAnyOrder(new PinReference(high.id().value(), "out", 0),
                new PinReference(low.id().value(), "out", 0));
    }

    @Test
    void aClosedCircuitAndAnIdNeverGivenAreToldApart() {
        OpenDocument document = document("Sumador");
        DocumentId id = idOf(document);
        open.remove(document);
        document.close();

        assertThatThrownBy(() -> api.readCircuit(id, ReadFilter.SUMMARY, Optional.empty()))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
        assertThatThrownBy(() -> api.readProblems(new DocumentId("circuit-99")))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void readingAndPicturesNeverChangeTheSelectionTheViewOrTheCircuit() throws Exception {
        OpenDocument document = document("Contador");
        Component flipFlop = onEditor(() -> place(document, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Map.of()));
        EditorView view = view(document);
        onEditor(() -> {
            view.selection().select(flipFlop.id());
            return null;
        });
        long changes = document.changeCount();
        Set<ElementId> selected = onEditor(() -> new HashSet<>(view.selection().ids()));
        String viewport = onEditor(() -> view.canvas().viewState().toString());
        DocumentId id = idOf(document);

        api.readCircuit(id, ReadFilter.FULL, Optional.empty());
        api.describeComponents(id, Optional.empty());
        api.readProblems(id);
        api.pictureOf(id, Optional.empty(), false);
        api.pictureOf(id, Optional.empty(), true);
        api.pictureOfWindow(id);

        assertThat(document.changeCount()).isEqualTo(changes);
        assertThat(onEditor(() -> new HashSet<>(view.selection().ids()))).isEqualTo(selected);
        assertThat(onEditor(() -> view.canvas().viewState().toString())).isEqualTo(viewport);
    }

    @Test
    void theExportPictureIsCappedAndSmallRegionsArePaintedBigger() throws Exception {
        OpenDocument document = document("Contador");
        onEditor(() -> place(document, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Map.of()));
        DocumentId id = idOf(document);

        RegionPicture whole = api.pictureOf(id, Optional.empty(), false);
        RegionPicture huge = api.pictureOf(id, Optional.of(new Bounds(-1000, -1000, 5000, 2000)), false);
        RegionPicture small = api.pictureOf(id, Optional.of(new Bounds(8, 8, 10, 10)), false);

        assertThat(whole.asOnScreen()).isFalse();
        assertThat(whole.region()).isEqualTo(new Bounds(8, 8, 8, 8));
        assertThat(huge.width()).isLessThanOrEqualTo(RegionPicture.MAXIMUM_WIDTH);
        assertThat(huge.height()).isLessThanOrEqualTo(RegionPicture.MAXIMUM_HEIGHT);
        assertThat(small.width()).isEqualTo(160);
        assertThat(small.height()).isEqualTo(160);
        for (RegionPicture picture : List.of(whole, huge, small)) {
            BufferedImage image = decode(picture.png());
            assertThat(image.getWidth()).isEqualTo(picture.width());
            assertThat(image.getHeight()).isEqualTo(picture.height());
        }
        assertThat(colours(decode(small.png()))).isGreaterThan(1);
    }

    @Test
    void anEmptyCircuitHasNoWholePictureButARegionOfItHasOne() {
        OpenDocument document = document("Vacío");
        DocumentId id = idOf(document);

        assertThatThrownBy(() -> api.pictureOf(id, Optional.empty(), false))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThat(api.pictureOf(id, Optional.of(new Bounds(0, 0, 20, 10)), false).width()).isEqualTo(320);
    }

    @Test
    void theOnScreenPictureIsTheActivePaneAsTheUserSeesIt() throws Exception {
        OpenDocument document = document("Contador");
        onEditor(() -> place(document, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Map.of()));
        view(document);
        DocumentId id = idOf(document);

        RegionPicture pane = api.pictureOf(id, Optional.empty(), true);
        RegionPicture part = api.pictureOf(id, Optional.of(new Bounds(0, 0, 10, 10)), true);

        assertThat(pane.asOnScreen()).isTrue();
        assertThat(pane.width()).isEqualTo(400);
        assertThat(pane.height()).isEqualTo(300);
        assertThat(colours(decode(pane.png()))).isGreaterThan(1);
        assertThat(part.width()).isLessThan(pane.width());
        assertThatThrownBy(() -> api.pictureOf(id, Optional.of(new Bounds(100_000, 100_000, 5, 5)), true))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE);
                    assertThat(exception.recovery()).containsKey("visibleRegion");
                });
    }

    @Test
    void aCircuitWithoutAWindowHasNoOnScreenNorWindowPicture() {
        OpenDocument document = document("Contador");
        DocumentId id = idOf(document);

        assertThatThrownBy(() -> api.pictureOf(id, Optional.of(new Bounds(0, 0, 5, 5)), true))
                .isInstanceOf(AutomationException.class);
        assertThatThrownBy(() -> api.pictureOfWindow(id)).isInstanceOf(AutomationException.class);
    }

    @Test
    void theWindowPictureHasTheWholeWindowScaledToFit() throws Exception {
        OpenDocument document = document("Contador");
        onEditor(() -> place(document, StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Map.of()));
        EditorView view = view(document);
        AtomicReference<JRootPane> root = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JRootPane pane = new JRootPane();
            pane.setJMenuBar(view.menuBar());
            pane.setContentPane(view);
            pane.setSize(2400, 1500);
            layOut(pane);
            root.set(pane);
        });
        windows.clear();
        windows.add(new ShownWindow(view, root.get(), "Contador - VSim", List.of()));

        WindowPicture picture = api.pictureOfWindow(idOf(document));

        assertThat(picture.width()).isEqualTo(WindowPicture.MAXIMUM_WIDTH);
        assertThat(picture.height()).isEqualTo(WindowPicture.MAXIMUM_HEIGHT);
        assertThat(picture.windowTitle()).isEqualTo("Contador - VSim");
        assertThat(picture.dialogs()).isEmpty();
        BufferedImage image = decode(picture.png());
        assertThat(image.getWidth()).isEqualTo(picture.width());
        assertThat(colours(image)).isGreaterThan(10);
    }

    private OpenDocument document(String name) {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName(name);
        OpenDocument document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        open.add(document);
        return document;
    }

    /** A view of {@code document} that is never shown, its pane 400 × 300, as the circuit's window. */
    private EditorView view(OpenDocument document) throws Exception {
        AtomicReference<EditorView> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of());
            EditorView view = new EditorView(document, services, NO_WINDOW);
            view.canvas().setSize(400, 300);
            view.setSize(800, 600);
            created.set(view);
        });
        views.add(created.get());
        windows.add(new ShownWindow(created.get(), created.get(), "", List.of()));
        return created.get();
    }

    private DocumentId idOf(OpenDocument document) {
        int index = open.indexOf(document);
        return api.listDocuments().get(index).id();
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static int colours(BufferedImage image) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                colours.add(image.getRGB(x, y));
            }
        }
        return colours.size();
    }
}
