package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.InstancePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where each definition is used (spec §10.5): every user-component instance reachable from the document's components
 * through the bodies of their definitions, as the flattener names it ({@code c5/c2}). A body tab shows the values of
 * one of these paths, its context.
 */
public final class InstancePaths {

    /** How many instances deep the walk goes: deeper nesting is not listed (the simulation refuses it anyway). */
    public static final int MAXIMUM_DEPTH = 32;

    /**
     * One use of {@code definitionId}: its simulation {@code path} and the ids of the instances it goes through, from
     * the document's component down to the instance itself.
     */
    public record InstanceUse(String path, String definitionId, List<ElementId> chain) {
        public InstanceUse {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(definitionId, "definitionId");
            chain = List.copyOf(chain);
        }
    }

    private InstancePaths() {
    }

    /** Every use, parents before their children, in document order within a level. */
    public static List<InstanceUse> of(CircuitDocument document) {
        List<InstanceUse> uses = new ArrayList<>();
        collect(document, document.components(), List.of(), uses);
        return List.copyOf(uses);
    }

    /** The uses of {@code definitionId}, in the order of {@link #of}; empty for a definition nothing places. */
    public static List<InstanceUse> usesOf(CircuitDocument document, String definitionId) {
        return of(document).stream().filter(use -> use.definitionId().equals(definitionId)).toList();
    }

    /** True when a definition that a placed piece reaches has {@code definitionId} in its body. */
    public static boolean isUsedInsideAnother(CircuitDocument document, String definitionId) {
        return !DefinitionUpdateSafety.containingNames(document, definitionId).isEmpty();
    }

    private static void collect(CircuitDocument document, List<Component> components, List<ElementId> above,
                                List<InstanceUse> uses) {
        if (above.size() >= MAXIMUM_DEPTH) {
            return;
        }
        for (Component component : components) {
            Optional<ComponentDefinition> definition = SubcircuitTypes.definitionIdOf(component.typeId())
                    .flatMap(document::definition);
            if (definition.isEmpty()) {
                continue;
            }
            List<ElementId> chain = new ArrayList<>(above);
            chain.add(component.id());
            uses.add(new InstanceUse(pathOf(chain), definition.get().id(), chain));
            collect(document, definition.get().body().components(), chain, uses);
        }
    }

    private static String pathOf(List<ElementId> chain) {
        String path = chain.getFirst().value();
        for (ElementId id : chain.subList(1, chain.size())) {
            path = InstancePath.child(path, id.value());
        }
        return path;
    }
}
