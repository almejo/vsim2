package cl.almejo.vsim.core.memory;

import java.util.List;

/** One memory file format. Adding a format means writing one of these and listing it in {@link MemoryFormats}. */
public interface MemoryFileFormat {

    String id();

    /** Whether this format can read a file with this name and first line (used by import and by drag-and-drop). */
    boolean canRead(String fileName, String firstLine);

    boolean canWrite();

    MemoryFile read(String text) throws MemoryFileProblem;

    /** The text of {@code file}; {@code width} is the memory's word width (it sets the digits written). */
    String write(MemoryFile file, int width);

    /** File name extensions (with the dot, lower case) this format is known by; may be empty. */
    List<String> extensions();
}
