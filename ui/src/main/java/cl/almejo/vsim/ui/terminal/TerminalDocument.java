package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.sound.TerminalSounds;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Terminal features of one document, shared by all its editor windows: the frames ({@link TerminalFeeds}), the
 * windows ({@link TerminalWindows}), «Guardar salida» ({@link TerminalOutputRecorder}) and the sounds
 * ({@link TerminalSoundController}), all polled on one frame ticker that runs only while the circuit has a Terminal.
 * EDT only.
 */
final class TerminalDocument {

    private final OpenDocument document;
    private final FrameTicker ticker;
    private final TerminalFeeds feeds;
    private final TerminalWindows windows;
    private final TerminalOutputRecorder recorder;
    private final TerminalSoundController sounds;
    private final List<Consumer<String>> noticeListeners = new CopyOnWriteArrayList<>();
    private final DocumentListener documentListener = this::documentChanged;
    private final Runnable placeListener = this::placeChanged;
    /** The instance whose Terminals a component tab's features read (always empty on the circuit's tab). */
    private Optional<String> shownContext;
    /** The editor window that owns the question about the output file; the last view installed supplies it. */
    private final AtomicReference<Supplier<java.awt.Component>> ownerWindow = new AtomicReference<>(() -> null);

    /** The features of {@code document}; {@code outputClaims} are the files every open circuit records to. */
    TerminalDocument(OpenDocument document, TerminalServices services, TerminalWindowPreferences preferences,
                     TerminalOutputClaims outputClaims) {
        this.document = Objects.requireNonNull(document, "document");
        ticker = services.tickers().get();
        feeds = new TerminalFeeds(TerminalFrameSource.of(document));
        windows = new TerminalWindows(document, feeds, services.windows(), preferences);
        recorder = new TerminalOutputRecorder(document, feeds, new TerminalOutputRecorder.Services(
                services.outputWriter(), services.later(), services.outputConfirmation(), outputClaims,
                () -> ownerWindow.get().get()),
                problem -> services.windows().edt().execute(() -> notice(problem)));
        sounds = new TerminalSoundController(document,
                new TerminalSounds(services.sound(), services.windows().nanoClock()), windows::flash);
        document.document().addListener(documentListener);
        shownContext = document.contextPath();
        if (document.isBody()) {
            document.addChangeListener(placeListener); // only a component tab changes the instance it shows
        }
        updateTicker();
    }

    /**
     * {@code owner} supplies the window that owns the question about a saved output file; the runnable gives it back
     * (when it is still the current one).
     */
    Runnable setOwnerWindow(Supplier<java.awt.Component> owner) {
        ownerWindow.set(owner);
        return () -> ownerWindow.compareAndSet(owner, () -> null);
    }

    TerminalWindows windows() {
        return windows;
    }

    TerminalOutputRecorder recorder() {
        return recorder;
    }

    /** {@code listener} shows the notices of this document (a window's status bar); the runnable removes it. */
    Runnable addNoticeListener(Consumer<String> listener) {
        noticeListeners.add(listener);
        return () -> noticeListeners.remove(listener);
    }

    /** The last window of the document closed. */
    void dispose() {
        document.removeChangeListener(placeListener);
        document.document().removeListener(documentListener);
        ticker.stop();
        windows.dispose();
        recorder.dispose();
        sounds.dispose();
        feeds.dispose();
    }

    private void notice(String text) {
        noticeListeners.forEach(listener -> listener.accept(text));
    }

    private void tick() {
        feeds.poll();
        sounds.poll(document.latestSnapshot());
        windows.tick();
    }

    /**
     * A component tab now shows another instance (spec §10.7): its Terminal windows showed the instance it left, so
     * they close (opening one again shows the new instance), and what reads its Terminals asks the new instance.
     */
    private void placeChanged() {
        Optional<String> now = document.contextPath();
        if (now.equals(shownContext)) {
            return;
        }
        Optional<String> left = shownContext;
        shownContext = now;
        windows.closeAll();
        feeds.askAgain(componentId -> left.ifPresent(path -> document.parent().ifPresent(circuit ->
                circuit.removeTerminalRequest(InstancePath.child(path, componentId)))));
    }

    private void documentChanged(CircuitDocument changed, DocumentChange change) {
        updateTicker();
    }

    private void updateTicker() {
        boolean needed = sounds.hasTerminals() || !feeds.isEmpty();
        if (needed && !ticker.isRunning()) {
            ticker.start(this::tick);
        } else if (!needed && ticker.isRunning()) {
            ticker.stop();
        }
    }
}
