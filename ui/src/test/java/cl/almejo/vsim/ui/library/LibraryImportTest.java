package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.clipboard.FileListTransferable;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTree;
import javax.swing.TransferHandler;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Test
    void aComponentWhoseNestedDefinitionIsMissingGivesOneCleanMessage() throws Exception {
        DefinitionBundle doubleInverter = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
                id -> Optional.of(ExampleDefinitions.inverter()));
        Path file = outside("double.vsimcomponent.json", doubleInverter);
        String json = Files.readString(file);
        assertThat(json.split("\"definitions\"", -1)).hasSize(2);
        Files.writeString(file, json.replace("\"definitions\"", "\"dropped\""));

        importer.importFiles(fixture.view, List.of(file));

        assertThat(fixture.dialogs.errors).singleElement().asString()
                .startsWith("No se pudo importar double.vsimcomponent.json: ")
                .doesNotContain("Exception");
        assertThat(fixture.library.entries()).isEmpty();
    }

    @Test
    void anUnreadableFileIsReportedAndTheOthersStillImported() throws Exception {
        Path unreadable = Files.createDirectories(directory.resolve("unreadable.vsimcomponent.json"));

        importer.importFiles(fixture.view, List.of(unreadable, outside("inverter.vsimcomponent.json", inverter)));

        assertThat(fixture.dialogs.errors).singleElement().asString()
                .startsWith("No se pudo importar unreadable.vsimcomponent.json: ");
        assertThat(fixture.library.find("inverter")).isPresent();
    }

    @Test
    void anUnexpectedFailureWhileReadingIsReportedLikeAFormatError() throws Exception {
        Path good = outside("inverter.vsimcomponent.json", inverter);
        Path bad = outside("bad.vsimcomponent.json", inverter);
        LibraryImport failing = new LibraryImport(fixture.service, fixture.dialogs, file -> {
            if (file.equals(bad)) {
                throw new IllegalStateException("boom");
            }
            return fixture.service.read(file);
        });

        failing.importFiles(fixture.view, List.of(bad, good));

        assertThat(fixture.dialogs.errors).containsExactly(Messages.format("library.import.failed",
                "bad.vsimcomponent.json", Messages.readError(new CircuitFormatException(
                        CircuitFormatException.Reason.INVALID_ELEMENT, "boom"))));
        assertThat(fixture.library.find("inverter")).contains(inverter);
    }

    @Test
    void droppingAComponentFileOnThePaletteImportsItIntoTheLibrary() throws Exception {
        Path file = outside("inverter.vsimcomponent.json", inverter);
        JTree tree = find(fixture.view.palette(), JTree.class);

        boolean imported = tree.getTransferHandler().importData(
                new TransferHandler.TransferSupport(tree, new FileListTransferable(List.of(file.toFile()))));

        assertThat(imported).isTrue();
        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(directory.resolve("library").resolve("inverter.vsimcomponent.json")).isRegularFile();
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
        assertThat(fixture.dialogs.errors).isEmpty();
    }

    /** Ruling: a component file dropped on the canvas (or the window) is imported, not opened as a circuit. */
    @Test
    void droppingAComponentFileOnTheCanvasImportsItAndOpensOnlyTheCircuits() throws Exception {
        Path file = outside("inverter.vsimcomponent.json", inverter);
        Path circuit = directory.resolve("outside").resolve("circuit.vsim");
        List<Path> opened = new ArrayList<>();
        // Wired as CircuitWindow wires it (without the invokeLater).
        fixture.view.canvas().setFileDropHandler(files -> fixture.view.openOrImportFiles(files, opened::addAll));

        boolean dropped = fixture.view.canvas().getTransferHandler().importData(new TransferHandler.TransferSupport(
                fixture.view.canvas(), new FileListTransferable(List.of(file.toFile(), circuit.toFile()))));

        assertThat(dropped).isTrue();
        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
        assertThat(opened).containsExactly(circuit);
        assertThat(fixture.dialogs.errors).isEmpty();
    }

    /** Archivo ▸ Abrir and the command line split files as a drop does (CircuitWindow.openOrImport). */
    @Test
    void openingAComponentFileImportsItAndOpensOnlyTheCircuits() throws Exception {
        Path file = outside("inverter.vsimcomponent.json", inverter);
        Path circuit = directory.resolve("outside").resolve("circuit.vsim");
        List<Path> opened = new ArrayList<>();
        fixture.view.setCircuitOpener(opened::addAll); // CircuitWindow: Application.openPaths

        fixture.view.openOrImport(List.of(file, circuit));

        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
        assertThat(opened).containsExactly(circuit);
        assertThat(fixture.dialogs.errors).isEmpty();
    }

    @Test
    void withoutAComponentImporterAComponentFileIsHandedToTheOpenerAsBefore() throws Exception {
        Path file = outside("inverter.vsimcomponent.json", inverter);
        List<Path> opened = new ArrayList<>();
        fixture.view.setComponentFileImporter(null); // what TemplateFeatures.dispose leaves

        fixture.view.openOrImportFiles(List.of(file), opened::addAll);

        assertThat(opened).containsExactly(file);
        assertThat(fixture.library.find("inverter")).isEmpty();
    }

    private static <T> T find(Container container, Class<T> kind) {
        for (java.awt.Component child : container.getComponents()) {
            if (kind.isInstance(child)) {
                return kind.cast(child);
            }
            if (child instanceof Container nested) {
                T found = find(nested, kind);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
