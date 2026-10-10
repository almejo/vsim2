package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class AutosaveServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T17:32:00Z");

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> opened = new ArrayList<>();
    /** The writer thread, run by hand: a queued write is not on disk until {@link #runWrites()}. */
    private final List<Runnable> pendingWrites = new ArrayList<>();
    private RecoveryStore store;
    private AutosaveService autosave;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        store = new RecoveryStore(directory.resolve("recovery"));
        autosave = new AutosaveService(store, Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add);
    }

    @AfterEach
    void close() {
        autosave.shutdown();
        runWrites();
        opened.forEach(OpenDocument::close);
        store.abandon();
    }

    private OpenDocument untracked() {
        OpenDocument document = OpenDocument.createEmpty(registry);
        opened.add(document);
        return document;
    }

    private OpenDocument tracked() {
        OpenDocument document = untracked();
        autosave.track(document);
        return document;
    }

    private int runWrites() {
        List<Runnable> writes = List.copyOf(pendingWrites);
        pendingWrites.clear();
        writes.forEach(Runnable::run);
        return writes.size();
    }

    private boolean hasCopy(OpenDocument document) {
        return Files.exists(store.contentPath(document.recoveryId()));
    }

    @Test
    void anUnmodifiedDocumentIsNeverCopied() {
        OpenDocument document = tracked();
        autosave.tick();
        assertThat(runWrites()).isZero();
        assertThat(hasCopy(document)).isFalse();
    }

    @Test
    void aModifiedDocumentIsCopiedOnceUntilItChangesAgain() throws Exception {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        autosave.tick();
        assertThat(hasCopy(document)).as("serialized on the EDT, written by the writer").isFalse();
        assertThat(runWrites()).isEqualTo(1);
        assertThat(new CircuitReader(registry).read(store.contentPath(document.recoveryId())).components()).hasSize(1);

        autosave.tick();
        assertThat(runWrites()).as("unchanged since the last copy").isZero();

        place(document, StandardComponentTypes.LED, 6, 2, Map.of());
        autosave.tick();
        assertThat(runWrites()).isEqualTo(1);
        assertThat(new CircuitReader(registry).read(store.contentPath(document.recoveryId())).components()).hasSize(2);
    }

    @Test
    void theCopyRemembersWhereTheDocumentCameFromAndWhen() throws Exception {
        Path original = directory.resolve("contador.vsim");
        OpenDocument document = tracked();
        document.save(original);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.tick();
        runWrites();

        store.abandon();
        assertThat(new RecoveryStore(directory.resolve("recovery")).orphans()).containsExactly(new RecoveryCopy(
                store.contentPath(document.recoveryId()), Optional.of(original), "contador", NOW));
    }

    @Test
    void savingOrUndoingBackToTheSavedStateDeletesTheCopy() throws Exception {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.tick();
        runWrites();
        assertThat(hasCopy(document)).isTrue();

        document.save(directory.resolve("a.vsim"));
        runWrites();
        assertThat(hasCopy(document)).isFalse();

        place(document, StandardComponentTypes.LED, 6, 2, Map.of());
        autosave.tick();
        runWrites();
        assertThat(hasCopy(document)).isTrue();
        document.history().undo();
        runWrites();
        assertThat(hasCopy(document)).as("back at the saved state").isFalse();
        autosave.tick();
        assertThat(runWrites()).isZero();
    }

    @Test
    void untrackingDeletesTheCopyAndRemovesTheListener() {
        OpenDocument document = untracked();
        int baseline = document.changeListenerCount();
        autosave.track(document);
        autosave.track(document);
        assertThat(document.changeListenerCount()).isEqualTo(baseline + 1);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.tick();
        runWrites();

        autosave.untrack(document);
        runWrites();
        assertThat(hasCopy(document)).isFalse();
        assertThat(document.changeListenerCount()).isEqualTo(baseline);
        place(document, StandardComponentTypes.LED, 6, 2, Map.of());
        autosave.tick();
        assertThat(runWrites()).isZero();
    }

    @Test
    void aFailingWriteIsLoggedOnceAndRetriedAtEveryTick() throws Exception {
        Path blocked = Files.writeString(directory.resolve("blocked"), "not a folder");
        AutosaveService failing = new AutosaveService(new RecoveryStore(blocked.resolve("recovery")),
                Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add);
        OpenDocument document = untracked();
        failing.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        failing.tick();
        assertThat(runWrites()).isEqualTo(1);
        failing.tick();
        assertThat(runWrites()).as("retried although unchanged").isEqualTo(1);
        assertThat(failing.loggedFailures()).isEqualTo(1);

        failing.shutdown();
        runWrites();
    }

    @Test
    void shutdownStopsTheTimerForGoodAndDeletesThisRunsFolder() {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.start();
        assertThat(autosave.isRunning()).isTrue();
        autosave.tick();
        runWrites();
        assertThat(store.sessionFolder()).isDirectory();

        autosave.shutdown();
        runWrites();
        assertThat(autosave.isRunning()).isFalse();
        assertThat(store.sessionFolder()).doesNotExist();

        autosave.start();
        assertThat(autosave.isRunning()).as("never restarts after shutdown").isFalse();
        autosave.tick();
        assertThat(runWrites()).isZero();
    }

    @Test
    void afterPendingWritesRunsAfterThisTicksCopies() {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        List<Boolean> copyWasOnDisk = new ArrayList<>();

        autosave.tick();
        autosave.afterPendingWrites(() -> copyWasOnDisk.add(hasCopy(document)));
        runWrites();

        assertThat(copyWasOnDisk).containsExactly(true);
    }

    @Test
    void aWriteQueuedBeforeShutdownDoesNotLeaveTheFolderBehind() {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.tick();

        autosave.shutdown();
        runWrites();

        assertThat(store.sessionFolder()).doesNotExist();
    }

    @Test
    void nothingIsWrittenOnceShutDownEvenIfTheWriterRunsLate() {
        OpenDocument document = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        autosave.tick();
        List<Runnable> queued = List.copyOf(pendingWrites);
        pendingWrites.clear();

        autosave.shutdown();
        runWrites();
        queued.forEach(Runnable::run);

        assertThat(store.sessionFolder()).doesNotExist();
        autosave.afterPendingWrites(() -> { throw new AssertionError("must not run after shutdown"); });
        assertThat(runWrites()).isZero();
    }
}
