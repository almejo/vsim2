package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Optional;
import java.util.Set;

/**
 * What is being moved or placed: the targets to skip ({@code excludedIds}: the moved elements; empty for a Ctrl copy
 * or a ghost), the box of its components and frames before the move (empty when it has none) and the registry that
 * resolves its component types (a paste's own registry).
 */
public record MovingSet(Set<ElementId> excludedIds, Optional<Bounds> box, ComponentTypeRegistry registry) {

    public MovingSet {
        excludedIds = Set.copyOf(excludedIds);
    }
}
