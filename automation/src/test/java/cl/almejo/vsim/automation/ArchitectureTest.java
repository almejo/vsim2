package cl.almejo.vsim.automation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The module boundaries of IA.1 (spec §11, A§4.2): neither core nor automation knows Swing, MCP or HTTP. */
class ArchitectureTest {

    private static final List<String> FORBIDDEN = List.of("java.awt", "javax.swing", "io.modelcontextprotocol",
            "org.eclipse.jetty", "jakarta.servlet", "cl.almejo.vsim.mcp", "cl.almejo.vsim.ui");

    @Test
    void automationImportsNeitherSwingNorTheSdkNorTheServer() {
        assertThat(offenders(Path.of("src/main/java"))).isEmpty();
    }

    @Test
    void coreImportsNeitherSwingNorTheSdkNorTheServer() {
        assertThat(offenders(Path.of("../core/src/main/java"))).isEmpty();
    }

    private static List<Path> offenders(Path sourceRoot) {
        assertThat(sourceRoot).isDirectory();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(ArchitectureTest::importsAForbiddenPackage)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean importsAForbiddenPackage(Path path) {
        try {
            String content = Files.readString(path);
            return FORBIDDEN.stream().anyMatch(prefix -> content.contains("import " + prefix)
                    || content.contains("import static " + prefix));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
