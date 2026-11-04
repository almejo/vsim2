package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which piece of a document's own circuit holds a custom component, directly or inside others: where a note about
 * something inside that component's body takes the user, since VSim cannot open a body yet. Answers come from the
 * document's embedded definitions; each definition is looked into once.
 */
final class HoldingInstances {

    private final CircuitDocument document;
    private final Map<String, Set<String>> heldBy = new HashMap<>();

    HoldingInstances(CircuitDocument document) {
        this.document = document;
    }

    /**
     * The first piece of the document's circuit (in its order) that is an instance of {@code definitionId} or holds
     * one at any depth; empty when none does.
     */
    Optional<ElementId> firstHolderOf(String definitionId) {
        for (Component component : document.components()) {
            Optional<String> held = SubcircuitTypes.definitionIdOf(component.typeId());
            if (held.isPresent() && definitionsInside(held.get()).contains(definitionId)) {
                return Optional.of(component.id());
            }
        }
        return Optional.empty();
    }

    /** {@code definitionId} itself and every definition its body uses, at any depth. */
    private Set<String> definitionsInside(String definitionId) {
        Set<String> known = heldBy.get(definitionId);
        if (known != null) {
            return known;
        }
        Set<String> inside = new HashSet<>();
        collect(definitionId, inside);
        heldBy.put(definitionId, inside);
        return inside;
    }

    private void collect(String definitionId, Set<String> inside) {
        if (!inside.add(definitionId)) {
            return;
        }
        document.definition(definitionId).map(ComponentDefinition::body).ifPresent(body ->
                body.components().forEach(component -> SubcircuitTypes.definitionIdOf(component.typeId())
                        .ifPresent(nested -> collect(nested, inside))));
    }
}
