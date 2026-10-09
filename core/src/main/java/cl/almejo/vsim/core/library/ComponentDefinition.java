package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A user-defined component (template): a box of {@code size} with {@code pinout}, simulated as {@code body}.
 * Immutable. The body never carries definitions; nested ones live next to it (document, file, bundle).
 */
public record ComponentDefinition(String id, String name, String description, GridSize size,
                                  List<DefinitionPin> pinout, CircuitFragment body) {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public ComponentDefinition {
        Objects.requireNonNull(id, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("definition id must be a slug: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("definition " + id + " needs a name");
        }
        name = name.strip();
        description = description == null ? "" : description;
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(body, "body");
        pinout = List.copyOf(pinout);
        if (pinout.isEmpty()) {
            throw new IllegalArgumentException("definition " + id + " has no pins");
        }
        if (!body.definitions().isEmpty()) {
            throw new IllegalArgumentException("the body of " + id + " must not carry definitions");
        }
        Set<ElementId> sources = new HashSet<>();
        for (DefinitionPin pin : pinout) {
            if (!sources.add(pin.source())) {
                throw new IllegalArgumentException("pin source used twice in " + id + ": " + pin.source());
            }
            String expected = pin.spec().direction() == PinDirection.OUTPUT
                    ? InterfacePins.OUTPUT_PIN : InterfacePins.INPUT_PIN;
            if (pin.spec().direction() == PinDirection.BIDIRECTIONAL || body.components().stream()
                    .noneMatch(component -> component.id().equals(pin.source()) && component.typeId().equals(expected))) {
                throw new IllegalArgumentException("pin " + pin.spec().name() + " of " + id + " needs a " + expected
                        + " with id " + pin.source());
            }
        }
    }

    public ComponentDefinition withName(String newName) {
        return new ComponentDefinition(id, newName, description, size, pinout, body);
    }

    /** Ids of the definitions instantiated directly in the body, in body order, without repeats. */
    public List<String> usedDefinitionIds() {
        return body.components().stream()
                .map(Component::typeId)
                .map(SubcircuitTypes::definitionIdOf)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }
}
