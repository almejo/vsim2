package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static cl.almejo.vsim.ui.library.TemplateActionsTest.texts;
import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

class LibraryImportTest {

    @TempDir
    Path directory;

    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private TemplateFixture fixture;
    private LibraryImport importer;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
        importer = new LibraryImport(fixture.service, fixture.dialogs);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Path outside(String fileName, DefinitionBundle bundle) throws IOException {
        Path file = directory.resolve("outside").resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new DefinitionFormat(StandardComponentTypes.registry()).write(bundle));
        return file;
    }

    @Test
    void theFileMenuImportsIntoTheLibrary() throws Exception {
        assertThat(texts(fixture.view.menu(MenuId.FILE)))
                .containsSequence("Guardar como…", "---", "Importar componente…", "---");
        fixture.dialogs.importFiles.add(List.of(outside("inverter.vsimcomponent.json", inverter)));

        perform(fixture.features.importComponent());

        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(directory.resolve("library").resolve("inverter.vsimcomponent.json")).isRegularFile();
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
    }

    @Test
    void anIdThatIsAlreadyThereAsksToReplaceOrKeepBoth() throws Exception {
        fixture.service.save(inverter);
        DefinitionBundle renamed = new DefinitionBundle(ExampleDefinitions.inverter().withName("Negador"), List.of());
        Path file = outside("negador.vsimcomponent.json", renamed);

        fixture.dialogs.collisions.add(LibraryDialogs.ImportChoice.CANCEL);
        importer.importFiles(fixture.view, List.of(file));
        assertThat(fixture.library.entries()).containsExactly(inverter);
        assertThat(fixture.dialogs.collisionNames).containsExactly("Negador");

        fixture.dialogs.collisions.add(LibraryDialogs.ImportChoice.KEEP_BOTH);
        importer.importFiles(fixture.view, List.of(file));
        assertThat(fixture.library.find("inverter-2").orElseThrow().root().name()).isEqualTo("Negador");
        assertThat(fixture.library.find("inverter")).contains(inverter);

        fixture.dialogs.collisions.add(LibraryDialogs.ImportChoice.REPLACE);
        importer.importFiles(fixture.view, List.of(file));
        assertThat(fixture.library.find("inverter")).contains(renamed);
    }

    @Test
    void keepingBothAlsoMakesTheNameUnique() {
        ComponentLibrary library = new ComponentLibrary();
        library.put(inverter);

        DefinitionBundle copy = LibraryImport.copyWithNewIdentity(inverter, library);

        assertThat(copy.root().id()).isEqualTo("inverter-2");
        assertThat(copy.root().name()).isEqualTo("Inversor 2");
        assertThat(copy.root().body()).isEqualTo(inverter.root().body());
    }

    @Test
    void badFilesAreReportedTogetherAndTheGoodOnesStillImported() throws Exception {
        Path broken = directory.resolve("broken.vsimcomponent.json");
        Files.writeString(broken, "nope");

        importer.importFiles(fixture.view, List.of(broken, outside("inverter.vsimcomponent.json", inverter)));

        assertThat(fixture.dialogs.errors).singleElement().asString().contains("broken.vsimcomponent.json");
        assertThat(fixture.library.find("inverter")).isPresent();
    }
}
