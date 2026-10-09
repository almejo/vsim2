package cl.almejo.vsim.core.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Editable circuit: plain data. Mutate it only through commands so undo/redo stays exact.
 * Every element has an order number (paint and file order) that never changes while it stays in the document;
 * {@link #restore} puts a removed element back at its old order.
 * Not thread-safe: use it from one thread (the Swing event thread in the UI).
 */
public final class CircuitDocument {

    private final NavigableMap<Long, Component> components = new TreeMap<>();
    private final NavigableMap<Long, Wire> wires = new TreeMap<>();
    private final NavigableMap<Long, TextLabel> labels = new TreeMap<>();
    private final Map<ElementId, Long> orders = new HashMap<>();
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<ElementId> pendingAdded = new LinkedHashSet<>();
    private final Set<ElementId> pendingRemoved = new LinkedHashSet<>();
    private final Set<ElementId> pendingChanged = new LinkedHashSet<>();
    private long nextIdNumber = 1;
    private long nextOrder;
    private int batchDepth;
    private long revision;
    private String name = "";

    public String name() {
        return name;
    }

    public void setName(String newName) {
        name = newName == null ? "" : newName;
    }

    /** Grows by one every time listeners are notified: once per non-empty change, or once per batch. */
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
        rejectDuplicate(element.id());
        reserveId(element.id());
        put(element, nextOrder++);
        recordAdded(element.id());
    }

    /** Re-adds {@code element} at the paint and file position {@code order} it had (see {@link #orderOf}). */
    public void restore(DocumentElement element, long order) {
        rejectDuplicate(element.id());
        if (order < 0 || components.containsKey(order) || wires.containsKey(order) || labels.containsKey(order)) {
            throw new IllegalArgumentException("order " + order + " is not free");
        }
        reserveId(element.id());
        nextOrder = Math.max(nextOrder, order + 1);
        put(element, order);
        recordAdded(element.id());
    }

    /** Paint and file position of the element; it never changes while the element stays in the document. */
    public long orderOf(ElementId id) {
        Long order = orders.get(id);
        if (order == null) {
            throw new IllegalArgumentException("no element with id " + id);
        }
        return order;
    }

    public void replace(DocumentElement element) {
        DocumentElement existing = element(element.id())
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + element.id()));
        if (existing.getClass() != element.getClass()) {
            throw new IllegalArgumentException("cannot replace " + existing.getClass().getSimpleName()
                    + " with " + element.getClass().getSimpleName());
        }
        put(element, orders.get(element.id()));
        recordChanged(element.id());
    }

    public Optional<DocumentElement> remove(ElementId id) {
        Long order = orders.remove(id);
        if (order == null) {
            return Optional.empty();
        }
        DocumentElement removed = components.remove(order);
        if (removed == null) {
            removed = wires.remove(order);
        }
        if (removed == null) {
            removed = labels.remove(order);
        }
        recordRemoved(id);
        return Optional.ofNullable(removed);
    }

    public Optional<DocumentElement> element(ElementId id) {
        Long order = orders.get(id);
        if (order == null) {
            return Optional.empty();
        }
        DocumentElement found = components.get(order);
        if (found == null) {
            found = wires.get(order);
        }
        if (found == null) {
            found = labels.get(order);
        }
        return Optional.ofNullable(found);
    }

    public Optional<Component> component(ElementId id) {
        Long order = orders.get(id);
        return order == null ? Optional.empty() : Optional.ofNullable(components.get(order));
    }

    public Optional<Wire> wire(ElementId id) {
        Long order = orders.get(id);
        return order == null ? Optional.empty() : Optional.ofNullable(wires.get(order));
    }

    public Optional<TextLabel> label(ElementId id) {
        Long order = orders.get(id);
        return order == null ? Optional.empty() : Optional.ofNullable(labels.get(order));
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
        return orders.isEmpty();
    }

    /** Runs {@code changes} and notifies listeners once at the end (batches may nest). */
    public void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0) {
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

    private void rejectDuplicate(ElementId id) {
        if (orders.containsKey(id)) {
            throw new IllegalArgumentException("duplicate element id " + id);
        }
    }

    private void put(DocumentElement element, long order) {
        orders.put(element.id(), order);
        switch (element) {
            case Component component -> components.put(order, component);
            case Wire wire -> wires.put(order, wire);
            case TextLabel label -> labels.put(order, label);
        }
    }

    private void reserveId(ElementId id) {
        String digits = id.value().replaceFirst("^\\D+", "");
        if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && digits.length() < 18) {
            nextIdNumber = Math.max(nextIdNumber, Long.parseLong(digits) + 1);
        }
    }

    private void recordAdded(ElementId id) {
        if (pendingRemoved.remove(id)) {
            pendingChanged.add(id);
        } else {
            pendingAdded.add(id);
        }
        notifyUnlessBatching();
    }

    private void recordRemoved(ElementId id) {
        if (!pendingAdded.remove(id)) {
            pendingChanged.remove(id);
            pendingRemoved.add(id);
        }
        notifyUnlessBatching();
    }

    private void recordChanged(ElementId id) {
        if (!pendingAdded.contains(id)) {
            pendingChanged.add(id);
        }
        notifyUnlessBatching();
    }

    private void notifyUnlessBatching() {
        if (batchDepth == 0) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        DocumentChange change = new DocumentChange(pendingAdded, pendingRemoved, pendingChanged);
        pendingAdded.clear();
        pendingRemoved.clear();
        pendingChanged.clear();
        if (change.isEmpty()) {
            return;
        }
        revision++;
        listeners.forEach(listener -> listener.documentChanged(this, change));
    }
}
