package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.sound.TerminalSounds;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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

    TerminalDocument(OpenDocument document, TerminalServices services, TerminalWindowPreferences preferences) {
        this.document = Objects.requireNonNull(document, "document");
        ticker = services.tickers().get();
        feeds = new TerminalFeeds(TerminalFrameSource.of(document));
        windows = new TerminalWindows(document, feeds, services.windows(), preferences);
        recorder = new TerminalOutputRecorder(document, feeds, services.outputWriter(),
                problem -> services.windows().edt().execute(() -> notice(problem)));
        sounds = new TerminalSoundController(document,
                new TerminalSounds(services.sound(), services.windows().nanoClock()), windows::flash);
        document.document().addListener(documentListener);
        updateTicker();
    }

    TerminalWindows windows() {
        return windows;
    }

    /** {@code listener} shows the notices of this document (a window's status bar); the runnable removes it. */
    Runnable addNoticeListener(Consumer<String> listener) {
        noticeListeners.add(listener);
        return () -> noticeListeners.remove(listener);
    }

    /** The last window of the document closed. */
    void dispose() {
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
