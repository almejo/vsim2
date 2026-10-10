package cl.almejo.vsim.ui.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryStoreTest {

    private static final Instant SAVED_AT = Instant.parse("2026-10-09T17:32:00Z");

    @TempDir
    Path directory;

    private final List<RecoveryStore> stores = new ArrayList<>();

    /** A store of another "running instance" sharing the same recovery root. */
    private RecoveryStore store() {
        RecoveryStore store = new RecoveryStore(directory.resolve("recovery"));
        stores.add(store);
        return store;
    }

    @AfterEach
    void releaseLocks() {
        stores.forEach(RecoveryStore::abandon);
    }

    private static RecoveryMetadata untitled(Instant savedAt) {
        return new RecoveryMetadata(Optional.empty(), "Sin título", savedAt);
    }

    private static List<String> fileNames(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }

    @Test
    void aWriteReplacesTheCopyAtomicallyAndLeavesNoTemporaryFile() throws IOException {
        RecoveryStore store = store();
        UUID document = UUID.randomUUID();
        store.write(document, "first", untitled(SAVED_AT));
        store.write(document, "second", new RecoveryMetadata(Optional.of(directory.resolve("contador.vsim")),
                "contador", SAVED_AT));

        assertThat(Files.readString(store.contentPath(document))).isEqualTo("second");
        assertThat(fileNames(store.sessionFolder()))
                .containsExactlyInAnyOrder("lock", document + ".vsim", document + ".properties");
    }

    @Test
    void aLiveStoreIsNeverAnOrphanAndAnAbandonedOneIs() throws IOException {
        RecoveryStore crashed = store();
        UUID document = UUID.randomUUID();
        Path original = directory.resolve("contador.vsim");
        crashed.write(document, "{}", new RecoveryMetadata(Optional.of(original), "contador", SAVED_AT));
        RecoveryStore next = store();
        assertThat(next.orphans()).as("the first run is still alive").isEmpty();

        crashed.abandon();
        assertThat(next.orphans()).containsExactly(
                new RecoveryCopy(crashed.contentPath(document), Optional.of(original), "contador", SAVED_AT));
        assertThat(crashed.orphans()).as("a store never offers its own folder").isEmpty();
    }

    @Test
    void deletingACopyAndThenTheSessionLeavesNothingBehind() throws IOException {
        RecoveryStore store = store();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.write(first, "1", untitled(SAVED_AT));
        store.write(second, "2", untitled(SAVED_AT));

        store.delete(first);
        assertThat(store.contentPath(first)).doesNotExist();
        assertThat(store.sessionFolder().resolve(first + ".properties")).doesNotExist();
        assertThat(store.contentPath(second)).exists();

        store.deleteSession();
        assertThat(store.sessionFolder()).doesNotExist();
        assertThat(store().orphans()).isEmpty();
    }

    @Test
    void unreadableMetadataFallsBackToTheFileNameAndTheFileTime() throws IOException {
        RecoveryStore crashed = store();
        UUID document = UUID.randomUUID();
        crashed.write(document, "{}", untitled(SAVED_AT));
        Files.writeString(crashed.sessionFolder().resolve(document + ".properties"), "savedAt=yesterday\n");
        crashed.abandon();

        RecoveryCopy copy = store().orphans().get(0);
        assertThat(copy.displayName()).isEqualTo(document.toString());
        assertThat(copy.originalPath()).isEmpty();
        assertThat(copy.savedAt()).isEqualTo(Files.getLastModifiedTime(copy.content()).toInstant());
        assertThat(copy.metadata()).isEqualTo(crashed.sessionFolder().resolve(document + ".properties"));
    }

    @Test
    void discardingTheLastCopyOfAnOrphanFolderRemovesTheFolder() throws IOException {
        RecoveryStore crashed = store();
        crashed.write(UUID.randomUUID(), "1", untitled(SAVED_AT));
        crashed.write(UUID.randomUUID(), "2", untitled(SAVED_AT.plusSeconds(60)));
        crashed.abandon();
        RecoveryStore next = store();
        List<RecoveryCopy> orphans = next.orphans();
        assertThat(orphans).extracting(RecoveryCopy::savedAt).containsExactly(SAVED_AT, SAVED_AT.plusSeconds(60));

        next.discard(orphans.get(0));
        assertThat(orphans.get(0).content()).doesNotExist();
        assertThat(orphans.get(0).metadata()).doesNotExist();
        assertThat(crashed.sessionFolder()).isDirectory();
        next.discard(orphans.get(1));
        assertThat(crashed.sessionFolder()).doesNotExist();
    }

    @Test
    void startUpCleaningDeletesOldCopiesAndEmptyAbandonedFoldersButNeverALiveOne() throws IOException {
        Instant now = Instant.now();
        RecoveryStore old = store();
        old.write(UUID.randomUUID(), "{}", new RecoveryMetadata(Optional.empty(), "viejo", now.minus(Duration.ofDays(31))));
        old.abandon();
        Files.setLastModifiedTime(old.sessionFolder().resolve("lock"), FileTime.from(now.minus(Duration.ofHours(2))));
        RecoveryStore recent = store();
        recent.write(UUID.randomUUID(), "{}", new RecoveryMetadata(Optional.empty(), "nuevo", now.minus(Duration.ofDays(29))));
        recent.abandon();
        RecoveryStore live = store();
        UUID liveDocument = UUID.randomUUID();
        live.write(liveDocument, "{}", new RecoveryMetadata(Optional.empty(), "vivo", now.minus(Duration.ofDays(40))));

        RecoveryStore next = store();
        next.cleanUp(now);

        assertThat(old.sessionFolder()).doesNotExist();
        assertThat(next.orphans()).extracting(RecoveryCopy::displayName).containsExactly("nuevo");
        assertThat(live.contentPath(liveDocument)).as("a live run's copies are never touched").exists();
    }
}
