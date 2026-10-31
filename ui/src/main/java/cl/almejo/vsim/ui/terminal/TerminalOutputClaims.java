package cl.almejo.vsim.ui.terminal;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which file each «Guardar salida» recording writes, across every open circuit, so two Terminals never write the same
 * file (each would truncate and interleave the other's bytes). EDT only.
 */
public final class TerminalOutputClaims {

    private final Map<Path, Object> owners = new HashMap<>();

    /** Takes {@code file} for {@code owner}; false when another owner holds it. */
    boolean claim(Path file, Object owner) {
        Objects.requireNonNull(owner, "owner");
        Object current = owners.putIfAbsent(key(file), owner);
        return current == null || current.equals(owner);
    }

    /** Gives {@code file} back, when {@code owner} holds it. */
    void release(Path file, Object owner) {
        owners.remove(key(file), owner);
    }

    private static Path key(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
