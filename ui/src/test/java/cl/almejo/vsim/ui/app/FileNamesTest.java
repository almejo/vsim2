package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

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
}
