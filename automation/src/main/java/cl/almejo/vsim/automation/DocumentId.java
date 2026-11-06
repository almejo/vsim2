package cl.almejo.vsim.automation;

import java.util.Objects;
import java.util.Optional;

/**
 * An open circuit as the AI names it: opaque, stable while that circuit stays open, the same for every view of it
 * (two windows of one circuit share one id). Closing the circuit ends the id; it is never given to another circuit.
 *
 * <p>A place of the circuit (the body of a definition with one instance's values, spec §12) is addressed by the same
 * id {@linkplain #in qualified} with the place, so every read, edit and simulation entry point takes it unchanged;
 * {@link #base()} is the circuit itself.
 */
public record DocumentId(String value) {

    private static final String PLACE_MARK = "@";

    public DocumentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a document id is never blank");
        }
    }

    /** This circuit's {@code place}; the circuit itself for the main place. */
    public DocumentId in(PlaceRef place) {
        Objects.requireNonNull(place, "place");
        if (place.isMain()) {
            return base();
        }
        return new DocumentId(base().value + PLACE_MARK + place.definition()
                + place.context().map(path -> PLACE_MARK + path).orElse(""));
    }

    /** The circuit this id names, whatever place it is qualified with. */
    public DocumentId base() {
        int mark = value.indexOf(PLACE_MARK);
        return mark < 0 ? this : new DocumentId(value.substring(0, mark));
    }

    /** The place this id is qualified with; empty for the circuit itself. */
    public Optional<PlaceRef> place() {
        int first = value.indexOf(PLACE_MARK);
        if (first < 0) {
            return Optional.empty();
        }
        int second = value.indexOf(PLACE_MARK, first + 1);
        String definition = second < 0 ? value.substring(first + 1) : value.substring(first + 1, second);
        return Optional.of(new PlaceRef(definition,
                second < 0 ? Optional.empty() : Optional.of(value.substring(second + 1))));
    }
}
