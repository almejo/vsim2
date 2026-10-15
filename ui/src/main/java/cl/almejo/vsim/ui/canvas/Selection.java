package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Selected element ids of one canvas. Listeners run only when the set of ids really changes. */
public final class Selection {

    private final Set<ElementId> ids = new LinkedHashSet<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public Set<ElementId> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    public boolean contains(ElementId id) {
        return ids.contains(id);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    public int size() {
        return ids.size();
    }

    /** Replaces the selection with {@code id} alone. */
    public void select(ElementId id) {
        if (ids.size() == 1 && ids.contains(id)) {
            return;
        }
        ids.clear();
        ids.add(id);
        fire();
    }

    public void add(ElementId id) {
        if (ids.add(id)) {
            fire();
        }
    }

    public void remove(ElementId id) {
        if (ids.remove(id)) {
            fire();
        }
    }

    public void toggle(ElementId id) {
        if (!ids.remove(id)) {
            ids.add(id);
        }
        fire();
    }

    /** Replaces the selection with {@code newIds}. */
    public void set(Collection<ElementId> newIds) {
        Set<ElementId> next = new LinkedHashSet<>(newIds);
        if (next.equals(ids)) {
            return;
        }
        ids.clear();
        ids.addAll(next);
        fire();
    }

    public void selectAll(CircuitDocument document) {
        set(document.elements().stream().map(DocumentElement::id).toList());
    }

    public void clear() {
        if (!ids.isEmpty()) {
            ids.clear();
            fire();
        }
    }

    /** Drops the ids that {@code change} reports as removed. */
    public void retain(DocumentChange change) {
        if (ids.removeAll(change.removed())) {
            fire();
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
