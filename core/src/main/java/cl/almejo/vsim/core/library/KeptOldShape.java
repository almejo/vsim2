package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.ElementId;

import java.util.List;
import java.util.Objects;

/**
 * A placed piece of {@code definitionId} that could not take {@code newPinNames} where the definition puts them
 * (that spot of its edge lands on a wire or a pin, spec §6.2 a): it keeps its box and its other pins where they were,
 * and those pins go to a spot of its own edge that touches nothing, so no net is joined silently. Problemas lists it.
 */
public record KeptOldShape(ElementId instance, String definitionId, List<String> newPinNames) {
    public KeptOldShape {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(definitionId, "definitionId");
        newPinNames = List.copyOf(newPinNames);
    }
}
