package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.GeneratedCircuit;
import cl.almejo.vsim.core.logic.GeneratorOptions;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ViewCreationTargetTest {

    private record Opened(String name, Command creation) {
    }

    private final List<Opened> opened = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void openNewCircuit(String name, Command creation) {
            opened.add(new Opened(name, creation));
        }
    };

    @TempDir
    Path libraryDirectory;
    private OpenDocument openDocument;
    private LibraryService service;
    private EditorView view;
    private ViewCreationTarget target;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(libraryDirectory));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, StandardComponentTypes.registry(), Runnable::run);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, library, List.of()), window);
        target = new ViewCreationTarget(view, service);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
        service.close();
    }

    private static GeneratedCircuit bcd() {
        TruthTableSpec spec = KarnaughModelTest.bcd();
        Minimization minimization = Minimizer.minimize(spec, Minimization.Form.SUM_OF_PRODUCTS);
        return CircuitGenerator.generate(spec, minimization, GeneratorOptions.DEFAULTS,
                StandardComponentTypes.registry());
    }

    @Test
    void aNewCircuitIsOneUndoStepOfANewWindow() {
        GeneratedCircuit circuit = bcd();
        target.openNewCircuit("BCD", circuit.fragment());

        assertThat(opened).singleElement().satisfies(window -> assertThat(window.name()).isEqualTo("BCD"));
        OpenDocument created = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            created.execute(opened.getFirst().creation());
            CircuitDocument document = created.document();
            assertThat(document.components()).hasSize(circuit.fragment().components().size());
            assertThat(document.frames()).hasSize(1);
            created.history().undo();
            assertThat(document.isEmpty()).isTrue();
            assertThat(created.history().canUndo()).isFalse();
        } finally {
            created.close();
        }
    }

    @Test
    void insertingFloatsTheFragmentOnTheCanvas() {
        GeneratedCircuit circuit = bcd();
        target.insertFloating(circuit.fragment());
        assertThat(view.canvas().tool()).isInstanceOfSatisfying(FloatingSelectionTool.class,
                tool -> assertThat(tool.fragment()).isSameAs(circuit.fragment()));
        assertThat(openDocument.document().isEmpty()).as("nothing is added until the user drops it").isTrue();
    }

    @Test
    void theComponentIsWrittenToTheLibraryAndItsNameIsTakenFromThenOn() throws Exception {
        GeneratedCircuit circuit = bcd();
        DefinitionBundle bundle = TableCircuits.component(KarnaughModelTest.bcd(), circuit.fragment(), "bcd", "BCD",
                StandardComponentTypes.registry());
        assertThat(target.takenComponentNames()).isEmpty();

        assertThat(target.saveComponent(bundle)).isEmpty();

        assertThat(service.library().find("bcd")).isPresent();
        assertThat(Files.list(libraryDirectory).toList()).hasSize(1);
        assertThat(target.takenComponentNames()).containsExactly("BCD");
        assertThat(target.takenComponentIds()).containsExactly("bcd");
    }

    @Test
    void theCurrentCircuitsDefinitionsAreTakenToo() {
        DefinitionBundle bundle = TableCircuits.component(KarnaughModelTest.bcd(), bcd().fragment(), "mio", "Mío",
                StandardComponentTypes.registry());
        openDocument.execute(new PutDefinitionsCommand(bundle.all()));
        assertThat(target.takenComponentNames()).containsExactly("Mío");
        assertThat(target.takenComponentIds()).containsExactly("mio");
    }

    @Test
    void aLibraryThatCannotBeWrittenSaysWhy() throws Exception {
        Path file = libraryDirectory.resolve("not-a-directory");
        Files.writeString(file, "x");
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(file));
        LibraryService broken = new LibraryService(preferences, new ComponentLibrary(),
                StandardComponentTypes.registry(), Runnable::run);
        try {
            DefinitionBundle bundle = TableCircuits.component(KarnaughModelTest.bcd(), bcd().fragment(), "bcd",
                    "BCD", StandardComponentTypes.registry());
            assertThat(new ViewCreationTarget(view, broken).saveComponent(bundle)).isPresent();
        } finally {
            broken.close();
        }
    }
}
