package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Takes a document from one state to another in one step: the difference between {@code before} and {@code after}
 * (elements at their orders, definitions, probes and guides), computed once when the command is made. It is the
 * all-or-nothing commit of a change tried first on a {@link CircuitDocument#copy() copy} (the AI's batches, A§6.2):
 * the copy took every step, this command only writes the result.
 *
 * <p>{@link #apply} and {@link #revert} first check that the document is exactly the state they start from (the
 * elements they remove or replace, the orders they fill) and throw IllegalStateException, changing nothing, when it is
 * not; the listeners hear the whole change once.
 */
public final class ReplaceContentCommand implements Command {

    /** One element on one side of the change, with its paint and file order. */
    private record Placed(DocumentElement element, long order) {
    }

    private final String description;
    /** Elements only {@code before} has, or that {@code after} has at another order or of another kind. */
    private final List<Placed> removed;
    /** Elements only {@code after} has, or that it has at another order or of another kind. */
    private final List<Placed> added;
    /** Elements both have at the same order, with other contents: before → after. */
    private final Map<ElementId, DocumentElement> changedBefore;
    private final Map<ElementId, DocumentElement> changedAfter;
    private final Map<String, ComponentDefinition> definitionsBefore;
    private final Map<String, ComponentDefinition> definitionsAfter;
    private final List<Probe> probesBefore;
    private final List<Probe> probesAfter;
    private final List<Guide> guidesBefore;
    private final List<Guide> guidesAfter;

    private ReplaceContentCommand(String description, Difference difference, CircuitDocument before,
                                  CircuitDocument after) {
        this.description = description;
        this.removed = List.copyOf(difference.removed);
        this.added = List.copyOf(difference.added);
        this.changedBefore = Collections.unmodifiableMap(difference.changedBefore);
        this.changedAfter = Collections.unmodifiableMap(difference.changedAfter);
        this.definitionsBefore = differingDefinitions(before, after);
        this.definitionsAfter = differingDefinitions(after, before);
        this.probesBefore = before.probes();
        this.probesAfter = after.probes();
        this.guidesBefore = before.guides();
        this.guidesAfter = after.guides();
    }

    /**
     * The command that turns {@code before} into {@code after}; {@code description} is its i18n key. Neither document
     * is changed nor kept: the difference is copied now.
     */
    public static ReplaceContentCommand between(CircuitDocument before, CircuitDocument after, String description) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(description, "description");
        return new ReplaceContentCommand(description, Difference.of(before, after), before, after);
    }

    /** The ids that applying adds: elements new in the after state (ids that changed order or kind are not new). */
    public Set<ElementId> addedIds() {
        Set<ElementId> ids = idsOf(added);
        ids.removeAll(idsOf(removed));
        return Collections.unmodifiableSet(ids);
    }

    /** The ids whose element applying changes: its contents, or its order or kind. */
    public Set<ElementId> changedIds() {
        Set<ElementId> ids = new LinkedHashSet<>(changedAfter.keySet());
        Set<ElementId> moved = idsOf(added);
        moved.retainAll(idsOf(removed));
        ids.addAll(moved);
        return Collections.unmodifiableSet(ids);
    }

    /** The ids that applying removes for good. */
    public Set<ElementId> removedIds() {
        Set<ElementId> ids = idsOf(removed);
        ids.removeAll(idsOf(added));
        return Collections.unmodifiableSet(ids);
    }

    @Override
    public boolean changesNothing() {
        return removed.isEmpty() && added.isEmpty() && changedAfter.isEmpty() && definitionsBefore.isEmpty()
                && definitionsAfter.isEmpty() && probesBefore.equals(probesAfter) && guidesBefore.equals(guidesAfter);
    }

    @Override
    public void apply(CircuitDocument document) {
        write(document, removed, changedBefore, changedAfter, added, definitionsBefore, definitionsAfter);
        document.setProbes(probesAfter);
        document.setGuides(guidesAfter);
    }

    @Override
    public void revert(CircuitDocument document) {
        write(document, added, changedAfter, changedBefore, removed, definitionsAfter, definitionsBefore);
        document.setProbes(probesBefore);
        document.setGuides(guidesBefore);
    }

    @Override
    public String description() {
        return description;
    }

    /**
     * Takes out {@code takenOut}, turns {@code replacedFrom} into {@code replacedTo} and puts in {@code putIn}, in one
     * batch, after checking that the document holds exactly what it starts from.
     */
    private static void write(CircuitDocument document, List<Placed> takenOut,
                              Map<ElementId, DocumentElement> replacedFrom, Map<ElementId, DocumentElement> replacedTo,
                              List<Placed> putIn, Map<String, ComponentDefinition> definitionsFrom,
                              Map<String, ComponentDefinition> definitionsTo) {
        requireStartingState(document, takenOut, replacedFrom, putIn);
        document.batch(() -> {
            takenOut.forEach(placed -> document.remove(placed.element().id()));
            replacedTo.values().forEach(document::replace);
            putIn.forEach(placed -> document.restore(placed.element(), placed.order()));
            definitionsFrom.keySet().stream()
                    .filter(id -> !definitionsTo.containsKey(id))
                    .forEach(document::removeDefinition);
            definitionsTo.values().forEach(document::putDefinition);
        });
    }

    private static void requireStartingState(CircuitDocument document, List<Placed> takenOut,
                                             Map<ElementId, DocumentElement> replacedFrom, List<Placed> putIn) {
        for (Placed placed : takenOut) {
            requireHolds(document, placed.element());
            if (document.orderOf(placed.element().id()) != placed.order()) {
                throw new IllegalStateException(placed.element().id() + " is no longer at order " + placed.order());
            }
        }
        replacedFrom.values().forEach(element -> requireHolds(document, element));
        Set<ElementId> freedIds = idsOf(takenOut);
        Set<Long> takenOrders = new HashSet<>();
        document.elements().forEach(element -> takenOrders.add(document.orderOf(element.id())));
        takenOut.forEach(placed -> takenOrders.remove(placed.order()));
        for (Placed placed : putIn) {
            ElementId id = placed.element().id();
            if (!freedIds.contains(id) && document.element(id).isPresent()) {
                throw new IllegalStateException("the document already has " + id);
            }
            if (takenOrders.contains(placed.order())) {
                throw new IllegalStateException("order " + placed.order() + " for " + id + " is taken");
            }
        }
    }

    private static void requireHolds(CircuitDocument document, DocumentElement element) {
        if (!document.element(element.id()).equals(Optional.of(element))) {
            throw new IllegalStateException("the document no longer holds " + element.id() + " as this change expects");
        }
    }

    /** The definitions of {@code side} that {@code other} lacks or has with other contents. */
    private static Map<String, ComponentDefinition> differingDefinitions(CircuitDocument side, CircuitDocument other) {
        Map<String, ComponentDefinition> differing = new LinkedHashMap<>();
        for (ComponentDefinition definition : side.definitions()) {
            if (!other.definition(definition.id()).equals(Optional.of(definition))) {
                differing.put(definition.id(), definition);
            }
        }
        return Collections.unmodifiableMap(differing);
    }

    private static Set<ElementId> idsOf(List<Placed> placed) {
        Set<ElementId> ids = new LinkedHashSet<>();
        placed.forEach(entry -> ids.add(entry.element().id()));
        return ids;
    }

    /** What differs between the elements of two states. */
    private static final class Difference {

        private final List<Placed> removed = new ArrayList<>();
        private final List<Placed> added = new ArrayList<>();
        private final Map<ElementId, DocumentElement> changedBefore = new LinkedHashMap<>();
        private final Map<ElementId, DocumentElement> changedAfter = new LinkedHashMap<>();

        static Difference of(CircuitDocument before, CircuitDocument after) {
            Difference difference = new Difference();
            for (DocumentElement element : before.elements()) {
                difference.compare(element, before.orderOf(element.id()), after);
            }
            for (DocumentElement element : after.elements()) {
                if (before.element(element.id()).isEmpty()) {
                    difference.added.add(new Placed(element, after.orderOf(element.id())));
                }
            }
            return difference;
        }

        private void compare(DocumentElement element, long order, CircuitDocument after) {
            Optional<DocumentElement> counterpart = after.element(element.id());
            if (counterpart.isEmpty()) {
                removed.add(new Placed(element, order));
                return;
            }
            long orderAfter = after.orderOf(element.id());
            if (orderAfter != order || counterpart.get().getClass() != element.getClass()) {
                removed.add(new Placed(element, order));
                added.add(new Placed(counterpart.get(), orderAfter));
            } else if (!counterpart.get().equals(element)) {
                changedBefore.put(element.id(), element);
                changedAfter.put(element.id(), counterpart.get());
            }
        }
    }
}
