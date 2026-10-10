package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.OpenDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Autosave (spec §4.1, §4.2). Every minute each tracked document that is modified and whose
 * {@link OpenDocument#changeCount()} moved since its last copy (every change goes through {@code execute}, channel
 * edits included — contract C5) is serialized on the EDT and handed to one writer thread, which also runs the deletes
 * in order. A document back at its saved state (saved, or undone to it) loses its copy at once; so does a document
 * that stops being tracked (closed). A failing document is logged once and retried at every tick. EDT only, except the
 * writer.
 */
public final class AutosaveService {

    public static final int INTERVAL_MILLISECONDS = 60_000;
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(AutosaveService.class);

    /** What autosave knows about one document. */
    private static final class Tracked {
        private final Runnable listener;
        /** The change count of the last copy handed to the writer; -1 when no copy is on disk. */
        private long copiedChangeCount = -1;
        /** Run by the writer once one of this document's copies is actually on disk ({@link #afterCopied}). */
        private final Queue<Runnable> afterCopied = new ConcurrentLinkedQueue<>();

        private Tracked(Runnable listener) {
            this.listener = listener;
        }

        private boolean hasCopy() {
            return copiedChangeCount >= 0;
        }
    }

    private final RecoveryStore store;
    private final Clock clock;
    private final Executor writer;
    private final Timer timer;
    private final Map<OpenDocument, Tracked> tracked = new LinkedHashMap<>();
    private final Set<UUID> failing = ConcurrentHashMap.newKeySet();
    private final AtomicInteger loggedFailures = new AtomicInteger();
    private volatile boolean shutDown;

    public AutosaveService(RecoveryStore store, Clock clock, Executor writer) {
        this.store = store;
        this.clock = clock;
        this.writer = writer;
        this.timer = new Timer(INTERVAL_MILLISECONDS, event -> tick());
    }

    /** The application's service: the system clock and one daemon writer thread. The timer starts with {@link #start()}. */
    public static AutosaveService create(RecoveryStore store) {
        ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vsim-autosave");
            thread.setDaemon(true);
            return thread;
        });
        return new AutosaveService(store, Clock.systemDefaultZone(), writer);
    }

    /** Starts the 60 s timer; nothing after {@link #shutdown()}. */
    public void start() {
        if (!shutDown) {
            timer.start();
        }
    }

    /** Whether the timer is ticking. */
    public boolean isRunning() {
        return timer.isRunning();
    }

    int loggedFailures() {
        return loggedFailures.get();
    }

    /** Starts copying {@code document}; tracking it twice changes nothing. */
    public void track(OpenDocument document) {
        if (shutDown || tracked.containsKey(document)) {
            return;
        }
        Runnable listener = () -> {
            if (!document.isModified()) {
                discardCopy(document);
            }
        };
        tracked.put(document, new Tracked(listener));
        document.addChangeListener(listener);
    }

    /** The document is closing: it stops being copied and its copy, if any, is deleted. */
    public void untrack(OpenDocument document) {
        Tracked state = tracked.get(document);
        if (state == null) {
            return;
        }
        discardCopy(document);
        document.removeChangeListener(state.listener);
        tracked.remove(document);
    }

    public void tick() {
        if (shutDown) {
            return;
        }
        for (Map.Entry<OpenDocument, Tracked> entry : tracked.entrySet()) {
            OpenDocument document = entry.getKey();
            Tracked state = entry.getValue();
            if (!document.isModified()) {
                discardCopy(document);
                continue;
            }
            UUID id = document.recoveryId();
            if (state.copiedChangeCount == document.changeCount() && !failing.contains(id)) {
                continue;
            }
            String name = document.displayName();
            String content;
            try {
                content = document.serialized();
            } catch (RuntimeException exception) {
                reportFailure(id, name, exception);
                continue;
            }
            RecoveryMetadata metadata = new RecoveryMetadata(document.path(), name, clock.instant());
            state.copiedChangeCount = document.changeCount();
            writer.execute(() -> {
                if (shutDown) {
                    return;
                }
                try {
                    store.write(id, content, metadata);
                    failing.remove(id);
                } catch (IOException | RuntimeException exception) {
                    reportFailure(id, name, exception);
                    return;
                }
                runAfterCopied(state);
            });
        }
    }

    /**
     * A clean exit: stops the timer for good, forgets every document and, after the pending writes, deletes this
     * instance's recovery folder. Waits at most {@link #SHUTDOWN_WAIT} for the writer.
     */
    public void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        timer.stop();
        tracked.forEach((document, state) -> document.removeChangeListener(state.listener));
        tracked.clear();
        writer.execute(() -> {
            try {
                store.deleteSession();
            } catch (IOException exception) {
                LOGGER.warn("Could not delete the recovery folder {}", store.sessionFolder(), exception);
            }
        });
        if (writer instanceof ExecutorService service) {
            service.shutdown();
            try {
                if (!service.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                    LOGGER.warn("The recovery copies were still being written at exit");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Runs {@code action} on the writer after every write queued so far, whether those writes succeeded or not (to
     * wait for a copy that is actually on disk, use {@link #afterCopied}). Nothing after shutdown.
     */
    public void afterPendingWrites(Runnable action) {
        if (!shutDown) {
            writer.execute(action);
        }
    }

    /**
     * Runs {@code action} on the writer once a copy of {@code document} made by this run is actually on disk: after
     * the next successful write, never after a failed one (a failing document is retried at every tick, and the action
     * waits for the first write that succeeds). Used to delete an old recovered copy only when the new one exists, so
     * a failing disk never leaves the recovered work without a copy. Nothing for an untracked document, and nothing
     * after shutdown: the action then never runs (the old copy stays, to be offered again).
     */
    public void afterCopied(OpenDocument document, Runnable action) {
        Tracked state = tracked.get(document);
        if (!shutDown && state != null) {
            state.afterCopied.add(action);
        }
    }

    /** On the writer, right after a successful write of {@code state}'s document. */
    private void runAfterCopied(Tracked state) {
        Runnable action;
        while ((action = state.afterCopied.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                LOGGER.warn("An action waiting for a recovery copy failed", exception);
            }
        }
    }

    private void discardCopy(OpenDocument document) {
        Tracked state = tracked.get(document);
        if (state == null || !state.hasCopy()) {
            return;
        }
        state.copiedChangeCount = -1;
        UUID id = document.recoveryId();
        writer.execute(() -> {
            try {
                store.delete(id);
            } catch (IOException exception) {
                LOGGER.warn("Could not delete the recovery copy {}", id, exception);
            }
        });
    }

    private void reportFailure(UUID id, String name, Exception exception) {
        if (failing.add(id)) {
            loggedFailures.incrementAndGet();
            LOGGER.warn("Could not write the recovery copy of {}", name, exception);
        }
    }
}
