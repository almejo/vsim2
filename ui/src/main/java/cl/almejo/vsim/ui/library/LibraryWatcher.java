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
 * When the directory itself is deleted the watching thread runs {@code onGone}, then checks every {@code poll} whether
 * it exists again; when it does, the thread watches it again, runs {@code onBack} and starts a reload (files may have
 * been written before it was watched).
 */
public final class LibraryWatcher implements AutoCloseable {

    public static final Duration DEBOUNCE = Duration.ofMillis(200);
    /** How often a deleted directory is looked for. */
    public static final Duration POLL = Duration.ofSeconds(2);

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryWatcher.class);

    private final Path directory;
    private final Duration debounce;
    private final Duration poll;
    private final Runnable onSettled;
    private final Runnable onGone;
    private final Runnable onBack;
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
        this(directory, debounce, POLL, onSettled, onGone, () -> {
        });
    }

    public LibraryWatcher(Path directory, Duration debounce, Duration poll, Runnable onSettled, Runnable onGone,
                          Runnable onBack) {
        this.directory = directory;
        this.debounce = debounce;
        this.poll = poll;
        this.onSettled = onSettled;
        this.onGone = onGone;
        this.onBack = onBack;
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
        register();
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

    private void register() throws IOException {
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
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

    private void report(Runnable callback, String what) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Reporting that the component library {} is {} failed", directory, what, exception);
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
                if (closed) {
                    return;
                }
                LOGGER.warn("The component library {} is gone; waiting for it to come back", directory);
                report(onGone, "gone");
                if (!waitForDirectory()) {
                    return;
                }
                LOGGER.info("The component library {} is back", directory);
                report(onBack, "back");
                changed();
            }
        }
    }

    /** Checks every {@code poll} until the directory exists again and is watched; false once closed. */
    private boolean waitForDirectory() {
        while (!closed) {
            try {
                Thread.sleep(poll.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (closed || !Files.isDirectory(directory)) {
                continue;
            }
            try {
                register();
                return true;
            } catch (ClosedWatchServiceException exception) {
                return false;
            } catch (IOException exception) {
                LOGGER.debug("Cannot watch {} yet: {}", directory, exception.getMessage());
            }
        }
        return false;
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
