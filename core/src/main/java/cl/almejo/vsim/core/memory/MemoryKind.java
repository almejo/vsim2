package cl.almejo.vsim.core.memory;

import java.util.Optional;

/** The two kinds of memory component. They never convert into each other. */
public enum MemoryKind {
    RAM("ram"), ROM("rom");

    private final String id;

    MemoryKind(String id) {
        this.id = id;
    }

    /** The lower-case name used in file headers. */
    public String id() {
        return id;
    }

    public static Optional<MemoryKind> fromId(String text) {
        for (MemoryKind kind : values()) {
            if (kind.id.equalsIgnoreCase(text)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
