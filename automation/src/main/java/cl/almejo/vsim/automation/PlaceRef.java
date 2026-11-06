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
}
