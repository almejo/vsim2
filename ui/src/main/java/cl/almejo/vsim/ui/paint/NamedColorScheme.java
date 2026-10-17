package cl.almejo.vsim.ui.paint;

import java.util.Objects;

/** A colour scheme the user made (M6.8): its stored id ({@code custom-<n>}), the name it shows and its colours. */
public record NamedColorScheme(String id, String name, ColorScheme scheme) {

    public NamedColorScheme {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scheme, "scheme");
    }
}
