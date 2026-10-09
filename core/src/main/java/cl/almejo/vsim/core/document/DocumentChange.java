package cl.almejo.vsim.core.document;

import java.util.Set;

/**
 * Ids affected by one notification. A batch reports the union of its changes; an element both added and removed
 * inside one batch is reported in neither, and one removed and added again is reported as changed.
 */
public record DocumentChange(Set<ElementId> added, Set<ElementId> removed, Set<ElementId> changed) {

    public static final DocumentChange EMPTY = new DocumentChange(Set.of(), Set.of(), Set.of());

    public DocumentChange {
        added = Set.copyOf(added);
        removed = Set.copyOf(removed);
        changed = Set.copyOf(changed);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }
}
