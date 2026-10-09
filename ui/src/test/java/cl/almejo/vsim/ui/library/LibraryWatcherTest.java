package cl.almejo.vsim.ui.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryWatcherTest {

    private static final long FILE_EVENT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final AtomicInteger settled = new AtomicInteger();

    /** File events take up to ~10 s on platforms whose WatchService polls (macOS), so wait longer than UiTestSupport. */
    static void eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + FILE_EVENT_TIMEOUT_NANOS;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 30 s");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void aBurstOfChangesSettlesIntoOneCall(@TempDir Path directory) throws Exception {
        // A wide debounce: the five calls must land within it even on a slow, busy machine.
        try (LibraryWatcher watcher = new LibraryWatcher(directory, Duration.ofSeconds(1), settled::incrementAndGet)) {
            for (int index = 0; index < 5; index++) {
                watcher.changed();
            }
            eventually(() -> settled.get() == 1);
            Thread.sleep(1200); // longer than the debounce: a split burst would settle again by now
            assertThat(settled).hasValue(1);
        }
    }

    @Test
    void filesChangedFromOutsideAreNoticed(@TempDir Path directory) throws Exception {
        Path library = directory.resolve("library");
        try (LibraryWatcher watcher = new LibraryWatcher(library, Duration.ofMillis(50), settled::incrementAndGet)) {
            watcher.start();
            assertThat(library).isDirectory();
            Files.writeString(library.resolve("x.vsimcomponent.json"), "{}");
            eventually(() -> settled.get() >= 1);
        }
    }

    @Test
    void aClosedWatcherStaysQuiet(@TempDir Path directory) throws Exception {
        LibraryWatcher watcher = new LibraryWatcher(directory, Duration.ofMillis(50), settled::incrementAndGet);
        watcher.start();
        watcher.close();
        watcher.changed();
        Files.writeString(directory.resolve("x.vsimcomponent.json"), "{}");
        Thread.sleep(300);
        assertThat(settled).hasValue(0);
    }

    @Test
    void theWatchingThreadIsADaemonAndEndsOnClose(@TempDir Path directory) throws Exception {
        LibraryWatcher watcher = new LibraryWatcher(directory, Duration.ofMillis(50), settled::incrementAndGet);
        watcher.start();
        Thread thread = watcher.watchingThread();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.isAlive()).isTrue();

        watcher.close();

        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    void aDeletedDirectoryStopsTheWatcherAndSaysSo(@TempDir Path directory) throws Exception {
        Path library = directory.resolve("library");
        AtomicInteger gone = new AtomicInteger();
        try (LibraryWatcher watcher = new LibraryWatcher(library, Duration.ofMillis(50), settled::incrementAndGet,
                gone::incrementAndGet)) {
            watcher.start();

            Files.delete(library);

            eventually(() -> gone.get() == 1);
            watcher.watchingThread().join(TimeUnit.SECONDS.toMillis(5));
            assertThat(watcher.watchingThread().isAlive()).isFalse();
        }
    }
}
