package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.LayoutCheck;
import cl.almejo.vsim.automation.check.StageCheck;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.read.ComponentTypeView;
import cl.almejo.vsim.automation.simulate.StepKind;
import cl.almejo.vsim.core.bundled.BundledExample;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The editor's host behind the IA.1 tools: the user's library, ids, the layout check and the export. */
class EditorAutomationHostToolsTest {

    @TempDir
    Path exportFolder;

    private final List<OpenDocument> open = new ArrayList<>();
    private final List<ShownWindow> windows = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final List<DefinitionBundle> library = new ArrayList<>();
    private final EditorWindows editorWindows = new EditorWindows() {
        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException("no new windows in this test");
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return Optional.empty();
        }

        @Override
        public List<DefinitionBundle> libraryComponents() {
            return List.copyOf(library);
        }
    };
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> windows.stream().filter(window -> window.view().openDocument() == document).toList(),
            editorWindows, new PreferenceProposals(AppPreferences.inMemory()));
    private final AutomationApi api = new AutomationApi(host);

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> views.forEach(EditorView::dispose));
        open.forEach(OpenDocument::close);
    }

    @Test
    void theLibrarysComponentsAreInTheCatalogueAndCanBePlaced() {
        library.add(new DefinitionBundle(ExampleDefinitions.doubleInverter(), List.of(ExampleDefinitions.inverter())));
        OpenDocument document = document("Prueba");
        DocumentId id = idOf(document);

        assertThat(api.describeComponents(id, Optional.of("Doble inversor")).types()).singleElement()
                .satisfies(type -> assertThat(type.origin()).isEqualTo(ComponentTypeView.Origin.LIBRARY));
        EditReceipt receipt = api.editStep(new EditRequest(id, new DocumentVersion(document.contentVersion()),
                "place-1",
                Optional.empty(), List.of(new EditOperation.PlaceInstance(Optional.of("twice"), "Doble inversor", 0,
                0, Rotation.R0)), false), Optional.empty()).receipt();

        assertThat(receipt.applied()).isTrue();
        assertThat(document.document().definition(ExampleDefinitions.DOUBLE_INVERTER)).isPresent();
        assertThat(document.document().definition(ExampleDefinitions.INVERTER)).isPresent();
        assertThat(api.describeComponents(id, Optional.of("Doble inversor")).types()).singleElement()
                .satisfies(type -> assertThat(type.origin()).isEqualTo(ComponentTypeView.Origin.CIRCUIT));
        assertThat(document.document().components()).singleElement().satisfies(component ->
                assertThat(component.typeId()).isEqualTo(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER)));
    }

    @Test
    void anIdVsimNeverGaveIsAnInvalidReferenceForEditsAndTheSimulationToo() {
        document("Prueba");
        DocumentId never = new DocumentId("circuit-77");

        assertThatThrownBy(() -> api.editStep(new EditRequest(never, new DocumentVersion(0), "x", Optional.empty(),
                List.of(new EditOperation.AddGuide(cl.almejo.vsim.core.document.Guide.Orientation.VERTICAL, 4)),
                false), Optional.empty()).receipt()).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThatThrownBy(() -> api.step(never, StepKind.NEXT_EVENT, 1, 0))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void aClosedCircuitIsClosedForEditsAndTheSimulation() {
        OpenDocument document = document("Prueba");
        DocumentId id = idOf(document);
        open.remove(document);
        document.close();

        assertThatThrownBy(() -> api.step(id, StepKind.NEXT_EVENT, 1, 0))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
    }

    @Test
    void theLayoutCheckMeasuresTextsWithTheEditorsFont() throws Exception {
        OpenDocument document = document("Prueba");
        onEditor(() -> {
            cl.almejo.vsim.ui.UiTestSupport.place(document, StandardComponentTypes.NAND2, 0, 0, Map.of());
            document.execute(new AddElementsCommand(List.of(new TextLabel(document.document().newLabelId(),
                    new GridPoint(1, 1), "Prueba: una nota encima de la compuerta"))));
            return null;
        });

        StageCheck.Result result = api.checkStage(new StageCheck.Request(idOf(document), Optional.empty(),
                Optional.empty(), List.of(new StageCheck.Check.Layout(Set.of()))));

        assertThat(result.passed()).isFalse();
        assertThat(result.checks().getFirst().details().get("faults").toString()).contains("TEXT_OVER_PART");
    }

    /** IA.3: the AI's layout check measures a note of several lines whole, as the canvas draws it. */
    @Test
    void theLayoutCheckSeesEveryLineOfANote() throws Exception {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document = new CircuitDocument();
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(0, 0), "WWWWWWWWWW");
        document.add(note);
        double oneLine = onEditor(() -> LabelTextMeasure.INSTANCE.freeText(note).height());
        cl.almejo.vsim.core.component.ComponentType and = registry.require(StandardComponentTypes.AND2);
        int bodyOffset = and.bounds(and.newComponent(document.newComponentId(), new GridPoint(0, 0), Rotation.R0,
                Map.of(), "")).y();
        // the gate's body starts just under the first line
        document.add(and.newComponent(document.newComponentId(),
                new GridPoint(0, (int) Math.ceil(oneLine) - bodyOffset), Rotation.R0, Map.of(), ""));
        assertThat(onEditor(() -> LayoutCheck.faults(document, registry, LabelTextMeasure.INSTANCE, Set.of())))
                .isEmpty();

        document.replace(note.withText("WWWWWWWWWW\nWWWWWWWWWW\nWWWWWWWWWW"));
        double threeLines = onEditor(() -> LabelTextMeasure.INSTANCE.freeText(
                document.label(note.id()).orElseThrow()).height());
        assertThat(threeLines).isGreaterThan(2.5 * oneLine);
        assertThat(onEditor(() -> LayoutCheck.faults(document, registry, LabelTextMeasure.INSTANCE, Set.of())))
                .extracting(LayoutCheck.Fault::kind).containsExactly(LayoutCheck.FaultKind.TEXT_OVER_PART);
    }

    @Test
    void noBundledExampleHasALayoutFault() throws Exception {
        ComponentTypeRegistry base = StandardComponentTypes.registry();
        for (BundledExample example : BundledExamples.all()) {
            CircuitDocument document = BundledExamples.open(example.id(), base);
            ComponentTypeRegistry registry = base.extendedWith(SubcircuitTypes.resolverFor(document));
            List<LayoutCheck.Fault> faults = onEditor(() -> LayoutCheck.faults(document, registry,
                    LabelTextMeasure.INSTANCE, Set.of()));
            assertThat(faults).as(example.id()).isEmpty();
        }
    }

    @Test
    void anExportThroughTheHostGoesToTheWindowsExportFolder() throws Exception {
        OpenDocument document = document("Contador");
        onEditor(() -> cl.almejo.vsim.ui.UiTestSupport.place(document, StandardComponentTypes.NAND2, 0, 0, Map.of()));
        EditorView view = view(document);
        view.services().preferences().setExportDirectory(exportFolder);

        ExportReceipt receipt = api.export(new ExportRequest(idOf(document), ExportRequest.Format.PNG,
                new ExportRequest.Scope.WholeCircuit(), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                ExportRequest.PdfOptions.DEFAULT));

        assertThat(Path.of(receipt.path())).exists().hasParent(exportFolder.toAbsolutePath());
        assertThat(Files.size(Path.of(receipt.path()))).isPositive();
        assertThat(receipt.version().value()).isEqualTo(document.contentVersion());
    }

    private OpenDocument document(String name) {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName(name);
        OpenDocument document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        open.add(document);
        return document;
    }

    private EditorView view(OpenDocument document) throws Exception {
        AtomicReference<EditorView> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of());
            created.set(new EditorView(document, services, NO_WINDOW));
        });
        views.add(created.get());
        windows.add(new ShownWindow(created.get(), created.get(), "", List.of()));
        return created.get();
    }

    private DocumentId idOf(OpenDocument document) {
        return api.listDocuments().get(open.indexOf(document)).id();
    }
}
