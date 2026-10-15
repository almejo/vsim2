package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionGraph;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final NavigableMap<Long, Frame> frames = new TreeMap<>();
    private final Map<ElementId, Long> orders = new HashMap<>();
    private final Map<String, ComponentDefinition> definitions = new LinkedHashMap<>();
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<ElementId> pendingAdded = new LinkedHashSet<>();
    private final Set<ElementId> pendingRemoved = new LinkedHashSet<>();
    private final Set<ElementId> pendingChanged = new LinkedHashSet<>();
    private long nextIdNumber = 1;
    private long nextOrder;
    private int batchDepth;
    private long revision;
    private String name = "";
    private final List<Runnable> probeListeners = new CopyOnWriteArrayList<>();
    private List<Probe> probes = List.of();
    private long nextProbeNumber = 1;

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

    public ElementId newFrameId() {
        return new ElementId("f" + nextIdNumber++);
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
        if (order < 0 || components.containsKey(order) || wires.containsKey(order) || labels.containsKey(order)
                || frames.containsKey(order)) {
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
        if (removed == null) {
            removed = frames.remove(order);
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
        if (found == null) {
            found = frames.get(order);
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

    public Optional<Frame> frame(ElementId id) {
        Long order = orders.get(id);
        return order == null ? Optional.empty() : Optional.ofNullable(frames.get(order));
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

    public List<Frame> frames() {
        return List.copyOf(frames.values());
    }

    public List<DocumentElement> elements() {
        List<DocumentElement> all = new ArrayList<>(components.values());
        all.addAll(wires.values());
        all.addAll(labels.values());
        all.addAll(frames.values());
        return List.copyOf(all);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public Optional<ComponentDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /** Every definition the document holds, in the order they were first added. */
    public List<ComponentDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    /**
     * Adds or replaces a definition; its instances, and the instances of definitions that nest it, are reported as
     * changed. A definition that would make the hierarchy recursive throws IllegalArgumentException and changes nothing.
     * Use it through commands only.
     */
    public Optional<ComponentDefinition> putDefinition(ComponentDefinition definition) {
        DefinitionGraph.knownClosure(List.of(definition.id()),
                id -> id.equals(definition.id()) ? Optional.of(definition) : definition(id));
        ComponentDefinition previous = definitions.put(definition.id(), definition);
        // Computed after the put: which definitions nest this id depends only on the others, and a definition that
        // names a missing id only reaches it once it is in the map.
        recordChangedInstances(affectedInstances(definition.id()));
        return Optional.ofNullable(previous);
    }

    /**
     * Removes a definition; its instances, and the instances of definitions that nest it, are reported as changed.
     * Use it through commands only.
     */
    public Optional<ComponentDefinition> removeDefinition(String id) {
        if (!definitions.containsKey(id)) {
            return Optional.empty();
        }
        // Computed before the removal: afterwards the nesting definitions no longer reach it.
        List<ElementId> affected = affectedInstances(id);
        ComponentDefinition removed = definitions.remove(id);
        recordChangedInstances(affected);
        return Optional.of(removed);
    }

    /** The definitions {@code users} instantiate, directly or nested, dependencies first; unknown ids are skipped. */
    public List<ComponentDefinition> definitionsUsedBy(Collection<Component> users) {
        return DefinitionGraph.knownClosure(SubcircuitTypes.definitionIds(users), this::definition);
    }

    /** What a saved file embeds: the definitions used by this document's components. */
    public List<ComponentDefinition> usedDefinitions() {
        return definitionsUsedBy(components.values());
    }

    /** Instances of {@code definitionId} and of every definition that nests it, with the current definitions. */
    private List<ElementId> affectedInstances(String definitionId) {
        Set<String> affectedTypeIds = new HashSet<>();
        affectedTypeIds.add(SubcircuitTypes.typeIdOf(definitionId));
        for (ComponentDefinition candidate : definitions.values()) {
            boolean nests = DefinitionGraph.knownClosure(List.of(candidate.id()), this::definition).stream()
                    .anyMatch(used -> used.id().equals(definitionId));
            if (nests) {
                affectedTypeIds.add(SubcircuitTypes.typeIdOf(candidate.id()));
            }
        }
        return components.values().stream()
                .filter(component -> affectedTypeIds.contains(component.typeId()))
                .map(Component::id)
                .toList();
    }

    private void recordChangedInstances(List<ElementId> instances) {
        batch(() -> instances.forEach(this::recordChanged));
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

    /** The waveform channels, in row order. They are not elements: changing them never bumps {@link #revision()}. */
    public List<Probe> probes() {
        return probes;
    }

    /**
     * Replaces the waveform channels and notifies the probe listeners (never the document listeners, so nothing is
     * recompiled). Duplicate ids throw IllegalArgumentException and change nothing; an equal list notifies nobody.
     * Use it through commands only.
     */
    public void setProbes(List<Probe> newProbes) {
        List<Probe> copy = List.copyOf(newProbes);
        Set<String> ids = new HashSet<>();
        for (Probe probe : copy) {
            if (!ids.add(probe.id())) {
                throw new IllegalArgumentException("duplicate probe id " + probe.id());
            }
        }
        if (copy.equals(probes)) {
            return;
        }
        probes = copy;
        copy.forEach(probe -> reserveProbeId(probe.id()));
        probeListeners.forEach(Runnable::run);
    }

    /** {@code p1}, {@code p2}, …, never one already used by a probe of this document. */
    public String newProbeId() {
        return "p" + nextProbeNumber++;
    }

    public void addProbeListener(Runnable listener) {
        probeListeners.add(listener);
    }

    public void removeProbeListener(Runnable listener) {
        probeListeners.remove(listener);
    }

    /** For tests: views and extensions must remove the probe listeners they add when disposed. */
    public int probeListenerCount() {
        return probeListeners.size();
    }

    private void reserveProbeId(String id) {
        String digits = id.substring(1);
        if (id.charAt(0) == 'p' && !digits.isEmpty() && digits.length() < 18
                && digits.chars().allMatch(Character::isDigit)) {
            nextProbeNumber = Math.max(nextProbeNumber, Long.parseLong(digits) + 1);
        }
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
            case Frame frame -> frames.put(order, frame);
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
