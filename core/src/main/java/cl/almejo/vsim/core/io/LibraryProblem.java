package cl.almejo.vsim.core.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** A library file that was skipped (spec §5.6: invalid files are reported, never fatal). */
public record LibraryProblem(Path file, Kind kind, Optional<CircuitFormatException.Reason> reason, String detail) {

    public enum Kind { UNREADABLE, INVALID, DUPLICATE_ID }

    public LibraryProblem {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        detail = detail == null ? "" : detail;
    }

    static LibraryProblem unreadable(Path file, IOException exception) {
        return new LibraryProblem(file, Kind.UNREADABLE, Optional.empty(), String.valueOf(exception.getMessage()));
    }

    static LibraryProblem invalid(Path file, CircuitFormatException exception) {
        return new LibraryProblem(file, Kind.INVALID, Optional.of(exception.reason()), exception.detail());
    }

    static LibraryProblem duplicate(Path file, String id) {
        return new LibraryProblem(file, Kind.DUPLICATE_ID, Optional.empty(), id);
    }
}
