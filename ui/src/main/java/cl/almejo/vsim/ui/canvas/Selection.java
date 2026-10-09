package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Selected element ids of one canvas. Single element in M2; a set so M3 can add multi-selection. */
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

    public void select(ElementId id) {
        if (ids.size() == 1 && ids.contains(id)) {
            return;
        }
        ids.clear();
        ids.add(id);
        fire();
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

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
