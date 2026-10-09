package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryProblemsTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void everyKindOfProblemHasAText() {
        Messages.initialize(Messages.SPANISH);
        Path file = Path.of("library", "roto.vsimcomponent.json");
        assertThat(LibraryProblemText.describe(new LibraryProblem(file, LibraryProblem.Kind.INVALID,
                Optional.of(CircuitFormatException.Reason.INVALID_JSON), "line 1")))
                .isEqualTo("roto.vsimcomponent.json no es un componente válido: El archivo no es JSON válido: line 1");
        assertThat(LibraryProblemText.describe(new LibraryProblem(file, LibraryProblem.Kind.DUPLICATE_ID,
                Optional.empty(), "inverter")))
                .isEqualTo("roto.vsimcomponent.json repite el id «inverter» de otro archivo");
        assertThat(LibraryProblemText.describe(new LibraryProblem(file, LibraryProblem.Kind.UNREADABLE,
                Optional.empty(), "Is a directory")))
                .isEqualTo("No se pudo leer roto.vsimcomponent.json: Is a directory");
        Path gone = Path.of("home", "user", "library");
        assertThat(LibraryProblemText.describe(LibraryProblem.directoryGone(gone)))
                .isEqualTo("La carpeta de la librería " + gone + " ya no existe: VSim dejó de seguir sus cambios"
                        + " hasta que reinicies VSim o elijas otra carpeta");
    }

    @Test
    void aGoneLibraryFolderIsDescribedInEnglishToo() {
        Path gone = Path.of("home", "user", "library");
        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(LibraryProblemText.describe(LibraryProblem.directoryGone(gone)))
                    .isEqualTo("The library folder " + gone + " no longer exists: VSim stopped following its changes"
                            + " until you restart VSim or choose another folder");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    @Test
    void libraryProblemsAppearInTheProblemsTab() throws Exception {
        Path library = directory.resolve("library");
        Files.createDirectories(library);
        Files.writeString(library.resolve("roto.vsimcomponent.json"), "{ not json");

        fixture.service.reload();

        assertThat(fixture.view.problems().libraryProblems()).singleElement().asString()
                .startsWith("roto.vsimcomponent.json no es un componente válido");

        Files.delete(library.resolve("roto.vsimcomponent.json"));
        fixture.service.reload();

        assertThat(fixture.view.problems().libraryProblems()).isEmpty();
    }

    @Test
    void aBrokenFileWhoseComponentWasSavedElsewhereCanBeDeleted() throws Exception {
        Path library = directory.resolve("library");
        Files.createDirectories(library);
        Files.writeString(library.resolve("inverter.vsimcomponent.json"), "{ not json");
        fixture.service.reload();
        assertThat(fixture.view.problems().libraryProblems()).singleElement().asString()
                .doesNotContain("se puede borrar");

        // The broken file is never overwritten: the component goes to inverter-2.vsimcomponent.json.
        fixture.service.save(new DefinitionBundle(ExampleDefinitions.inverter(), List.of()));

        assertThat(fixture.view.problems().libraryProblems()).singleElement().asString()
                .startsWith("inverter.vsimcomponent.json no es un componente válido")
                .endsWith("(la librería ya tiene «Inversor» en otro archivo: este se puede borrar)");
        assertThat(library.resolve("inverter-2.vsimcomponent.json")).isRegularFile();
    }

    @Test
    void disposingTheFeaturesLeavesNoListener() {
        EditorView secondView = new EditorView(fixture.openDocument, fixture.services(), TemplateFixture.NO_WINDOW);
        ComponentLibrary library = fixture.library;
        int libraryListeners = library.listenerCount();
        int problemsListeners = fixture.service.problemsListenerCount();
        int documentListeners = fixture.openDocument.changeListenerCount();

        TemplateFeatures second = TemplateFeatures.install(secondView, fixture.service, new FakeLibraryDialogs());
        assertThat(library.listenerCount()).isGreaterThan(libraryListeners);
        assertThat(fixture.service.problemsListenerCount()).isGreaterThan(problemsListeners);
        assertThat(fixture.openDocument.changeListenerCount()).isGreaterThan(documentListeners);

        second.dispose();

        assertThat(library.listenerCount()).isEqualTo(libraryListeners);
        assertThat(fixture.service.problemsListenerCount()).isEqualTo(problemsListeners);
        assertThat(fixture.openDocument.changeListenerCount()).isEqualTo(documentListeners);
        secondView.dispose();
    }
}
