package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenDocumentTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(OpenDocument::close);
    }

    private OpenDocument track(OpenDocument document) {
        opened.add(document);
        return document;
    }

    @Test
    void newDocumentIsPristineAndUntitled() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        assertThat(document.isPristine()).isTrue();
        assertThat(document.isModified()).isFalse();
        assertThat(document.displayName()).isEqualTo("Sin título");
        assertThat(document.needsSaveAs()).isTrue();
        assertThat(document.suggestedFileName()).isEqualTo("Sin título.vsim");
    }

    @Test
    void suggestedFileNameReplacesCharactersInvalidInFileNames() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        document.document().setName("a/b\\c:d*e?f\"g<h>i|j");
        assertThat(document.suggestedFileName()).isEqualTo("a_b_c_d_e_f_g_h_i_j.vsim");
    }

    @Test
    void startDirectoryIsThePreferredFolderElseTheDocumentsFolderElseHome(@TempDir Path directory) throws Exception {
        OpenDocument untitled = track(OpenDocument.createEmpty(registry));
        assertThat(untitled.startDirectory(Optional.empty())).isEqualTo(Path.of(System.getProperty("user.home")));
        assertThat(untitled.startDirectory(Optional.of(directory))).isEqualTo(directory);
        Path file = directory.resolve("saved.vsim");
        untitled.save(file);
        assertThat(untitled.startDirectory(Optional.empty())).isEqualTo(directory);
        Path other = Files.createDirectory(directory.resolve("other"));
        assertThat(untitled.startDirectory(Optional.of(other))).isEqualTo(other);
    }

    @Test
    void savingOverAnExistingFileKeepsItsPermissions(@TempDir Path directory) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = directory.resolve("kept.vsim");
        Files.writeString(file, "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        track(OpenDocument.createEmpty(registry)).save(file);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-r--r--");
    }

    @Test
    void savingOverAReadOnlyFileFailsAndChangesNothing(@TempDir Path directory) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = directory.resolve("locked.vsim");
        Files.writeString(file, "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assumeTrue(!Files.isWritable(file)); // not when the tests run as root
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        assertThatThrownBy(() -> document.save(file)).isInstanceOf(IOException.class);
        assertThat(Files.readString(file)).isEqualTo("old");
        assertThat(document.path()).isEmpty();
    }

    @Test
    void aSavedFileHoldsExactlyTheSerializedDocument(@TempDir Path directory) throws Exception {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        Path file = directory.resolve("serialized.vsim");
        document.save(file);
        assertThat(Files.readString(file)).isEqualTo(document.serialized());
    }

    @Test
    void aNewFileIsNotCreatedWithOwnerOnlyPermissions(@TempDir Path directory) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = directory.resolve("fresh.vsim");
        track(OpenDocument.createEmpty(registry)).save(file);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isNotEqualTo("rw-------");
    }

    @Test
    void everyEditRecompilesTheNetlistWithTheDocumentRevisionAsToken() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        Component source = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        assertThat(document.netlistToken()).isEqualTo(document.document().revision());
        assertThat(document.netlist().netForPin(new PinRef(source.id(), 0))).isPresent();
        assertThat(document.isModified()).isTrue();
        assertThat(document.isPristine()).isFalse();
    }

    @Test
    void simulationCatchesUpWithTheDocument() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        Component led = place(document, StandardComponentTypes.LED, 3, -2, Map.of());
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().componentState(led.id().value()).equals(new VisibleState.LedState(Value.ONE)));
    }

    @Test
    void historyChangesAndSavesNotifyChangeListeners(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        AtomicInteger notifications = new AtomicInteger();
        document.addChangeListener(notifications::incrementAndGet);
        place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
        document.history().undo();
        document.save(directory.resolve("c.vsim"));
        assertThat(notifications).hasValue(3);
    }

    @Test
    void savedFileReopensWithTheSameContent(@TempDir Path directory) throws Exception {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
        Path file = directory.resolve("inversor.vsim");
        document.save(file);
        assertThat(document.isModified()).isFalse();
        assertThat(document.path()).contains(file.toAbsolutePath());
        assertThat(document.needsSaveAs()).isFalse();
        assertThat(document.displayName()).isEqualTo("inversor");

        OpenDocument reopened = track(OpenDocument.open(file, registry));
        assertThat(reopened.document().components()).hasSize(1);
        assertThat(reopened.isImportedFromVsim1()).isFalse();
        assertThat(reopened.isModified()).isFalse();
    }

    @Test
    void importedDocumentNeedsSaveAs() throws Exception {
        OpenDocument document = track(OpenDocument.open(Path.of("../core/src/test/resources/vsim1/one-gate.json"), registry));
        assertThat(document.isImportedFromVsim1()).isTrue();
        assertThat(document.path()).isEmpty();
        assertThat(document.needsSaveAs()).isTrue();
        assertThat(document.displayName()).isEqualTo("one-gate");
        assertThat(document.suggestedFileName()).isEqualTo("one-gate.vsim");
        assertThat(document.isPristine()).isFalse();
    }

    @Test
    void failedSaveKeepsModifiedStateAndPath(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
        Path good = directory.resolve("ok.vsim");
        document.save(good);
        place(document, StandardComponentTypes.BUFFER, 5, 0, Map.of(StandardComponentTypes.NEGATE, true));
        String before = Files.readString(good);

        assertThatThrownBy(() -> document.save(directory.resolve("missing-folder/x.vsim"))).isInstanceOf(IOException.class);

        assertThat(document.isModified()).isTrue();
        assertThat(document.path()).contains(good.toAbsolutePath());
        assertThat(Files.readString(good)).isEqualTo(before);
        try (var leftovers = Files.list(directory)) {
            assertThat(leftovers.map(path -> path.getFileName().toString())).containsExactly("ok.vsim");
        }
    }

    @Test
    void closeIsIdempotent() {
        OpenDocument document = OpenDocument.createEmpty(registry);
        document.close();
        document.close();
    }

    @Test
    void aDocumentIsBackedByTheFileItWasOpenedFromOrLastSavedTo(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("primero.vsim");
        track(OpenDocument.createEmpty(registry)).save(first);
        OpenDocument document = track(OpenDocument.open(first, registry));
        assertThat(document.isBackedBy(directory.resolve("./primero.vsim"))).isTrue();
        Path second = directory.resolve("segundo.vsim");
        document.save(second);
        assertThat(document.isBackedBy(first)).isFalse();
        assertThat(document.isBackedBy(second)).isTrue();
        assertThat(track(OpenDocument.createEmpty(registry)).isBackedBy(first)).isFalse();
    }

    @Test
    void anImportedVsim1DocumentIsBackedByItsSourceUntilSaved() throws Exception {
        Path legacy = Path.of("../core/src/test/resources/vsim1/4-bit-adder.json");
        OpenDocument document = track(OpenDocument.open(legacy, registry));
        assertThat(document.path()).isEmpty();
        assertThat(document.isBackedBy(legacy.toAbsolutePath())).isTrue();
    }

    @Test
    void pauseOnShortCircuitStopsTheSimulationWhenTwoOutputsAreJoined() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        assertThat(document.pauseOnShortCircuit()).isFalse();
        document.setPauseOnShortCircuit(true);
        assertThat(document.pauseOnShortCircuit()).isTrue();
        place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        place(document, StandardComponentTypes.SWITCH, 0, 6, Map.of("value", "0"));
        waitUntil(() -> document.latestSnapshot().running()
                && document.latestSnapshot().token() == document.netlistToken());

        document.execute(new AddWireCommand(new Wire(document.document().newWireId(),
                new GridPoint(4, 1), new GridPoint(4, 7))));
        waitUntil(() -> !document.latestSnapshot().shortCircuits().isEmpty());
        assertThat(document.latestSnapshot().running()).isFalse();
    }

    /** IA.3: the AI's version moves with every edit, undo and redo, but not when the file is saved. */
    @Test
    void theContentVersionMovesWithEveryChangeButNotWithASave(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        assertThat(document.contentVersion()).isZero();

        place(document, StandardComponentTypes.LED, 0, 0, Map.of());
        assertThat(document.contentVersion()).isEqualTo(1);
        document.history().undo();
        document.history().redo();
        assertThat(document.contentVersion()).isEqualTo(3);
        document.save(directory.resolve("led.vsim"));
        document.history().markUnsaved();
        assertThat(document.contentVersion()).isEqualTo(3);
        assertThat(document.changeCount()).isEqualTo(5);
    }

    @Test
    void everyHistoryNotificationMovesTheChangeCountAndTheRecoveryIdStays(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        OpenDocument other = track(OpenDocument.createEmpty(registry));
        UUID id = document.recoveryId();
        assertThat(other.recoveryId()).isNotEqualTo(id);
        assertThat(document.changeCount()).isZero();

        place(document, StandardComponentTypes.LED, 0, 0, Map.of());
        assertThat(document.changeCount()).isEqualTo(1);
        document.history().undo();
        document.history().redo();
        assertThat(document.changeCount()).isEqualTo(3);
        document.save(directory.resolve("led.vsim"));
        assertThat(document.changeCount()).isEqualTo(4);
        assertThat(document.recoveryId()).isEqualTo(id);
    }

    @Test
    void aRecoveredCopyOpensAsTheModifiedOriginal(@TempDir Path directory) throws Exception {
        Path original = directory.resolve("contador.vsim");
        Path copy = directory.resolve("copy.vsim");
        OpenDocument source = track(OpenDocument.createEmpty(registry));
        source.save(original);
        place(source, StandardComponentTypes.LED, 4, 4, Map.of());
        source.save(copy);

        OpenDocument recovered = track(OpenDocument.openRecovered(copy, original, registry));
        assertThat(recovered.document().components()).hasSize(1);
        assertThat(recovered.isModified()).isTrue();
        assertThat(recovered.isPristine()).isFalse();
        assertThat(recovered.path()).contains(original.toAbsolutePath());
        assertThat(recovered.isBackedBy(original)).isTrue();
        assertThat(recovered.displayName()).isEqualTo("contador");
        assertThat(recovered.needsSaveAs()).as("Guardar overwrites the original").isFalse();

        recovered.save(original);
        assertThat(recovered.isModified()).isFalse();
    }

    @Test
    void aRecoveredUntitledCopyAsksForANameWhenSaved(@TempDir Path directory) throws Exception {
        Path copy = directory.resolve("copy.vsim");
        OpenDocument source = track(OpenDocument.createEmpty(registry));
        place(source, StandardComponentTypes.LED, 4, 4, Map.of());
        source.save(copy);

        OpenDocument recovered = track(OpenDocument.openRecovered(copy, null, registry));
        assertThat(recovered.isModified()).isTrue();
        assertThat(recovered.needsSaveAs()).isTrue();
        assertThat(recovered.path()).isEmpty();
        assertThat(recovered.isBackedBy(copy)).as("the copy is not the document's file").isFalse();
        assertThat(recovered.displayName()).isEqualTo("Sin título");
    }

    @Test
    void aRecoveredCopyWithoutAStoredNameNeverTakesTheCopyFileName(@TempDir Path directory) throws Exception {
        Path original = directory.resolve("contador.vsim");
        Path copy = directory.resolve("1b2c3d.vsim");
        OpenDocument source = track(OpenDocument.createEmpty(registry));
        source.save(copy);

        OpenDocument recovered = track(OpenDocument.openRecovered(copy, original, registry));
        assertThat(recovered.document().name()).isEmpty();
        assertThat(recovered.displayName()).isEqualTo("contador");
    }
}
