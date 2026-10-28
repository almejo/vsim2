package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The work of {@link MigratePathsTest#migrateTheGivenPaths}: loads every {@code .vsim} circuit and
 * {@code .vsimcomponent.json} component file under the given files or folders and saves it back with VSim's own
 * writers (so old files end up in the current format). A file that cannot be loaded is reported and left alone.
 */
final class MigratePaths {

    private static final String CIRCUIT_SUFFIX = ".vsim";

    private MigratePaths() {
    }

    static void run(List<Path> paths, PrintStream report) throws IOException {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        for (Path path : paths) {
            List<Path> files;
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    files = walk.filter(Files::isRegularFile).filter(MigratePaths::isVsimFile).sorted().toList();
                }
            } else {
                files = List.of(path);
            }
            for (Path file : files) {
                report.println(file + ": " + migrate(file, registry));
            }
        }
    }

    private static boolean isVsimFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(CIRCUIT_SUFFIX) || LibraryDirectory.isComponentFile(file);
    }

    private static String migrate(Path file, ComponentTypeRegistry registry) {
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String rewritten;
            if (LibraryDirectory.isComponentFile(file)) {
                DefinitionFormat format = new DefinitionFormat(registry);
                rewritten = format.write(format.read(original));
            } else {
                CircuitFormat format = new CircuitFormat(registry);
                rewritten = format.write(format.read(original));
            }
            if (rewritten.equals(original)) {
                return "unchanged";
            }
            AtomicFiles.writeString(file, rewritten);
            return "rewritten";
        } catch (IOException | RuntimeException | CircuitFormatException exception) {
            return "failed (" + exception.getMessage() + ")";
        }
    }
}
