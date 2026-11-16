package cl.almejo.vsim.core.machine;

import java.util.Collection;
import java.util.Objects;

/**
 * An input or output pin of a {@link StateMachine}: a stable {@code id} (never shown) and the visible {@code name}
 * the conditions and outputs use. The body's interface pin is made from the id ({@link MachineBody}), so a rename
 * keeps the pin's source and the placed pieces' wires stay on it. A pin born from a name has that name as its id,
 * which is what the files of hitos 1–2 (pin names only) mean.
 */
public record MachinePin(String id, String name) {

    public MachinePin {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a machine pin needs an id");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("a machine pin needs a name");
        }
    }

    /** A new pin called {@code name}, with {@code name} as its id. */
    public static MachinePin named(String name) {
        return new MachinePin(name, name);
    }

    /**
     * A new pin called {@code name}: its id is {@code name}, else {@code name-2}, {@code name-3}… the first that is
     * not in {@code takenIds} (the ids of the other pins of its side; a renamed pin keeps its old id, and a new pin
     * must not take it, or it would take the renamed pin's wires).
     */
    public static MachinePin minted(String name, Collection<String> takenIds) {
        String id = name;
        for (int suffix = 2; takenIds.contains(id); suffix++) {
            id = name + "-" + suffix;
        }
        return new MachinePin(id, name);
    }

    public MachinePin withName(String newName) {
        return new MachinePin(id, newName);
    }
}
