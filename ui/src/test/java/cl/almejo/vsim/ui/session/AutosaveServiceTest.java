package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    /** APP-5: an instance whose definition is gone cannot be serialized, so it never gets a recovery copy. */
    private OpenDocument unserializable() {
        OpenDocument document = tracked();
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of());
        document.document().removeDefinition(ExampleDefinitions.INVERTER);
        return document;
    }

    @Test
    void aDocumentThatCannotBeSerializedIsReportedOncePerDocumentAndAgainWhenReopened() {
        List<OpenDocument> reported = new ArrayList<>();
        autosave.onSerializationFailure(reported::add);
        OpenDocument document = unserializable();

        autosave.tick();
        autosave.tick();
        assertThat(reported).as("once, not at every tick").containsExactly(document);
        assertThat(runWrites()).as("no copy was written").isZero();

        autosave.untrack(document);
        autosave.track(document);
        autosave.tick();
        assertThat(reported).as("reopened: told again").containsExactly(document, document);
    }

    @Test
    void aCircuitThatCanBeSerializedAgainIsToldSoAndToldAgainIfItFailsOnceMore() {
        List<String> told = new ArrayList<>();
        autosave.onSerializationFailure(document -> told.add("cannot"));
        autosave.onSerializationRecovered(document -> told.add("again"));
        OpenDocument document = unserializable();
        autosave.tick();

        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        autosave.tick();
        autosave.tick();
        document.document().removeDefinition(ExampleDefinitions.INVERTER);
        place(document, StandardComponentTypes.LED, 8, 2, Map.of());
        autosave.tick();

        assertThat(told).containsExactly("cannot", "again", "cannot");
    }

    /** Review I1: «already told» is its own state, not the failed writes'. */
    @Test
    void aCircuitWhoseCopyFailedToWriteIsStillToldWhenItCannotBeSerialized() throws Exception {
        Path blocked = Files.writeString(directory.resolve("blocked"), "not a folder");
        AutosaveService failing = new AutosaveService(new RecoveryStore(blocked.resolve("recovery")),
                Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add);
        List<OpenDocument> reported = new ArrayList<>();
        failing.onSerializationFailure(reported::add);
        OpenDocument document = untracked();
        failing.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        failing.tick();
        runWrites();

        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of());
        document.document().removeDefinition(ExampleDefinitions.INVERTER);
        failing.tick();

        assertThat(reported).containsExactly(document);
        failing.shutdown();
        runWrites();
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

    /** Debt «Autosave: recovery-copy write failures»: the user is told once, and again only after a write succeeded. */
    @Test
    void aCopyThatCannotBeWrittenIsToldOnceHoweverManyTicksFail() throws Exception {
        Path blocked = Files.writeString(directory.resolve("blocked"), "not a folder");
        RecoveryStore brokenStore = new RecoveryStore(blocked.resolve("recovery"));
        AutosaveService service = new AutosaveService(brokenStore, Clock.fixed(NOW, ZoneOffset.UTC),
                pendingWrites::add, Runnable::run);
        List<String> told = new ArrayList<>();
        service.onWriteFailure(() -> told.add("cannot"));
        service.onWriteRecovered(() -> told.add("again"));
        OpenDocument document = untracked();
        service.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        service.tick();
        runWrites();
        service.tick();
        runWrites();
        assertThat(told).containsExactly("cannot");

        service.shutdown();
        runWrites();
    }

    @Test
    void theWriteNoticeGoesAwayWhenTheFailingDocumentIsClosed() throws Exception {
        Path blocked = Files.writeString(directory.resolve("blocked"), "not a folder");
        AutosaveService service = new AutosaveService(new RecoveryStore(blocked.resolve("recovery")),
                Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add, Runnable::run);
        List<String> told = new ArrayList<>();
        service.onWriteFailure(() -> told.add("cannot"));
        service.onWriteRecovered(() -> told.add("again"));
        OpenDocument document = untracked();
        service.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        service.tick();
        runWrites();

        service.untrack(document);

        assertThat(told).containsExactly("cannot", "again");
        service.shutdown();
        runWrites();
    }

    @Test
    void aWriteThatSucceedsAfterAFailureTakesTheNoticeAwayAndAnotherFailureIsToldAgain() throws Exception {
        Path folder = directory.resolve("flaky");
        RecoveryStore flaky = new RecoveryStore(folder);
        AutosaveService service = new AutosaveService(flaky, Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add,
                Runnable::run);
        List<String> told = new ArrayList<>();
        service.onWriteFailure(() -> told.add("cannot"));
        service.onWriteRecovered(() -> told.add("again"));
        OpenDocument document = untracked();
        service.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        // the session folder is replaced by a plain file: writing fails; put a folder back: writing succeeds
        Path session = flaky.sessionFolder();
        Files.createDirectories(session.getParent());
        Files.writeString(session, "in the way");
        service.tick();
        runWrites();
        assertThat(told).containsExactly("cannot");

        Files.delete(session);
        Files.createDirectories(session);
        service.tick();
        runWrites();
        assertThat(told).containsExactly("cannot", "again");

        service.shutdown();
        runWrites();
    }

    /** Review I2: an old recovered copy must outlive a failed write of this run's copy. */
    @Test
    void anActionWaitingForACopyDoesNotRunWhenTheWriteFails() throws Exception {
        Path oldCopy = Files.writeString(directory.resolve("old.vsim"), "recovered work");
        Path blocked = Files.writeString(directory.resolve("blocked"), "not a folder");
        AutosaveService failing = new AutosaveService(new RecoveryStore(blocked.resolve("recovery")),
                Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add);
        OpenDocument document = untracked();
        failing.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        failing.afterCopied(document, () -> deleteQuietly(oldCopy));
        failing.tick();
        runWrites();
        failing.tick(); // retried, and failing again
        runWrites();

        assertThat(oldCopy).exists();
        failing.shutdown();
        runWrites();
        assertThat(oldCopy).as("not after shutdown either").exists();
    }

    @Test
    void anActionWaitingForACopyRunsOnceTheNewCopyIsOnDisk() throws Exception {
        Path oldCopy = Files.writeString(directory.resolve("old.vsim"), "recovered work");
        OpenDocument document = tracked();
        OpenDocument other = tracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        List<Boolean> newCopyWasOnDisk = new ArrayList<>();

        autosave.afterCopied(document, () -> {
            newCopyWasOnDisk.add(hasCopy(document));
            deleteQuietly(oldCopy);
        });
        autosave.afterCopied(other, () -> deleteQuietly(oldCopy)); // other is unmodified: never copied
        autosave.tick();
        assertThat(oldCopy).as("not before the writer ran").exists();
        runWrites();

        assertThat(newCopyWasOnDisk).containsExactly(true);
        assertThat(oldCopy).doesNotExist();
        place(document, StandardComponentTypes.LED, 6, 2, Map.of());
        autosave.tick();
        runWrites();
        assertThat(newCopyWasOnDisk).as("only once").hasSize(1);
    }

    @Test
    void anActionRegisteredBeforeTrackingRunsOnceTheDocumentIsTrackedAndCopied() throws Exception {
        // Application.openRecovered asks for the action before the document is shown and tracked.
        Path oldCopy = Files.writeString(directory.resolve("old.vsim"), "recovered work");
        OpenDocument document = untracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        autosave.afterCopied(document, () -> deleteQuietly(oldCopy));
        autosave.track(document);
        autosave.tick();
        assertThat(oldCopy).as("not before the writer ran").exists();
        runWrites();

        assertThat(hasCopy(document)).isTrue();
        assertThat(oldCopy).as("the old recovery copy is gone once the new one is on disk").doesNotExist();
    }

    @Test
    void anActionWaitingForTrackingNeverRunsIfTheDocumentIsNeverTracked() throws Exception {
        Path oldCopy = Files.writeString(directory.resolve("old.vsim"), "recovered work");
        OpenDocument document = untracked();
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());

        autosave.afterCopied(document, () -> deleteQuietly(oldCopy));
        autosave.tick();
        runWrites();
        autosave.shutdown();
        runWrites();

        assertThat(oldCopy).exists();
    }

    @Test
    void anActionWaitingForACopyRunsAtTheFirstWriteThatSucceeds() throws Exception {
        Path oldCopy = Files.writeString(directory.resolve("old.vsim"), "recovered work");
        Path recoveryRoot = directory.resolve("later");
        Files.writeString(recoveryRoot, "a file where the folder goes: writes fail");
        AutosaveService retrying = new AutosaveService(new RecoveryStore(recoveryRoot.resolve("recovery")),
                Clock.fixed(NOW, ZoneOffset.UTC), pendingWrites::add);
        OpenDocument document = untracked();
        retrying.track(document);
        place(document, StandardComponentTypes.LED, 2, 2, Map.of());
        retrying.afterCopied(document, () -> deleteQuietly(oldCopy));
        retrying.tick();
        runWrites();
        assertThat(oldCopy).exists();

        Files.delete(recoveryRoot); // the disk is writable again
        retrying.tick();
        runWrites();

        assertThat(oldCopy).doesNotExist();
        retrying.shutdown();
        runWrites();
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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
