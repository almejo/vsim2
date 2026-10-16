package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How the wires follow one move (spec M6.7 §2–§4), computed by {@link WireFollow#plan}: wires translated whole with
 * the moved elements, wires that keep their id with new endpoints, new wires (ids already reserved in the document)
 * and removed wires, plus the accidental contacts the move makes. Sets keep their (document) order.
 */
public record FollowPlan(Set<ElementId> movedWholeWireIds, List<Wire> replacedWires, List<Wire> addedWires,
                         Set<ElementId> removedWireIds, List<NewContact> newContacts) {

    public static final FollowPlan EMPTY = new FollowPlan(Set.of(), List.of(), List.of(), Set.of(), List.of());

    public FollowPlan {
        movedWholeWireIds = Collections.unmodifiableSet(new LinkedHashSet<>(movedWholeWireIds));
        replacedWires = List.copyOf(replacedWires);
        addedWires = List.copyOf(addedWires);
        removedWireIds = Collections.unmodifiableSet(new LinkedHashSet<>(removedWireIds));
        newContacts = List.copyOf(newContacts);
    }

    /** True when no wire changes and the move makes no new contact. */
    public boolean isEmpty() {
        return movedWholeWireIds.isEmpty() && replacedWires.isEmpty() && addedWires.isEmpty()
                && removedWireIds.isEmpty() && newContacts.isEmpty();
    }

    /** The wires the plan creates or changes (moved whole, replaced, added), by id, in that order. */
    public Set<ElementId> changedWireIds() {
        Set<ElementId> changed = new LinkedHashSet<>(movedWholeWireIds);
        replacedWires.forEach(wire -> changed.add(wire.id()));
        addedWires.forEach(wire -> changed.add(wire.id()));
        return Collections.unmodifiableSet(changed);
    }
}
