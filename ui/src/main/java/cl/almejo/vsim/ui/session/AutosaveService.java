package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.DaemonThreads;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Autosave (spec §4.1, §4.2). Every minute each tracked document that is modified and whose
 * {@link OpenDocument#changeCount()} moved since its last copy (every change goes through {@code execute}, channel
 * edits included — contract C5) is serialized on the EDT and handed to one writer thread, which also runs the deletes
 * in order. Work the editor keeps outside a document ({@link RecoveryDrafts}: a machine's test draft, fix round 2 of
 * hito 4) goes in its copy too: a document holding it is copied even when saved, and every edit of it counts as a
 * change. A document back at its saved state (saved, or undone to it) and holding no such work loses its copy at
 * once; so does a document that stops being tracked (closed). A failing document is logged once, the user told once
 * ({@link #onWriteFailure}), and retried at every tick. EDT only, except the
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
        /** The drafts' change count ({@link RecoveryDrafts#changeCount}) of the last copy handed to the writer. */
        private long copiedDraftCount = -1;
        /** Run by the writer once one of this document's copies is actually on disk. */
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
    private final RecoveryDrafts drafts;
    private final Timer timer;
    private final Map<OpenDocument, Tracked> tracked = new LinkedHashMap<>();
    /** Actions asked for before the document was tracked ({@link #afterCopied}); {@link #track} moves them over. */
    private final Map<OpenDocument, Queue<Runnable>> waitingForTracking = new IdentityHashMap<>();
    private final Set<UUID> failing = ConcurrentHashMap.newKeySet();
    /**
     * The recovery ids of the tracked documents. A failed write adds its document to {@link #failing} only while it is
     * here, so a write queued before the close cannot put a closed document back there.
     */
    private final Set<UUID> live = ConcurrentHashMap.newKeySet();
    private final AtomicInteger loggedFailures = new AtomicInteger();
    /**
     * The tracked documents that could not be serialized at their last try, already told (EDT only). Its own set, not
     * {@link #failing}: a document whose copy failed to write must still be told when it cannot be serialized.
     */
    private final Set<UUID> unserializable = new HashSet<>();
    /** Whether the user was told that a recovery copy cannot be written (so the notice is shown once per episode). */
    private final AtomicBoolean writeTrouble = new AtomicBoolean();
    /** Runs the write-trouble listeners: the EDT in the application (the writer thread is not it). */
    private final Executor notifier;
    private volatile boolean shutDown;
    private volatile Runnable writeFailure = () -> { };
    private volatile Runnable writeRecovered = () -> { };
    /** Told (on the EDT) once per document that cannot be serialized, so it has no recovery copy. */
    private Consumer<OpenDocument> serializationFailure = document -> { };
    /** Told (on the EDT) when a document that could not be serialized can be again. */
    private Consumer<OpenDocument> serializationRecovered = document -> { };

    public AutosaveService(RecoveryStore store, Clock clock, Executor writer) {
        this(store, clock, writer, RecoveryDrafts.NONE, SwingUtilities::invokeLater);
    }

    /** {@code notifier} runs the write-trouble listeners ({@link #onWriteFailure}); tests pass a direct one. */
    public AutosaveService(RecoveryStore store, Clock clock, Executor writer, Executor notifier) {
        this(store, clock, writer, RecoveryDrafts.NONE, notifier);
    }

    /** With the work the editor keeps outside the documents ({@code drafts}) carried in their copies. */
    public AutosaveService(RecoveryStore store, Clock clock, Executor writer, RecoveryDrafts drafts) {
        this(store, clock, writer, drafts, SwingUtilities::invokeLater);
    }

    /** With {@code drafts} carried in the copies and {@code notifier} running the write-trouble listeners. */
    public AutosaveService(RecoveryStore store, Clock clock, Executor writer, RecoveryDrafts drafts,
                           Executor notifier) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.store = store;
        this.clock = clock;
        this.writer = writer;
        this.notifier = notifier;
        this.timer = new Timer(INTERVAL_MILLISECONDS, event -> tick());
    }

    /** The application's service: the system clock and one daemon writer thread. The timer starts with {@link #start()}. */
    public static AutosaveService create(RecoveryStore store, RecoveryDrafts drafts) {
        ExecutorService writer = DaemonThreads.single("vsim-autosave");
        return new AutosaveService(store, Clock.systemDefaultZone(), writer, drafts);
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

    /**
     * {@code listener} runs on the EDT the first time a tracked document cannot be serialized, and again after it
     * could be ({@link #onSerializationRecovered}) or after it was untracked and tracked anew.
     */
    public void onSerializationFailure(Consumer<OpenDocument> listener) {
        this.serializationFailure = listener;
    }

    /** {@code listener} runs on the EDT when a document that could not be serialized can be again. */
    public void onSerializationRecovered(Consumer<OpenDocument> listener) {
        this.serializationRecovered = listener;
    }

    /**
     * {@code listener} runs (on the EDT) when a recovery copy cannot be written (full disk, unwritable folder), once
     * until every failing copy was written or its document closed ({@link #onWriteRecovered}). The failure is still
     * logged.
     */
    public void onWriteFailure(Runnable listener) {
        this.writeFailure = listener;
    }

    /** {@code listener} runs (on the EDT) when no recovery copy fails to be written any more. */
    public void onWriteRecovered(Runnable listener) {
        this.writeRecovered = listener;
    }

    /**
     * Tells the listeners when «some copy cannot be written» changed; the change is told once. Only the writer thread
     * calls it (and it is synchronized), so reading {@link #failing} and setting the flag cannot interleave.
     */
    private synchronized void writeTroubleMayHaveChanged() {
        boolean trouble = !failing.isEmpty();
        if (writeTrouble.compareAndSet(!trouble, trouble) && !shutDown) {
            Runnable listener = trouble ? writeFailure : writeRecovered;
            notifier.execute(listener);
        }
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
            if (!needsCopy(document)) {
                discardCopy(document);
            }
        };
        Tracked state = new Tracked(listener);
        Queue<Runnable> early = waitingForTracking.remove(document);
        if (early != null) {
            state.afterCopied.addAll(early);
        }
        tracked.put(document, state);
        live.add(document.recoveryId());
        document.addChangeListener(listener);
    }

    /** The document is closing: it stops being copied and its copy, if any, is deleted. */
    public void untrack(OpenDocument document) {
        waitingForTracking.remove(document);
        Tracked state = tracked.get(document);
        if (state == null) {
            return;
        }
        discardCopy(document);
        UUID id = document.recoveryId();
        live.remove(id);
        // on the writer, after every write already queued for this document: those cannot re-add it any more
        writer.execute(() -> {
            failing.remove(id);
            writeTroubleMayHaveChanged();
        });
        unserializable.remove(document.recoveryId());
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
            if (!needsCopy(document)) {
                discardCopy(document);
                continue;
            }
            UUID id = document.recoveryId();
            long draftCount = drafts.changeCount(document);
            if (state.copiedChangeCount == document.changeCount() && state.copiedDraftCount == draftCount
                    && !failing.contains(id)) {
                continue;
            }
            String name = document.displayName();
            String content;
            try {
                content = drafts.serialized(document);
            } catch (RuntimeException exception) {
                // retried at every tick: the change it was made for is still not copied
                if (unserializable.add(id)) {
                    loggedFailures.incrementAndGet();
                    LOGGER.warn("Could not serialize {} for its recovery copy", name, exception);
                    serializationFailure.accept(document);
                }
                continue;
            }
            if (unserializable.remove(id)) {
                serializationRecovered.accept(document);
            }
            RecoveryMetadata metadata = new RecoveryMetadata(document.path(), name, clock.instant());
            state.copiedChangeCount = document.changeCount();
            state.copiedDraftCount = draftCount;
            writer.execute(() -> writeCopy(state, id, name, content, metadata));
        }
    }

    /** Runs on the writer. */
    private void writeCopy(Tracked state, UUID id, String name, String content, RecoveryMetadata metadata) {
        if (shutDown) {
            return;
        }
        try {
            store.write(id, content, metadata);
            failing.remove(id);
            writeTroubleMayHaveChanged();
        } catch (IOException | RuntimeException exception) {
            reportFailure(id, name, exception);
            return;
        }
        runAfterCopied(state);
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
        live.clear();
        waitingForTracking.clear();
        writer.execute(this::deleteSessionFolder);
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
     * a failing disk never leaves the recovered work without a copy. For a document not tracked yet the action waits
     * for {@link #track} (the application asks before it shows the document); never tracked, it never runs. Nothing
     * after shutdown: the action then never runs (the old copy stays, to be offered again).
     */
    public void afterCopied(OpenDocument document, Runnable action) {
        if (shutDown) {
            return;
        }
        Tracked state = tracked.get(document);
        if (state != null) {
            state.afterCopied.add(action);
        } else {
            waitingForTracking.computeIfAbsent(document, ignored -> new ConcurrentLinkedQueue<>()).add(action);
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

    /** Whether {@code document} has something its file does not: changes, or work the editor keeps outside it. */
    private boolean needsCopy(OpenDocument document) {
        return document.isModified() || drafts.holds(document);
    }

    private void discardCopy(OpenDocument document) {
        Tracked state = tracked.get(document);
        if (state == null || !state.hasCopy()) {
            return;
        }
        state.copiedChangeCount = -1;
        state.copiedDraftCount = -1;
        UUID id = document.recoveryId();
        writer.execute(() -> deleteCopy(id));
    }

    /** Runs on the writer. */
    private void deleteCopy(UUID id) {
        try {
            store.delete(id);
        } catch (IOException exception) {
            LOGGER.warn("Could not delete the recovery copy {}", id, exception);
        }
    }

    /** Runs on the writer. */
    private void deleteSessionFolder() {
        try {
            store.deleteSession();
        } catch (IOException exception) {
            LOGGER.warn("Could not delete the recovery folder {}", store.sessionFolder(), exception);
        }
    }

    private void reportFailure(UUID id, String name, Exception exception) {
        if (live.contains(id) && failing.add(id)) {
            loggedFailures.incrementAndGet();
            LOGGER.warn("Could not write the recovery copy of {}", name, exception);
        }
        writeTroubleMayHaveChanged();
    }
}
