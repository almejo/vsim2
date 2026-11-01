package cl.almejo.vsim.ui.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which file each «Guardar salida» recording writes, across every open circuit, so two Terminals never write the same
 * file (each would truncate and interleave the other's bytes), and what the user answered about each file in this run
 * of the app (kept in memory only: the next run asks again). Files are compared by {@link #canonical}. EDT only.
 */
public final class TerminalOutputClaims {

    private final Map<Path, Object> owners = new HashMap<>();
    private final Map<Path, Boolean> answers = new HashMap<>();

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

    /** What the user answered about recording to {@code file} in this run, if asked (or if they chose it). */
    Optional<Boolean> answerFor(Path file) {
        return Optional.ofNullable(answers.get(canonical(file)));
    }

    /** Remembers the answer about {@code file} for the rest of the run. */
    void remember(Path file, boolean record) {
        answers.put(canonical(file), record);
    }

    private static Path key(Path file) {
        return canonical(file);
    }

    /**
     * The identity of {@code file}: its real path when it exists, so a symlink and the file it points to are the same
     * one; else the real path of the nearest folder that exists plus the rest, normalized (so the identity stays the
     * same once a recording creates the file). {@code a/../a} and {@code a} are the same file too.
     */
    static Path canonical(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (IOException exception) {
            return absolute;
        }
    }
}
