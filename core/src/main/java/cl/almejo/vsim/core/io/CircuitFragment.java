package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Part of a circuit (clipboard, drag and drop). Coordinates are relative: {@link #of} moves the top-left of the
 * fragment's bounds to (0,0). The elements keep the ids they had in their source document.
 */
public record CircuitFragment(List<Component> components, List<Wire> wires, List<TextLabel> labels) {

    private static final Bounds EMPTY_BOUNDS = new Bounds(0, 0, 0, 0);

    public CircuitFragment {
        components = List.copyOf(components);
        wires = List.copyOf(wires);
        labels = List.copyOf(labels);
    }

    /** The listed elements of {@code document} (unknown ids are ignored), moved so their bounds start at (0,0). */
    public static CircuitFragment of(CircuitDocument document, Collection<ElementId> ids,
                                     ComponentTypeRegistry registry) {
        Set<ElementId> wanted = Set.copyOf(ids);
        CircuitFragment selected = new CircuitFragment(
                document.components().stream().filter(component -> wanted.contains(component.id())).toList(),
                document.wires().stream().filter(wire -> wanted.contains(wire.id())).toList(),
                document.labels().stream().filter(label -> wanted.contains(label.id())).toList());
        Bounds bounds = selected.bounds(registry);
        return selected.translate(-bounds.x(), -bounds.y());
    }

    public List<DocumentElement> elements() {
        List<DocumentElement> all = new ArrayList<>(components);
        all.addAll(wires);
        all.addAll(labels);
        return List.copyOf(all);
    }

    /** Union of the element bounds; {@code Bounds(0, 0, 0, 0)} for an empty fragment. */
    public Bounds bounds(ComponentTypeRegistry registry) {
        return ElementBounds.union(elements(), registry).orElse(EMPTY_BOUNDS);
    }

    public boolean isEmpty() {
        return components.isEmpty() && wires.isEmpty() && labels.isEmpty();
    }

    public CircuitFragment translate(int deltaX, int deltaY) {
        return new CircuitFragment(
                components.stream().map(component -> component.translate(deltaX, deltaY)).toList(),
                wires.stream().map(wire -> wire.translate(deltaX, deltaY)).toList(),
                labels.stream().map(label -> label.translate(deltaX, deltaY)).toList());
    }
}
