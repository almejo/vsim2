package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Part of a circuit (clipboard, drag and drop, template body), with the definitions its instances need
 * (dependencies first). Coordinates are relative: {@link #of} moves the top-left of the
 * fragment's bounds to (0,0). The elements keep the ids they had in their source document.
 */
public record CircuitFragment(List<Component> components, List<Wire> wires, List<TextLabel> labels,
                              List<ComponentDefinition> definitions, List<Frame> frames) {

    private static final Bounds EMPTY_BOUNDS = new Bounds(0, 0, 0, 0);

    public CircuitFragment {
        components = List.copyOf(components);
        wires = List.copyOf(wires);
        labels = List.copyOf(labels);
        definitions = List.copyOf(definitions);
        frames = List.copyOf(frames);
    }

    /** A fragment without frames. */
    public CircuitFragment(List<Component> components, List<Wire> wires, List<TextLabel> labels,
                           List<ComponentDefinition> definitions) {
        this(components, wires, labels, definitions, List.of());
    }

    /** A fragment that needs no definitions (or whose definitions the target already has). */
    public CircuitFragment(List<Component> components, List<Wire> wires, List<TextLabel> labels) {
        this(components, wires, labels, List.of());
    }

    /**
     * The listed elements of {@code document} (unknown ids are ignored), moved so their bounds start at (0,0), with the
     * definitions their instances need.
     */
    public static CircuitFragment of(CircuitDocument document, Collection<ElementId> ids,
                                     ComponentTypeRegistry registry) {
        Set<ElementId> wanted = Set.copyOf(ids);
        List<Component> components = document.components().stream()
                .filter(component -> wanted.contains(component.id())).toList();
        CircuitFragment selected = new CircuitFragment(components,
                document.wires().stream().filter(wire -> wanted.contains(wire.id())).toList(),
                document.labels().stream().filter(label -> wanted.contains(label.id())).toList(),
                document.definitionsUsedBy(components),
                document.frames().stream().filter(frame -> wanted.contains(frame.id())).toList());
        Bounds bounds = selected.bounds(registry);
        return selected.translate(-bounds.x(), -bounds.y());
    }

    /**
     * Types for this fragment's elements: {@code base} when it carries no definitions, otherwise {@code base} extended
     * with its own sub-circuit types. {@code base} wins on equal ids, like a paste does.
     */
    public ComponentTypeRegistry registryFor(ComponentTypeRegistry base) {
        if (definitions.isEmpty()) {
            return base;
        }
        Map<String, ComponentType> types = new HashMap<>();
        for (ComponentDefinition definition : definitions) {
            types.put(SubcircuitTypes.typeIdOf(definition.id()), SubcircuitTypes.typeOf(definition));
        }
        return base.extendedWith(typeId -> Optional.ofNullable(types.get(typeId)));
    }

    public List<DocumentElement> elements() {
        List<DocumentElement> all = new ArrayList<>(components);
        all.addAll(wires);
        all.addAll(labels);
        all.addAll(frames);
        return List.copyOf(all);
    }

    /** Union of the element bounds; {@code Bounds(0, 0, 0, 0)} for an empty fragment. */
    public Bounds bounds(ComponentTypeRegistry registry) {
        return ElementBounds.union(elements(), registry).orElse(EMPTY_BOUNDS);
    }

    public boolean isEmpty() {
        return components.isEmpty() && wires.isEmpty() && labels.isEmpty() && frames.isEmpty();
    }

    public CircuitFragment translate(int deltaX, int deltaY) {
        return new CircuitFragment(
                components.stream().map(component -> component.translate(deltaX, deltaY)).toList(),
                wires.stream().map(wire -> wire.translate(deltaX, deltaY)).toList(),
                labels.stream().map(label -> label.translate(deltaX, deltaY)).toList(),
                definitions, frames.stream().map(frame -> frame.translate(deltaX, deltaY)).toList());
    }
}
