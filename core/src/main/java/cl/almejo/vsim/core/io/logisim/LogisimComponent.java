package cl.almejo.vsim.core.io.logisim;

import java.util.Objects;

/**
 * A Logisim {@code <comp>}: {@code library} is the description of its library ({@code #Gates}, {@code #Wiring}, …) or
 * empty for an instance of one of the file's own circuits, whose {@code name} is then that circuit's.
 */
public record LogisimComponent(String library, String name, LogisimPoint location, LogisimAttributes attributes) {

    public LogisimComponent {
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(attributes, "attributes");
    }

    /** Whether this is an instance of one of the file's circuits rather than a built-in part. */
    public boolean isSubcircuit() {
        return library.isEmpty();
    }
}
