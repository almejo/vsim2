package cl.almejo.vsim.core.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Editable circuit: plain data. Mutate it only through commands so undo/redo stays exact.
 * Not thread-safe: use it from one thread (the Swing event thread in the UI).
 */
public final class CircuitDocument {

    private final Map<ElementId, Component> components = new LinkedHashMap<>();
    private final Map<ElementId, Wire> wires = new LinkedHashMap<>();
    private final Map<ElementId, TextLabel> labels = new LinkedHashMap<>();
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private long nextIdNumber = 1;
    private int batchDepth;
    private boolean changedDuringBatch;
    private long revision;
    private String name = "";

    public String name() {
        return name;
    }

    public void setName(String newName) {
        name = newName == null ? "" : newName;
    }

    /** Grows by one every time listeners are notified: once per change, or once per batch. */
    public long revision() {
        return revision;
    }

    public ElementId newComponentId() {
        return new ElementId("c" + nextIdNumber++);
    }

    public ElementId newWireId() {
        return new ElementId("w" + nextIdNumber++);
    }

    public ElementId newLabelId() {
        return new ElementId("l" + nextIdNumber++);
    }

    public void add(DocumentElement element) {
        if (element(element.id()).isPresent()) {
            throw new IllegalArgumentException("duplicate element id " + element.id());
        }
        reserveId(element.id());
        put(element);
        changed();
    }

    public void replace(DocumentElement element) {
        DocumentElement existing = element(element.id())
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + element.id()));
        if (existing.getClass() != element.getClass()) {
            throw new IllegalArgumentException("cannot replace " + existing.getClass().getSimpleName()
                    + " with " + element.getClass().getSimpleName());
        }
        put(element);
        changed();
    }

    public Optional<DocumentElement> remove(ElementId id) {
        DocumentElement removed = components.remove(id);
        if (removed == null) {
            removed = wires.remove(id);
        }
        if (removed == null) {
            removed = labels.remove(id);
        }
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    public Optional<DocumentElement> element(ElementId id) {
        DocumentElement found = components.get(id);
        if (found == null) {
            found = wires.get(id);
        }
        if (found == null) {
            found = labels.get(id);
        }
        return Optional.ofNullable(found);
    }

    public Optional<Component> component(ElementId id) {
        return Optional.ofNullable(components.get(id));
    }

    public Optional<Wire> wire(ElementId id) {
        return Optional.ofNullable(wires.get(id));
    }

    public Optional<TextLabel> label(ElementId id) {
        return Optional.ofNullable(labels.get(id));
    }

    public List<Component> components() {
        return List.copyOf(components.values());
    }

    public List<Wire> wires() {
        return List.copyOf(wires.values());
    }

    public List<TextLabel> labels() {
        return List.copyOf(labels.values());
    }

    public List<DocumentElement> elements() {
        List<DocumentElement> all = new ArrayList<>(components.values());
        all.addAll(wires.values());
        all.addAll(labels.values());
        return List.copyOf(all);
    }

    public boolean isEmpty() {
        return components.isEmpty() && wires.isEmpty() && labels.isEmpty();
    }

    /** Runs {@code changes} and notifies listeners once at the end (batches may nest). */
    public void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0 && changedDuringBatch) {
                changedDuringBatch = false;
                notifyListeners();
            }
        }
    }

    public void addListener(DocumentListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DocumentListener listener) {
        listeners.remove(listener);
    }

    private void put(DocumentElement element) {
        switch (element) {
            case Component component -> components.put(component.id(), component);
            case Wire wire -> wires.put(wire.id(), wire);
            case TextLabel label -> labels.put(label.id(), label);
        }
    }

    private void reserveId(ElementId id) {
        String digits = id.value().replaceFirst("^\\D+", "");
        if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && digits.length() < 18) {
            nextIdNumber = Math.max(nextIdNumber, Long.parseLong(digits) + 1);
        }
    }

    private void changed() {
        if (batchDepth > 0) {
            changedDuringBatch = true;
        } else {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        revision++;
        listeners.forEach(listener -> listener.documentChanged(this));
    }
}
