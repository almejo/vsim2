package cl.almejo.vsim.ui.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches one directory on its own daemon thread and runs {@code onSettled} (on another daemon thread) once a burst
 * of file changes has been quiet for {@code debounce}. Never touches Swing. {@link #close()} stops both threads.
 * <p>
 * When the directory itself is deleted the watch cannot go on: the watching thread runs {@code onGone} and ends.
 */
public final class LibraryWatcher implements AutoCloseable {

    public static final Duration DEBOUNCE = Duration.ofMillis(200);

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryWatcher.class);

    private final Path directory;
    private final Duration debounce;
    private final Runnable onSettled;
    private final Runnable onGone;
    private final ScheduledExecutorService scheduler;
    private WatchService watchService;
    private Thread thread;
    private ScheduledFuture<?> pending;
    private volatile boolean closed;

    public LibraryWatcher(Path directory, Duration debounce, Runnable onSettled) {
        this(directory, debounce, onSettled, () -> {
        });
    }

    public LibraryWatcher(Path directory, Duration debounce, Runnable onSettled, Runnable onGone) {
        this.directory = directory;
        this.debounce = debounce;
        this.onSettled = onSettled;
        this.onGone = onGone;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread worker = new Thread(runnable, "vsim-library-reload");
            worker.setDaemon(true);
            return worker;
        });
    }

    /**
     * Creates the directory when missing (a missing directory cannot be watched, so the library directory exists
     * from the first start on) and starts watching it.
     */
    public synchronized void start() throws IOException {
        if (thread != null || closed) {
            return;
        }
        Files.createDirectories(directory);
        watchService = directory.getFileSystem().newWatchService();
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        thread = new Thread(this::watch, "vsim-library-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /** Something changed: (re)starts the quiet period. Package-private so tests can simulate events. */
    synchronized void changed() {
        if (closed) {
            return;
        }
        if (pending != null) {
            pending.cancel(false);
        }
        pending = scheduler.schedule(this::settle, debounce.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** For tests: the thread that waits for file events, or null before {@link #start()}. */
    synchronized Thread watchingThread() {
        return thread;
    }

    private void settle() {
        if (closed) {
            return;
        }
        try {
            onSettled.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Reloading the component library {} failed", directory, exception);
        }
    }

    private void gone() {
        try {
            onGone.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Reporting that the component library {} is gone failed", directory, exception);
        }
    }

    private void watch() {
        while (!closed) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException exception) {
                return;
            }
            key.pollEvents();
            changed();
            if (!key.reset()) {
                if (!closed) {
                    LOGGER.warn("Stopped watching the component library {}: it is gone", directory);
                    gone();
                }
                return;
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (pending != null) {
            pending.cancel(false);
        }
        scheduler.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException exception) {
                LOGGER.warn("Could not stop watching {}", directory, exception);
            }
        }
        if (thread != null) {
            thread.interrupt();
        }
    }
}
