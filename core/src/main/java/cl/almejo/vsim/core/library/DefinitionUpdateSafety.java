package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.List;

/** Protects wires in saved parent bodies when a library revision changes a child's external interface. */
public final class DefinitionUpdateSafety {
    private DefinitionUpdateSafety() {
    }

    /** Direct parents are sufficient: any deeper use must cross one of these parent bodies first. */
    public static List<String> containingNames(CircuitDocument document, String definitionId) {
        return document.definitions().stream().filter(parent -> parent.usedDefinitionIds().contains(definitionId))
                .map(ComponentDefinition::name).toList();
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

    /**
     * A library/logic replacement currently has no simultaneous wire-follow plan. Reject geometry changes while
     * there are live pieces; geometry changes made through the shape command have their own checked planner.
     */
    public static void requireSafeReplacement(CircuitDocument document, ComponentDefinition replacement) {
        requireSafe(document, replacement);
        document.definition(replacement.id()).ifPresent(before -> {
            if (!sameInterface(before, replacement)) {
                List<String> instances = document.components().stream()
                        .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(replacement.id())))
                        .map(component -> component.label().isBlank() ? component.id().value() : component.label())
                        .toList();
                if (!instances.isEmpty()) {
                    throw new UnsafeDefinitionUpdateException(replacement.id(), instances,
                            "appearance.update.unsafeGeometry");
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
