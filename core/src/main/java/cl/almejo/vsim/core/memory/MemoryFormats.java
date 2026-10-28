package cl.almejo.vsim.core.memory;

import java.util.List;
import java.util.Optional;

/** The list of memory file formats. */
public final class MemoryFormats {

    private static final List<MemoryFileFormat> ALL = List.of(new VsimMemoryFormat(), new LogisimRawFormat());

    private MemoryFormats() {
    }

    public static List<MemoryFileFormat> all() {
        return ALL;
    }

    /** The first format that says it can read a file with this name and first line. */
    public static Optional<MemoryFileFormat> forReading(String fileName, String firstLine) {
        return ALL.stream().filter(format -> format.canRead(fileName, firstLine)).findFirst();
    }

    public static MemoryFileFormat byId(String id) {
        return ALL.stream().filter(format -> format.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown memory format " + id));
    }
}
