package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetInstanceMemoryContentsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.library.ComponentForm;
import cl.almejo.vsim.ui.library.LibraryDialogs;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.TemplateFeatures;
import cl.almejo.vsim.ui.memory.LinkedMemoryWatcher;
import cl.almejo.vsim.ui.memory.MemoryEditorExtension;
import cl.almejo.vsim.ui.memory.MemoryEditorLauncher;
import cl.almejo.vsim.ui.memory.MemoryFileImport;
import cl.almejo.vsim.ui.memory.MemoryLinkPrompts;
import cl.almejo.vsim.ui.memory.MemoryLinks;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory links end to end, as the «Enlaces» frame of the user's test bank (hand-test checklist section L), so nobody
 * tries them by hand again: a top-level RAM linked to a .hex feeding a NOT; «Memoria enlazada», a component whose RAM
 * is linked to the same .hex inside, used twice, the second piece with its own contents (they win over the file:
 * Problemas lists it, OVER_LINK); «Memoria chica», whose piece keeps contents for a memory no longer in it
 * (FOR_NOTHING). Everything goes through what the editor uses: «Enlazar…» of {@link MemoryLinks}, commands, the rows
 * and actions of Problemas, «Crear componente desde selección», save and open, the linked-file watcher.
 */
class MemoryLinksScenarioTest {

    private static final String LINKED = "memoria-enlazada";
    private static final String SMALL = "memoria-chica";
    private static final ElementId INNER_RAM = new ElementId("c1");
    private static final ElementId SMALL_RAM = new ElementId("c10");
    private static final String OVER_LINK_TEXT = ": usa su propio contenido, no el archivo enlazado";
    private static final String FOR_NOTHING_TEXT = "guarda contenido propio de una memoria que ya no está en el componente";
    private static final MemoryContents OWN_CONTENTS = MemoryContents.decode("@0 AA BB CC DD");

    @TempDir
    Path folder;

    private final List<Runnable> closers = new ArrayList<>();
    private final ScenarioPrompts prompts = new ScenarioPrompts();
    private final ScenarioDialogs dialogs = new ScenarioDialogs();
    private final MemoryLinks links = new MemoryLinks(prompts, (file, command) -> true,
            MemoryLinks.PollScheduler.MANUAL);
    private EditorServices services;
    private Path hexFile;
    private OpenDocument circuit;
    private EditorView main;
    private Component topRam;
    private Component inverter;
    private Component addressConstant;
    private Component chipSelectConstant;
    private Component firstPiece;
    private Component secondPiece;
    private Component smallPiece;

    @BeforeEach
    void buildTheBank() throws IOException {
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(folder.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, List.of(new MemoryEditorExtension(new MemoryEditorLauncher(links), links)));
        LibraryService libraryService = new LibraryService(preferences, library, StandardComponentTypes.registry(),
                Runnable::run);
        closers.add(libraryService::close);
        hexFile = folder.resolve("programa.hex");
        Files.writeString(hexFile, "11 22 33 44\n");
        circuit = track(OpenDocument.createEmpty(StandardComponentTypes.registry()));
        main = view(circuit);
        TemplateFeatures templates = TemplateFeatures.install(main, libraryService, dialogs);
        closers.addFirst(templates::dispose);

        buildTheLinkedRamAndItsInverter();
        buildTheLinkedComponentUsedTwice();
        buildTheSmallComponentWithAnOrphanEntry();
    }

    @AfterEach
    void closeAll() {
        closers.forEach(Runnable::run);
    }

    /** RAM (16 × 8) at address 1 (a 4-bit constant on A, CS at 1), its Q straight into an 8-bit NOT. */
    private void buildTheLinkedRamAndItsInverter() {
        topRam = add(circuit, StandardComponentTypes.RAM, new GridPoint(20, 20), ram());
        GridPoint address = pinOf(circuit, topRam, "A");
        GridPoint chipSelect = pinOf(circuit, topRam, "CS");
        GridPoint output = pinOf(circuit, topRam, "Q");
        addressConstant = touching(StandardComponentTypes.CONSTANT, Map.of(StandardComponentTypes.WIDTH, 4,
                StandardComponentTypes.VALUE, "1"), "out", address);
        chipSelectConstant = touching(StandardComponentTypes.CONSTANT, Map.of(StandardComponentTypes.WIDTH, 1,
                StandardComponentTypes.VALUE, "1"), "out", chipSelect);
        inverter = touching(StandardComponentTypes.BUFFER, Map.of(StandardComponentTypes.WIDTH, 8,
                StandardComponentTypes.NEGATE, true), "in", output);
        prompts.linkFile = Optional.of(hexFile);
        links.link(main, topRam.id());
    }

