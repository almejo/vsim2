package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
        Set<String> pinNames = new HashSet<>();
        Set<GridPoint> pinPositions = new HashSet<>();
        for (DefinitionPin pin : pinout) {
            requireValidPin(id, size, pin.spec(), pinNames, pinPositions);
            if (!sources.add(pin.source())) {
                throw new IllegalArgumentException("pin source used twice in " + id + ": " + pin.source());
            }
            String expected = pin.spec().direction() == PinDirection.OUTPUT
                    ? InterfacePins.OUTPUT_PIN : InterfacePins.INPUT_PIN;
            Component source = body.components().stream()
                    .filter(component -> component.id().equals(pin.source()) && component.typeId().equals(expected))
                    .findFirst()
                    .orElse(null);
            if (pin.spec().direction() == PinDirection.BIDIRECTIONAL || source == null) {
                throw new IllegalArgumentException("pin " + pin.spec().name() + " of " + id + " needs a " + expected
                        + " with id " + pin.source());
            }
            int sourceWidth = InterfacePins.widthOf(source);
            if (pin.spec().width() != sourceWidth) {
                throw new IllegalArgumentException("pin " + pin.spec().name() + " of " + id + " is "
                        + pin.spec().width() + " bits wide but its " + expected + " " + pin.source() + " is "
                        + sourceWidth + " bits wide");
            }
        }
    }

    /**
     * A pin needs a unique, non-blank name and a position on the box ({@code [0..width] x [0..height]}) no other pin
     * uses (pins at one point would short their outer nets).
     */
    private static void requireValidPin(String id, GridSize size, PinSpec spec, Set<String> names,
                                        Set<GridPoint> positions) {
        if (spec.name().isBlank()) {
            throw new IllegalArgumentException("a pin of " + id + " has no name");
        }
        if (!names.add(spec.name())) {
            throw new IllegalArgumentException("pin name used twice in " + id + ": " + spec.name());
        }
        GridPoint position = spec.position();
        if (position.x() < 0 || position.x() > size.width() || position.y() < 0 || position.y() > size.height()) {
            throw new IllegalArgumentException("pin " + spec.name() + " of " + id + " is outside the " + size.width()
                    + " x " + size.height() + " box: " + position.x() + "," + position.y());
        }
        if (!positions.add(position)) {
            throw new IllegalArgumentException("pin " + spec.name() + " of " + id + " shares its position "
                    + position.x() + "," + position.y() + " with another pin");
        }
    }

    public ComponentDefinition withName(String newName) {
        return new ComponentDefinition(id, newName, description, size, pinout, body);
    }

    /** Ids of the definitions instantiated directly in the body, in body order, without repeats. */
    public List<String> usedDefinitionIds() {
        return SubcircuitTypes.definitionIds(body.components());
    }
}
