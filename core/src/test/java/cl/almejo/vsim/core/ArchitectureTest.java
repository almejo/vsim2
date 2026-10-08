package cl.almejo.vsim.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

    @Test
    void coreDoesNotDependOnAwtOrSwing() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ArchitectureTest::importsUserInterfaceToolkit)
                    .toList();
            assertThat(offenders).isEmpty();
        }
        assertThat(sourceRoot.resolve("cl/almejo/vsim/core/package-info.java")).exists();
    }

    private static boolean importsUserInterfaceToolkit(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("java.awt") || content.contains("javax.swing");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
