package cl.almejo.vsim.core.memory;

import java.util.Optional;
import java.util.OptionalInt;

/** The optional description of a memory found in a file's first line; every part may be missing. */
public record MemoryHeader(Optional<MemoryKind> kind, Optional<String> name, OptionalInt words, OptionalInt width) {

    public static MemoryHeader empty() {
        return new MemoryHeader(Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
    }

    public boolean isEmpty() {
        return kind.isEmpty() && name.isEmpty() && words.isEmpty() && width.isEmpty();
    }
}
