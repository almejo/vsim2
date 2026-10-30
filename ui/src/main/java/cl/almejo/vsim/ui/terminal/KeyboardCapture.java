package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which part of the circuit, if any, has the keyboard of one editor window (decision 5): a click on a Teclado captures
 * it, and while it lasts the keys typed on any pane of the window go to that part ({@link CapturedKeys}). The capture
 * belongs to the window, not to one pane, so split panes share it; another window on the same circuit has its own.
 * The window ends it on Esc, focus loss, a tool change and a click elsewhere; this class ends it by itself when the
 * part leaves the document (deleted, or its placing undone) and when the window closes ({@link #dispose()}). Restarting
 * the simulation keeps it. EDT only.
 */
public final class KeyboardCapture {

    private final CircuitDocument document;
    private final DocumentListener documentListener = this::documentChanged;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private String capturedId;

    /** A capture for a window on {@code document}; it listens to the document until {@link #dispose()}. */
    public KeyboardCapture(CircuitDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        document.addListener(documentListener);
    }

    /** The keys go to component {@code componentId} (a top-level part that takes typing) from now on. */
    public void capture(String componentId) {
        Objects.requireNonNull(componentId, "componentId");
        if (componentId.equals(capturedId)) {
            return;
        }
        capturedId = componentId;
        changed();
    }

    /** The keys go back to the editor. Nothing happens when nothing is captured. */
    public void release() {
        if (capturedId == null) {
            return;
        }
        capturedId = null;
        changed();
    }

    /** The id of the component that has the keyboard; empty when none has. */
    public Optional<String> captured() {
        return Optional.ofNullable(capturedId);
    }

    /** Whether {@code componentId} has the keyboard now (its painter shows «Escribiendo… Esc para salir»). */
    public boolean isCapturing(String componentId) {
        return componentId.equals(capturedId);
    }

    /** {@code listener} runs after every capture or release; the returned runnable removes it. */
    public Runnable addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    /** The window closes: the capture ends and the document is no longer watched. */
    public void dispose() {
        document.removeListener(documentListener);
        release();
        listeners.clear();
    }

    private void documentChanged(CircuitDocument changedDocument, DocumentChange change) {
        if (capturedId != null && changedDocument.component(new ElementId(capturedId)).isEmpty()) {
            release();
        }
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }
}
