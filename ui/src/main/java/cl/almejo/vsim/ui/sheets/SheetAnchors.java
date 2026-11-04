package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.print.SheetPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Where each document's print sheets are held while they are shown on the canvas (sheets spec §0a: showing is
 * anchoring). One per application, shared by every window, so two views of one circuit and the print dialog see the
 * same sheets; the canvas takes an anchor the first time a showing view lays the sheets out tiled
 * ({@link SheetPlan#anchorOf}) and clears it when no view shows them any more; the dialog and the AI's export pass it
 * to {@link SheetPlan#layout} for «Todo el circuito».
 * <p>
 * Session state, not document state: never saved, never an undo step, never marks the circuit modified. A closed
 * document's anchor goes away with it (weak keys, by identity). No Swing here; EDT only.
 */
public final class SheetAnchors {

    /** Weak keys: an anchor must never keep a closed document in memory. {@code CircuitDocument} has identity. */
    private final Map<CircuitDocument, GridPoint> anchors = new WeakHashMap<>();
    private final List<Consumer<CircuitDocument>> listeners = new ArrayList<>();

    /** Where {@code document}'s sheets are held; empty while they are laid out automatically. */
    public Optional<GridPoint> anchor(CircuitDocument document) {
        return Optional.ofNullable(anchors.get(Objects.requireNonNull(document, "document")));
    }

    /**
     * Holds {@code document}'s sheets at {@code anchor}, unless they are already held (the first showing view takes
     * it; the others keep it). Tells the listeners only when it was taken.
     */
    public void take(CircuitDocument document, GridPoint anchor) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(anchor, "anchor");
        if (anchors.putIfAbsent(document, anchor) == null) {
            notifyListeners(document);
        }
    }

    /** Lets {@code document}'s sheets be laid out automatically again; tells the listeners when it had an anchor. */
    public void clear(CircuitDocument document) {
        Objects.requireNonNull(document, "document");
        if (anchors.remove(document) != null) {
            notifyListeners(document);
        }
    }

    /** {@code listener} hears the document whose anchor was taken or cleared. */
    public void addListener(Consumer<CircuitDocument> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Consumer<CircuitDocument> listener) {
        listeners.remove(listener);
    }

    /** For the tests that check every listener is taken back. */
    public int listenerCount() {
        return listeners.size();
    }

    /** How many documents have an anchor (closed ones drop out once collected). */
    int documentCount() {
        return anchors.size();
    }

    private void notifyListeners(CircuitDocument document) {
        for (Consumer<CircuitDocument> listener : List.copyOf(listeners)) {
            listener.accept(document);
        }
    }
}
