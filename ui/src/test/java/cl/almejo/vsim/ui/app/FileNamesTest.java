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
    void addsAnyExtensionOnlyWhenMissing() {
        assertThat(FileNames.withExtension(Path.of("/tmp/tabla"), ".csv")).isEqualTo(Path.of("/tmp/tabla.csv"));
        assertThat(FileNames.withExtension(Path.of("/tmp/tabla.CSV"), ".csv")).isEqualTo(Path.of("/tmp/tabla.CSV"));
        assertThat(FileNames.withExtension(Path.of("sumador.vsim"), ".pdf")).isEqualTo(Path.of("sumador.vsim.pdf"));
    }

    @Test
    void safeNameReplacesTheCharactersFileSystemsRefuse() {
        assertThat(FileNames.safeName("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j");
        assertThat(FileNames.safeName("sumador 4 bits")).isEqualTo("sumador 4 bits");
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
