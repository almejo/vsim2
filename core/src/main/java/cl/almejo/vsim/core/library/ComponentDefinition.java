package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
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
                                  List<DefinitionPin> pinout, CircuitFragment body,
                                  Optional<SymbolDrawing> appearance, long revision) {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** A newly created definition with the standard box and its first revision. */
    public ComponentDefinition(String id, String name, String description, GridSize size,
                               List<DefinitionPin> pinout, CircuitFragment body) {
        this(id, name, description, size, pinout, body, Optional.empty(), 1);
    }

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
        Objects.requireNonNull(appearance, "appearance");
        if (revision < 1) {
            throw new IllegalArgumentException("component revision must be positive");
        }
        if (appearance.isPresent() && !appearance.get().size().equals(size)) {
            throw new IllegalArgumentException("definition and drawing sizes must agree");
        }
        // No pins is allowed: «Crear componente» warns first (a body that only runs on its own, like a blinker).
        pinout = List.copyOf(pinout);
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
                        + bitCount(pin.spec().width()) + " wide but its " + expected + " " + pin.source() + " is "
                        + bitCount(sourceWidth) + " wide");
            }
        }
    }

    private static String bitCount(int width) {
        return width + (width == 1 ? " bit" : " bits");
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
        if (name.equals(Objects.requireNonNull(newName, "newName").strip())) {
            return this;
        }
        return new ComponentDefinition(id, newName, description, size, pinout, body, appearance,
                Math.incrementExact(revision));
    }

    /**
     * This definition with {@code newDescription} (the «Descripción» Propiedades shows for its instances; null is
     * empty): a new revision, or {@code this} when the text is the same.
     */
    public ComponentDefinition withDescription(String newDescription) {
        String next = newDescription == null ? "" : newDescription;
        if (description.equals(next)) {
            return this;
        }
        return new ComponentDefinition(id, name, next, size, pinout, body, appearance, Math.incrementExact(revision));
    }

    public ComponentDefinition withAppearance(Optional<SymbolDrawing> newAppearance) {
        return withGeometry(size, pinout, newAppearance);
    }

    /** One accepted definition appearance edit advances the revision once, regardless of its pin count. */
    public ComponentDefinition withGeometry(GridSize newSize, List<DefinitionPin> newPinout,
                                            Optional<SymbolDrawing> newAppearance) {
        if (size.equals(newSize) && pinout.equals(newPinout) && appearance.equals(newAppearance)) {
            return this;
        }
        return new ComponentDefinition(id, name, description, newSize, newPinout, body, newAppearance,
                Math.incrementExact(revision));
    }

    /** Content equality for revision comparison; independent counters are not a component's identity. */
    public boolean sameContentAs(ComponentDefinition other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id) && name.equals(other.name) && description.equals(other.description)
                && size.equals(other.size) && pinout.equals(other.pinout) && body.equals(other.body)
                && appearance.equals(other.appearance);
    }

    /** Assigns a locally edited candidate its next revision; importing a library version does not use this. */
    public ComponentDefinition revisedFrom(ComponentDefinition previous) {
        Objects.requireNonNull(previous, "previous");
        if (!id.equals(previous.id)) {
            throw new IllegalArgumentException("a revision must keep the component identity");
        }
        if (sameContentAs(previous)) {
            return previous;
        }
        return new ComponentDefinition(id, name, description, size, pinout, body, appearance,
                Math.incrementExact(previous.revision));
    }

    /** A new document holding the body's elements (and no definitions: the body never carries them). */
    public CircuitDocument bodyDocument() {
        CircuitDocument document = new CircuitDocument();
        body.elements().forEach(document::add);
        return document;
    }

    /**
     * Like {@link #bodyDocument()}, with the definitions of {@code source} that the body uses (directly or nested),
     * so the body's subcircuits can be resolved.
     */
    public CircuitDocument bodyDocument(CircuitDocument source) {
        CircuitDocument document = new CircuitDocument();
        source.definitionsUsedBy(body.components()).forEach(document::putDefinition);
        body.elements().forEach(document::add);
        return document;
    }

    /** Ids of the definitions instantiated directly in the body, in body order, without repeats. */
    public List<String> usedDefinitionIds() {
        return SubcircuitTypes.definitionIds(body.components());
    }
}
