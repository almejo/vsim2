package cl.almejo.vsim.core.library;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Walks "definition uses definition" edges. Results list dependencies before the definitions that use them. */
public final class DefinitionGraph {

    private DefinitionGraph() {
    }

    /** The definitions {@code rootIds} need, roots included; an unknown id or a cycle throws IllegalArgumentException. */
    public static List<ComponentDefinition> closure(Collection<String> rootIds,
                                                    Function<String, Optional<ComponentDefinition>> lookup) {
        return walk(rootIds, lookup, false);
    }

    /** Like {@link #closure} but unknown ids are skipped; a cycle still throws. */
    public static List<ComponentDefinition> knownClosure(Collection<String> rootIds,
                                                         Function<String, Optional<ComponentDefinition>> lookup) {
        return walk(rootIds, lookup, true);
    }

    private static List<ComponentDefinition> walk(Collection<String> rootIds,
                                                  Function<String, Optional<ComponentDefinition>> lookup,
                                                  boolean skipUnknown) {
        List<ComponentDefinition> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (String rootId : rootIds) {
            visit(rootId, lookup, skipUnknown, done, new LinkedHashSet<>(), ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(String id, Function<String, Optional<ComponentDefinition>> lookup, boolean skipUnknown,
                              Set<String> done, LinkedHashSet<String> path, List<ComponentDefinition> ordered) {
        if (done.contains(id)) {
            return;
        }
        if (!path.add(id)) {
            throw new IllegalArgumentException("recursive definition: " + String.join(" > ", path) + " > " + id);
        }
        Optional<ComponentDefinition> found = lookup.apply(id);
        if (found.isEmpty()) {
            if (!skipUnknown) {
                throw new IllegalArgumentException("unknown definition " + id);
            }
            path.remove(id);
            return;
        }
        for (String used : found.get().usedDefinitionIds()) {
            visit(used, lookup, skipUnknown, done, path, ordered);
        }
        path.remove(id);
        done.add(id);
        ordered.add(found.get());
    }
}
