package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileNamesTest {

    @Test
    void addsVsimExtensionOnlyWhenMissing() {
        assertThat(FileNames.withExtension(Path.of("/tmp/sumador"))).isEqualTo(Path.of("/tmp/sumador.vsim"));
        assertThat(FileNames.withExtension(Path.of("/tmp/sumador.vsim"))).isEqualTo(Path.of("/tmp/sumador.vsim"));
        assertThat(FileNames.withExtension(Path.of("/tmp/SUMADOR.VSIM"))).isEqualTo(Path.of("/tmp/SUMADOR.VSIM"));
        assertThat(FileNames.withExtension(Path.of("/tmp/4-bit-adder.json"))).isEqualTo(Path.of("/tmp/4-bit-adder.json.vsim"));
    }

    @Test
    void normalizedGivesOneSpellingPerFile(@TempDir Path directory) throws IOException {
        Path file = Files.createFile(directory.resolve("a.vsim"));
        Files.createDirectory(directory.resolve("sub"));
        assertThat(FileNames.normalized(directory.resolve("sub/../a.vsim"))).isEqualTo(FileNames.normalized(file));
        assertThat(FileNames.normalized(Path.of("no-such-directory/../b.vsim")))
                .isEqualTo(Path.of("b.vsim").toAbsolutePath().normalize());
    }
}
