package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.io.CircuitFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@link PartsShowcase} («Todas las piezas», not a bundled example) as a file to open by hand. Run
 * {@code VSIM_WRITE_PARTS_SHOWCASE=/absolute/folder ./gradlew :core:test --tests '*PartsShowcaseFileTest.writeTheShowcase'}
 * (from the repository root) to write {@value #FILE_NAME} there with the document writer; without the variable nothing
 * is written.
 */
public final class PartsShowcaseFile {

    /** The environment variable that names the folder to write into. */
    public static final String TARGET_VARIABLE = "VSIM_WRITE_PARTS_SHOWCASE";
    /** The name of the written file. */
    public static final String FILE_NAME = "todas-las-piezas.vsim";

    private PartsShowcaseFile() {
    }

    /** Writes the showcase into {@code folder}, creating it if needed, and returns the written file. */
    public static Path writeInto(Path folder) throws IOException {
        Files.createDirectories(folder);
        CircuitBuilder builder = PartsShowcase.create().builder();
        Path file = folder.resolve(FILE_NAME);
        Files.writeString(file, new CircuitFormat(builder.registry()).write(builder.document()) + "\n");
        System.out.println("wrote " + file);
        return file;
    }

    /** Writes the showcase into the folder {@link #TARGET_VARIABLE} names; without it does nothing. */
    public static void main(String[] arguments) throws IOException {
        String target = System.getenv(TARGET_VARIABLE);
        if (target == null || target.isBlank()) {
            System.out.println(TARGET_VARIABLE + " is not set: nothing written");
            return;
        }
        writeInto(Path.of(target));
    }
}
