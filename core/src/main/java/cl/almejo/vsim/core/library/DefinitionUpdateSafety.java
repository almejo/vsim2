package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Protects wires in saved parent bodies when a library revision changes a child's external interface. */
public final class DefinitionUpdateSafety {
    private DefinitionUpdateSafety() {
    }

    /**
     * The names of the definitions that use {@code definitionId} directly (any deeper use must cross one of these
     * parent bodies first) and that a placed piece reaches. A copy of a parent that nothing placed uses, directly or
     * through other definitions, is left over in the document and never locks its child.
     */
    public static List<String> containingNames(CircuitDocument document, String definitionId) {
        Set<String> reachable = reachableDefinitionIds(document);
        return document.definitions().stream()
                .filter(parent -> reachable.contains(parent.id()) && parent.usedDefinitionIds().contains(definitionId))
                .map(ComponentDefinition::name).toList();
    }

    /** The definitions the placed pieces instantiate, and those they use, at any depth. */
    private static Set<String> reachableDefinitionIds(CircuitDocument document) {
        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(SubcircuitTypes.definitionIds(document.components()));
        while (!pending.isEmpty()) {
            String id = pending.pop();
            if (reachable.add(id)) {
                document.definition(id).ifPresent(definition -> pending.addAll(definition.usedDefinitionIds()));
            }
        }
        return reachable;
    }

    /** Throws before any mutation; name, drawing and internal-logic changes with the same interface remain valid. */
    public static void requireSafe(CircuitDocument document, ComponentDefinition replacement) {
        document.definition(replacement.id()).ifPresent(before -> {
            if (!sameInterface(before, replacement)) {
                List<String> parents = containingNames(document, replacement.id());
                if (!parents.isEmpty()) {
                    throw new UnsafeDefinitionUpdateException(replacement.id(), parents);
                }
            }
        });
    }

    private static boolean sameInterface(ComponentDefinition before, ComponentDefinition after) {
        if (!before.size().equals(after.size()) || before.pinout().size() != after.pinout().size()) {
            return false;
        }
        for (int index = 0; index < before.pinout().size(); index++) {
            DefinitionPin first = before.pinout().get(index);
            DefinitionPin second = after.pinout().get(index);
            if (!first.spec().position().equals(second.spec().position())
                    || first.spec().direction() != second.spec().direction()
                    || first.spec().width() != second.spec().width()) {
                return false;
            }
        }
        return true;
    }
}
