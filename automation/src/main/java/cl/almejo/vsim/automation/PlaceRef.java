package cl.almejo.vsim.automation;

import java.util.Objects;
import java.util.Optional;

/**
 * A place of a circuit as the AI names it (spec §12): the body of a definition ({@code definition} is its id) with the
 * values of the instance at {@code context} (a simulation path as {@code vsim_list_places} gives it; empty: «Sin
 * valores»), or the main circuit ({@link #MAIN}).
 */
public record PlaceRef(String definition, Optional<String> context) {

    /** The {@code definition} that names the circuit itself. */
    public static final String MAIN = "main";

    public PlaceRef {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        if (definition.isBlank()) {
            throw new IllegalArgumentException("a place names a definition, or main");
        }
        context = context.filter(path -> !path.isBlank());
    }

    /** The circuit itself. */
    public static PlaceRef main() {
        return new PlaceRef(MAIN, Optional.empty());
    }

    public boolean isMain() {
        return definition.equals(MAIN);
    }

    /**
     * Whether a view of the place {@code definitionId} (empty: the circuit itself) with the values of the piece at
     * {@code contextPath} (empty: «Sin valores») shows this place: the circuit's own view for the circuit; for a
     * component, its views on the same piece, or every view of it when this place names no piece (its body is the
     * same in all).
     */
    public boolean isShownBy(Optional<String> definitionId, Optional<String> contextPath) {
        if (isMain()) {
            return definitionId.isEmpty();
        }
        return definitionId.filter(definition::equals).isPresent()
                && (context.isEmpty() || context.equals(contextPath));
    }
}
