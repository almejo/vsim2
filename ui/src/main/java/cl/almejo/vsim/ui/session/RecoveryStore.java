package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.core.io.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Recovery copies on disk (spec §4.2). Each running instance writes into its own folder
 * {@code <recoveryRoot>/<session-uuid>/}: a {@code lock} file locked for the life of the process and, per document,
 * {@code <document-uuid>.vsim} (format v2) plus {@code <document-uuid>.properties} ({@code originalPath},
 * {@code displayName}, {@code savedAt} ISO-8601). A folder whose lock can be taken belongs to a run that ended without
 * cleaning up, so its copies are {@link #orphans()}; a folder whose lock cannot be taken (held, or a filesystem without
 * locks) is treated as live and never offered. Writes are atomic (temporary file + move). {@link #write},
 * {@link #delete}, {@link #deleteSession()} and {@link #abandon()} are synchronized, so autosave and shutdown may call
 * them from different threads; the start-up methods ({@link #cleanUp}, {@link #orphans()}, {@link #discard}) only touch
 * folders of other runs and are meant to be called from one thread at a time. The application keeps one store per
 * process: on POSIX, closing any channel to a lock file releases the process's lock on it.
 */
public final class RecoveryStore {

    /** Copies older than this are deleted at start without asking (Assumption 17). */
    public static final Duration MAXIMUM_AGE = Duration.ofDays(30);
    /** An orphan folder left without copies is deleted once its lock file is this old. */
    static final Duration EMPTY_FOLDER_AGE = Duration.ofHours(1);
    static final String LOCK_FILE = "lock";
    static final String CONTENT_SUFFIX = ".vsim";
    static final String METADATA_SUFFIX = ".properties";
    private static final String ORIGINAL_PATH = "originalPath";
    private static final String DISPLAY_NAME = "displayName";
    private static final String SAVED_AT = "savedAt";
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryStore.class);

    private final Path recoveryRoot;
    private final Path sessionFolder;
    private boolean sessionOpen;
    private FileChannel lockChannel;
    private FileLock lock;

    /** Touches nothing on disk: the session folder is created and locked by the first {@link #write}. */
    public RecoveryStore(Path recoveryRoot) {
        this.recoveryRoot = recoveryRoot.toAbsolutePath();
        this.sessionFolder = this.recoveryRoot.resolve(UUID.randomUUID().toString());
    }

    /** This instance's folder; it exists from the first write until {@link #deleteSession()}. */
    public Path sessionFolder() {
        return sessionFolder;
    }

    /** Where the copy of {@code documentId} lives in this instance's folder, whether or not it exists. */
    public Path contentPath(UUID documentId) {
        return sessionFolder.resolve(documentId + CONTENT_SUFFIX);
    }

    public synchronized void write(UUID documentId, String content, RecoveryMetadata metadata) throws IOException {
        openSession();
        Path target = contentPath(documentId);
        AtomicFiles.writeString(target, content);
        AtomicFiles.writeString(metadataFor(target), metadataText(metadata));
    }

    public synchronized void delete(UUID documentId) throws IOException {
        Path target = contentPath(documentId);
        Files.deleteIfExists(target);
        Files.deleteIfExists(metadataFor(target));
    }

    /** A clean exit: releases the lock and removes this instance's folder with everything in it. */
    public synchronized void deleteSession() throws IOException {
        releaseLock();
        deleteFolder(sessionFolder);
    }

    /** Copies of runs that ended without cleaning up, oldest first. Never throws: unreadable folders are skipped. */
    public List<RecoveryCopy> orphans() {
        List<RecoveryCopy> copies = new ArrayList<>();
        for (Path folder : otherFolders()) {
            whileLocked(folder, () -> copies.addAll(copiesIn(folder)));
        }
        copies.sort(Comparator.comparing(RecoveryCopy::savedAt).thenComparing(RecoveryCopy::displayName));
        return copies;
    }

    /** Deletes an orphan copy; its folder goes too when no other copy is left in it. */
    public void discard(RecoveryCopy copy) throws IOException {
        Files.deleteIfExists(copy.content());
        Files.deleteIfExists(copy.metadata());
        Path folder = copy.content().getParent();
        if (Files.isDirectory(folder) && copiesIn(folder).isEmpty()) {
            deleteFolder(folder);
        }
    }

    /**
     * Start-up cleaning of folders no running instance holds: copies saved before {@code now - MAXIMUM_AGE} are
     * deleted, and a folder left without copies goes once its lock file is older than {@link #EMPTY_FOLDER_AGE}.
     */
    public void cleanUp(Instant now) {
        Instant oldest = now.minus(MAXIMUM_AGE);
        Instant emptySince = now.minus(EMPTY_FOLDER_AGE);
        for (Path folder : otherFolders()) {
            Optional<Boolean> empty = whileLocked(folder, () -> {
                for (RecoveryCopy copy : copiesIn(folder)) {
                    if (copy.savedAt().isBefore(oldest)) {
                        deleteQuietly(copy);
                    }
                }
                return copiesIn(folder).isEmpty() && lastModified(folder.resolve(LOCK_FILE)).isBefore(emptySince);
            });
            if (empty.orElse(false)) {
                try {
                    deleteFolder(folder); // after the lock was released: Windows cannot delete a locked file
                } catch (IOException exception) {
                    LOGGER.warn("Could not delete the old recovery folder {}", folder, exception);
                }
            }
        }
    }

    /** Releases the lock and leaves the copies where they are, as a process that crashed would. For tests only. */
    public synchronized void abandon() {
        releaseLock();
    }

    static Path metadataFor(Path content) {
        return content.resolveSibling(stem(content) + METADATA_SUFFIX);
    }

    private void openSession() throws IOException {
        if (sessionOpen && Files.isDirectory(sessionFolder)) {
            return;
        }
        releaseLock();
        Files.createDirectories(sessionFolder);
        try {
            lockChannel = FileChannel.open(sessionFolder.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
        } catch (IOException exception) {
            // A filesystem without locks: another VSim cannot take the lock either, so it treats this folder as live.
            LOGGER.warn("The recovery folder {} cannot be locked", sessionFolder, exception);
        }
        sessionOpen = true;
    }

    private void releaseLock() {
        sessionOpen = false;
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not release the lock of {}", sessionFolder, exception);
        } finally {
            lock = null;
            lockChannel = null;
        }
    }

    private List<Path> otherFolders() {
        if (!Files.isDirectory(recoveryRoot)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(recoveryRoot)) {
            return children.filter(Files::isDirectory).filter(folder -> !folder.equals(sessionFolder)).sorted().toList();
        } catch (IOException exception) {
            LOGGER.warn("Could not list the recovery folder {}", recoveryRoot, exception);
            return List.of();
        }
    }

    /** Runs {@code action} while holding {@code folder}'s lock; empty (and nothing runs) when the folder is live. */
    private static <T> Optional<T> whileLocked(Path folder, Supplier<T> action) {
        try (FileChannel channel = FileChannel.open(folder.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            FileLock folderLock;
            try {
                folderLock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                return Optional.empty(); // held by a store of this same process
            }
            if (folderLock == null) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(action.get());
            } finally {
                folderLock.release();
            }
        } catch (IOException exception) {
            LOGGER.warn("Treating the recovery folder {} as in use: its lock cannot be taken", folder, exception);
            return Optional.empty();
        }
    }

    private static List<RecoveryCopy> copiesIn(Path folder) {
        List<RecoveryCopy> copies = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*" + CONTENT_SUFFIX)) {
            for (Path content : files) {
                copies.add(describe(content));
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not list the recovery folder {}", folder, exception);
        }
        return copies;
    }

    private static RecoveryCopy describe(Path content) {
        String stem = stem(content);
        Optional<Path> originalPath = Optional.empty();
        String displayName = stem;
        Instant savedAt = null;
        Path metadata = metadataFor(content);
        if (Files.isRegularFile(metadata)) {
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(Files.readString(metadata)));
                String original = properties.getProperty(ORIGINAL_PATH, "");
                if (!original.isBlank()) {
                    originalPath = Optional.of(Path.of(original));
                }
                displayName = properties.getProperty(DISPLAY_NAME, stem);
                savedAt = Instant.parse(properties.getProperty(SAVED_AT, ""));
            } catch (IOException | IllegalArgumentException | DateTimeException exception) {
                LOGGER.warn("Ignoring the unreadable recovery metadata {}", metadata, exception);
            }
        }
        return new RecoveryCopy(content, originalPath, displayName, savedAt == null ? lastModified(content) : savedAt);
    }

    private static String metadataText(RecoveryMetadata metadata) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(ORIGINAL_PATH,
                metadata.originalPath().map(path -> path.toAbsolutePath().toString()).orElse(""));
        properties.setProperty(DISPLAY_NAME, metadata.displayName());
        properties.setProperty(SAVED_AT, metadata.savedAt().toString());
        StringWriter text = new StringWriter();
        properties.store(text, null);
        return text.toString();
    }

    private static void deleteQuietly(RecoveryCopy copy) {
        try {
            Files.deleteIfExists(copy.content());
            Files.deleteIfExists(copy.metadata());
        } catch (IOException exception) {
            LOGGER.warn("Could not delete the old recovery copy {}", copy.content(), exception);
        }
    }

    private static void deleteFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(folder);
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }

    private static String stem(Path content) {
        String fileName = content.getFileName().toString();
        return fileName.endsWith(CONTENT_SUFFIX) ? fileName.substring(0, fileName.length() - CONTENT_SUFFIX.length())
                : fileName;
    }
}
