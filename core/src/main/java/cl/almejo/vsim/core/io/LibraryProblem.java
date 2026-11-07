package cl.almejo.vsim.core.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A library file that was skipped (spec §5.6: invalid files are reported, never fatal), or the library directory that
 * is no longer watched ({@link Kind#DIRECTORY_GONE}, {@code file} is then the directory).
 */
public record LibraryProblem(Path file, Kind kind, Optional<CircuitFormatException.Reason> reason, String detail) {

    public enum Kind { UNREADABLE, INVALID, DUPLICATE_ID, DIRECTORY_GONE }

    public LibraryProblem {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        detail = detail == null ? "" : detail;
    }

    /**
     * The id of the component the file is named after ({@code inverter} for {@code inverter.vsimcomponent.json});
     * empty for a file of another name or a folder. A broken file named after a component the library has (saved
     * meanwhile to another file: a broken file is never overwritten) can be deleted.
     */
    public Optional<String> componentIdNamed() {
        String fileName = String.valueOf(file.getFileName());
        int idLength = fileName.length() - DefinitionFormat.FILE_SUFFIX.length();
        boolean componentFile = idLength > 0 && fileName.regionMatches(true, idLength, DefinitionFormat.FILE_SUFFIX, 0,
                DefinitionFormat.FILE_SUFFIX.length());
        return componentFile ? Optional.of(fileName.substring(0, idLength)) : Optional.empty();
    }

    /** The detail is the exception's message, or its class name when it has none. */
    static LibraryProblem unreadable(Path file, IOException exception) {
        return new LibraryProblem(file, Kind.UNREADABLE, Optional.empty(), messageOf(exception));
    }

    static LibraryProblem invalid(Path file, CircuitFormatException exception) {
        return new LibraryProblem(file, Kind.INVALID, Optional.of(exception.reason()), exception.detail());
    }

    /**
     * A file the reader failed on in a way the format does not describe (a bug): invalid, with no reason. The detail is
     * the exception's message, or its class name when it has none.
     */
    static LibraryProblem invalid(Path file, RuntimeException exception) {
        return new LibraryProblem(file, Kind.INVALID, Optional.empty(), messageOf(exception));
    }

    static LibraryProblem duplicate(Path file, String id) {
        return new LibraryProblem(file, Kind.DUPLICATE_ID, Optional.empty(), id);
    }

    /** The watched library directory was deleted: its changes are no longer followed. */
    public static LibraryProblem directoryGone(Path directory) {
        return new LibraryProblem(directory, Kind.DIRECTORY_GONE, Optional.empty(), "");
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
