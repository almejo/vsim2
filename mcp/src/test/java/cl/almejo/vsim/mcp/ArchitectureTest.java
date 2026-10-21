package cl.almejo.vsim.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The MCP module never knows the editor (spec §11, A§4.2): the editor depends on it, never the other way. */
class ArchitectureTest {

    private static final List<String> FORBIDDEN = List.of("java.awt", "javax.swing", "cl.almejo.vsim.ui");

    @Test
    void mcpImportsNeitherTheEditorNorSwing() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(ArchitectureTest::importsAForbiddenPackage)
                    .toList()).isEmpty();
        }
    }

    private static boolean importsAForbiddenPackage(Path path) {
        try {
            String content = Files.readString(path);
            return FORBIDDEN.stream().anyMatch(prefix -> content.contains("import " + prefix));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