    /** Two pieces of «Memoria enlazada»; the second gets its own words, then the RAM inside is linked from its tab. */
    private void buildTheLinkedComponentUsedTwice() {
        Component innerRam = StandardComponentTypes.registry().require(StandardComponentTypes.RAM).newComponent(
                INNER_RAM, new GridPoint(0, 0), Rotation.R0, ram(), "");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(LINKED, "Memoria enlazada", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(innerRam), List.of(), List.of())))));
        firstPiece = add(circuit, SubcircuitTypes.typeIdOf(LINKED), new GridPoint(60, 0), Map.of());
        secondPiece = add(circuit, SubcircuitTypes.typeIdOf(LINKED), new GridPoint(60, 20), Map.of());
        circuit.execute(SetInstanceMemoryContentsCommand.set(new PieceMemories.Place(secondPiece.id(),
                INNER_RAM.value()), OWN_CONTENTS));
        OpenDocument tab = body(circuit, LINKED, firstPiece);
        EditorView tabView = new EditorView(tab, services, NO_WINDOW);
        prompts.linkFile = Optional.of(hexFile);
        links.link(tabView, INNER_RAM);
        tabView.dispose();
        tab.close();
    }

    /** «Memoria chica»: its piece keeps words for its RAM, then the RAM is deleted from its tab (an orphan entry). */
    private void buildTheSmallComponentWithAnOrphanEntry() {
        Component smallRam = StandardComponentTypes.registry().require(StandardComponentTypes.RAM).newComponent(
                SMALL_RAM, new GridPoint(0, 0), Rotation.R0, ram(), "");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(SMALL, "Memoria chica", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(smallRam), List.of(), List.of())))));
        smallPiece = add(circuit, SubcircuitTypes.typeIdOf(SMALL), new GridPoint(90, 0), Map.of());
        circuit.execute(SetInstanceMemoryContentsCommand.set(new PieceMemories.Place(smallPiece.id(),
                SMALL_RAM.value()), MemoryContents.decode("@0 1")));
        OpenDocument tab = body(circuit, SMALL, smallPiece);
        tab.execute(new RemoveElementsCommand(List.of(SMALL_RAM)));
        tab.close();
    }

    @Test
    void theWordsAreReadThroughTheLinksAndThePieceWithItsOwnWordsReadsThem() throws Exception {
        assertThat(wordAt(circuit, topRam.id(), 1)).isEqualTo(0x22);
        waitUntil(() -> inverterOutput().equals(Value.of(8, 0xDD)));
        assertThat(pieceWordAt(firstPiece, 1)).isEqualTo(0x22);
        assertThat(pieceWordAt(secondPiece, 1)).isEqualTo(0xBB);
    }

    @Test
    void problemasListsTheOwnWordsOverTheLinkAndTheOrphanEntry() {
        assertThat(ownContentsRows(main)).hasSize(2);
        assertThat(rowIndex(main, OVER_LINK_TEXT)).isNotNegative();
        assertThat(rowIndex(main, FOR_NOTHING_TEXT)).isNotNegative();
    }

    @Test
    void volverAlDelComponenteFromProblemasReadsTheFileAndOneUndoBringsBothBack() throws Exception {
        Action backToComponent = onlyAction(main, OVER_LINK_TEXT, "Volver al del componente");

        backToComponent.actionPerformed(null);

        assertThat(pieceWordAt(secondPiece, 1)).isEqualTo(0x22);
        assertThat(rowIndex(main, OVER_LINK_TEXT)).isNegative();
        circuit.history().undo();
        assertThat(pieceWordAt(secondPiece, 1)).isEqualTo(0xBB);
        assertThat(rowIndex(main, OVER_LINK_TEXT)).isNotNegative();
    }

    @Test
    void olvidarEseContenidoFromProblemasRemovesTheEntryAndOneUndoBringsItBack() {
        Action forget = onlyAction(main, FOR_NOTHING_TEXT, "Olvidar ese contenido");

        forget.actionPerformed(null);

        assertThat(circuit.document().component(smallPiece.id()).orElseThrow().memoryContents()).isEmpty();
        assertThat(rowIndex(main, FOR_NOTHING_TEXT)).isNegative();
        circuit.history().undo();
        assertThat(circuit.document().component(smallPiece.id()).orElseThrow().memoryContents())
                .containsKey(SMALL_RAM.value());
        assertThat(rowIndex(main, FOR_NOTHING_TEXT)).isNotNegative();
    }

    @Test
    void theLinkSetInsideTheComponentSurvivesSaveAndReopen() throws Exception {
        Path saved = folder.resolve("banco.vsim");
        circuit.save(saved);

        OpenDocument reopened = track(OpenDocument.open(saved, StandardComponentTypes.registry()));

        Component inner = reopened.document().definition(LINKED).orElseThrow().body().components().getFirst();
        assertThat(MemoryLinkPaths.linkedFile(inner, reopened.path())).contains(linkedPath());
        assertThat(pieceWordAt(reopened, firstPiece, 1)).isEqualTo(0x22);
        assertThat(pieceWordAt(reopened, secondPiece, 1)).isEqualTo(0xBB);
        assertThat(ownContentsRows(view(reopened))).hasSize(2);
    }

    @Test
    void creatingAComponentKeepingTheLinksKeepsThemAlsoAfterSaveAndReopen() throws Exception {
        selectTheLinkedRamAndItsParts();
        dialogs.next = form -> {
            assertThat(form.offersMemoryLinks()).isTrue();
            assertThat(form.keepMemoryLinks()).isTrue();
            form.setName("Con enlace");
            form.setReplaceSelection(false); // the definition is what matters here, not where its piece fits
        };

        main.templateActions().orElseThrow().createComponent().actionPerformed(null);

        assertThat(dialogs.messages).isEmpty();
        assertThat(MemoryLinkPaths.linkedFile(ramOf(circuit, "Con enlace"), circuit.path())).contains(linkedPath());
        // a piece of it on the canvas, as from the palette: a circuit saves only the components it uses
        add(circuit, SubcircuitTypes.typeIdOf(definitionNamed(circuit, "Con enlace").id()), new GridPoint(120, 0),
                Map.of());
        Path saved = folder.resolve("banco.vsim");
        circuit.save(saved);
        OpenDocument reopened = track(OpenDocument.open(saved, StandardComponentTypes.registry()));
        assertThat(MemoryLinkPaths.linkedFile(ramOf(reopened, "Con enlace"), reopened.path()))
                .contains(linkedPath());
    }

    @Test
    void creatingAComponentWithoutTheLinksKeepsTheWordsDropsTheLinkAndWarns() {
        selectTheLinkedRamAndItsParts();
        dialogs.next = form -> {
            form.setName("Sin enlace");
            form.setReplaceSelection(false);
            form.setKeepMemoryLinks(false);
        };

        main.templateActions().orElseThrow().createComponent().actionPerformed(null);

        Component inner = ramOf(circuit, "Sin enlace");
        assertThat(inner.parameters()).containsEntry(MemoryTypes.LINK, "");
        assertThat(MemoryContents.decode((String) inner.parameters().get(MemoryTypes.CONTENTS)).word(1))
                .isEqualTo(0x22);
        assertThat(dialogs.messages).containsExactly(
                "El enlace a programa.hex no viaja dentro del componente; se guardó una copia");
    }

    /**
     * Known debt (deuda-tecnica.md): a linked file is watched only in the places that have a view, so the RAM inside a
     * component follows the file only while a tab of the component is open. This asserts today's behaviour.
     */
    @Test
    void editingTheHexReloadsTheTopLevelRamButTheInnerOneOnlyWithItsTabOpen() throws Exception {
        LinkedMemoryWatcher circuitWatcher = links.watcherOf(circuit).orElseThrow();
        circuitWatcher.poll();

        editTheFile("55 66 77 88\n");
        circuitWatcher.poll();
        circuitWatcher.poll();

        assertThat(wordAt(circuit, topRam.id(), 1)).isEqualTo(0x66);
        waitUntil(() -> inverterOutput().equals(Value.of(8, 0x99)));
        assertThat(pieceWordAt(firstPiece, 1)).as("no tab of the component: not reloaded").isEqualTo(0x22);

        OpenDocument tab = body(circuit, LINKED, firstPiece);
        EditorView tabView = new EditorView(tab, services, NO_WINDOW);
        try {
            LinkedMemoryWatcher tabWatcher = links.watcherOf(tab).orElseThrow();
            tabWatcher.poll();
            tabWatcher.poll();
            assertThat(pieceWordAt(firstPiece, 1)).as("its tab open: reloaded").isEqualTo(0x66);
            assertThat(pieceWordAt(secondPiece, 1)).as("its own words still win").isEqualTo(0xBB);
        } finally {
            tabView.dispose();
            tab.close();
        }
    }

    /** Where the link points, as {@link MemoryLinkPaths#linkedFile} resolves it. */
    private Path linkedPath() {
        return hexFile.toAbsolutePath().normalize();
    }

    // The bank

    private static Map<String, Object> ram() {
        return Map.of(MemoryTypes.WORDS, "16", MemoryTypes.WIDTH, 8);
    }

    private Component add(OpenDocument place, String typeId, GridPoint position, Map<String, Object> parameters) {
        Component component = place.registry().require(typeId).newComponent(place.document().newComponentId(),
                position, Rotation.R0, parameters, "");
        place.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    /** Places a part so its pin {@code pinName} touches {@code target}: touching pins connect, no wire needed. */
    private Component touching(String typeId, Map<String, Object> parameters, String pinName, GridPoint target) {
        ComponentType type = circuit.registry().require(typeId);
        Component probe = type.newComponent(new ElementId("probe"), GridPoint.ORIGIN, Rotation.R0, parameters, "");
        GridPoint offset = type.pinLocation(probe, pinIndex(type, probe, pinName));
        return add(circuit, typeId, new GridPoint(target.x() - offset.x(), target.y() - offset.y()), parameters);
    }

    private static GridPoint pinOf(OpenDocument place, Component component, String pinName) {
        ComponentType type = place.registry().typeOf(component);
        return type.pinLocation(component, pinIndex(type, component, pinName));
    }

    private static int pinIndex(ComponentType type, Component component, String pinName) {
        return type.forComponent(component).pinIndexOf(pinName).orElseThrow();
    }

    /** The «Enlaces» RAM with its constants (test parts: they become its input pins) and its NOT. */
    private void selectTheLinkedRamAndItsParts() {
        main.selection().set(List.of(addressConstant.id(), chipSelectConstant.id(), topRam.id(), inverter.id()));
    }

    private static ComponentDefinition definitionNamed(OpenDocument place, String componentName) {
        return place.document().definitions().stream().filter(definition -> definition.name().equals(componentName))
                .findFirst().orElseThrow();
    }

    private static Component ramOf(OpenDocument place, String componentName) {
        return definitionNamed(place, componentName).body().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.RAM))
                .findFirst().orElseThrow();
    }

    private void editTheFile(String text) throws IOException {
        Files.writeString(hexFile, text);
        // a save in another program a moment later: the watcher compares the modification time and the size
        Files.setLastModifiedTime(hexFile, FileTime.from(Instant.now().plusSeconds(5)));
    }

    // Reading

    private static long wordAt(OpenDocument place, ElementId memory, long address) throws Exception {
        awaitSimulation(place);
        return place.requestMemoryContents(memory).get(5, TimeUnit.SECONDS).orElseThrow().word(address);
    }

    private long pieceWordAt(Component piece, long address) throws Exception {
        return pieceWordAt(circuit, piece, address);
    }

    /** What the RAM inside {@code piece} holds now, read in a tab of the component showing that piece's values. */
    private long pieceWordAt(OpenDocument place, Component piece, long address) throws Exception {
        OpenDocument tab = place.openBody(LINKED, Optional.of(piece.id().value()));
        try {
            awaitSimulation(place);
            return wordAt(tab, INNER_RAM, address);
        } finally {
            tab.close();
        }
    }

    private Value inverterOutput() {
        String net = circuit.netlist().netForPin(new PinRef(inverter.id(), pinIndex(
                circuit.registry().require(StandardComponentTypes.BUFFER), inverter, "out"))).orElseThrow().id().value();
        return circuit.latestSnapshot().netValue(net);
    }

    // Problemas

    private static List<String> ownContentsRows(EditorView view) {
        return view.problems().panel().problemTexts().stream()
                .filter(text -> text.endsWith(OVER_LINK_TEXT) || text.contains(FOR_NOTHING_TEXT)).toList();
    }

    private static int rowIndex(EditorView view, String text) {
        List<String> rows = view.problems().panel().problemTexts();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).contains(text)) {
                return index;
            }
        }
        return -1;
    }

    /** The one action the right click on the row with {@code text} offers, checked by its name. */
    private static Action onlyAction(EditorView view, String text, String name) {
        List<Action> actions = view.problems().panel().actionsAt(rowIndex(view, text));
        assertThat(actions).extracting(action -> action.getValue(Action.NAME)).containsExactly(name);
        return actions.getFirst();
    }

    // Places and views

    private OpenDocument track(OpenDocument document) {
        closers.add(document::close);
        return document;
    }

    private EditorView view(OpenDocument place) {
        EditorView view = new EditorView(place, services, NO_WINDOW);
        closers.addFirst(view::dispose);
        return view;
    }

    private static OpenDocument body(OpenDocument place, String definitionId, Component piece) {
        return place.openBody(definitionId, Optional.of(piece.id().value()));
    }

    /** «Enlazar…» picks {@link #linkFile}; nothing else is asked in this scenario. */
    private static final class ScenarioPrompts implements MemoryLinkPrompts {
        Optional<Path> linkFile = Optional.empty();
        final List<String> informed = new ArrayList<>();

        @Override
        public Optional<Path> chooseLinkFile(java.awt.Component parent) {
            return linkFile;
        }

        @Override
        public Optional<Path> chooseMemoryFile(java.awt.Component parent) {
            return Optional.empty();
        }

        @Override
        public Optional<MemoryFileImport.Proposal> chooseKind(java.awt.Component parent, String fileName,
                                                              MemoryFileImport.Proposal proposal) {
            return Optional.empty();
        }

        @Override
        public void inform(java.awt.Component parent, String message) {
            informed.add(message);
        }
    }

    /** The «Crear componente» form filled by {@link #next} and accepted; «Crear igual» without outputs. */
    private static final class ScenarioDialogs implements LibraryDialogs {
        Consumer<ComponentForm> next = form -> { };
        final List<String> messages = new ArrayList<>();

        @Override
        public Optional<ComponentForm.Result> editComponent(java.awt.Component parent, ComponentForm form) {
            next.accept(form);
            return form.canAccept() ? Optional.of(form.result()) : Optional.empty();
        }

        @Override
        public boolean confirmMissingPins(java.awt.Component parent, String message) {
            return true;
        }

        @Override
        public Optional<String> askNewName(java.awt.Component parent, String currentName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> editDescription(java.awt.Component parent, String componentName, String current) {
            return Optional.empty();
        }

        @Override
        public Optional<Path> chooseExportFile(java.awt.Component parent, String suggestedFileName) {
            return Optional.empty();
        }

        @Override
        public List<Path> chooseImportFiles(java.awt.Component parent) {
            return List.of();
        }

        @Override
        public ImportChoice askImportCollision(java.awt.Component parent, String componentName) {
            return ImportChoice.CANCEL;
        }

        @Override
        public boolean confirmRemove(java.awt.Component parent, String componentName) {
            return false;
        }

        @Override
        public void showInFolder(java.awt.Component parent, Path file) {
        }

        @Override
        public void showMessage(java.awt.Component parent, String message) {
            messages.add(message);
        }

        @Override
        public void showError(java.awt.Component parent, String message) {
            messages.add(message);
        }
    }
}
