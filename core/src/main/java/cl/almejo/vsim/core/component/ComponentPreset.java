package cl.almejo.vsim.core.component;

import java.util.Map;
import java.util.Objects;

/**
 * A shortcut to a type with some parameters already set: «NAND» is the {@code and} type with «Negar salida» on. The
 * palette, favourites, recents, quick insert and the AI offer it by its {@code id}; a file never stores it (a placed
 * NAND is an {@code and} with {@code negate = true}).
 */
public record ComponentPreset(String id, String typeId, Map<String, Object> parameters) {

    public ComponentPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        parameters = Map.copyOf(parameters);
    }
}
