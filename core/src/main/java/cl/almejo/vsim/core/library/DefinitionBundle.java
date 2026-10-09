package cl.almejo.vsim.core.library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** A definition plus every definition it nests (dependencies first): what a library file or a palette entry holds. */
public record DefinitionBundle(ComponentDefinition root, List<ComponentDefinition> dependencies) {

    public DefinitionBundle {
        Objects.requireNonNull(root, "root");
        dependencies = List.copyOf(dependencies);
        Map<String, ComponentDefinition> byId = new HashMap<>();
        for (ComponentDefinition dependency : dependencies) {
            byId.put(dependency.id(), dependency);
        }
        if (byId.containsKey(root.id())) {
            throw new IllegalArgumentException("a definition cannot depend on itself: " + root.id());
        }
        ComponentDefinition checkedRoot = root;
        DefinitionGraph.closure(List.of(root.id()), id -> id.equals(checkedRoot.id())
                ? Optional.of(checkedRoot) : Optional.ofNullable(byId.get(id)));
    }

    public static DefinitionBundle of(ComponentDefinition root, Function<String, Optional<ComponentDefinition>> lookup) {
        List<ComponentDefinition> closure = DefinitionGraph.closure(List.of(root.id()),
                id -> id.equals(root.id()) ? Optional.of(root) : lookup.apply(id));
        return new DefinitionBundle(root, closure.subList(0, closure.size() - 1));
    }

    /** Dependencies, then the root: the order commands and files use. */
    public List<ComponentDefinition> all() {
        List<ComponentDefinition> all = new ArrayList<>(dependencies);
        all.add(root);
        return List.copyOf(all);
    }
}
