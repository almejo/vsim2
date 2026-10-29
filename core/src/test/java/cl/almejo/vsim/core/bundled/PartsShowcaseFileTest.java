package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartsShowcaseFileTest {

    /**
     * Writes «Todas las piezas» into the folder named by the environment variable, from the repository root:
     * {@code VSIM_WRITE_PARTS_SHOWCASE=/absolute/folder ./gradlew :core:test --tests '*PartsShowcaseFileTest.writeTheShowcase'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = PartsShowcaseFile.TARGET_VARIABLE, matches = ".+")
    void writeTheShowcase() throws IOException {
        PartsShowcaseFile.main(new String[0]);
    }

    @Test
    void theShowcaseIsWrittenIntoTheFolderAndReadBack(@TempDir Path folder) throws Exception {
        Path file = PartsShowcaseFile.writeInto(folder);

        assertThat(file).exists().hasFileName(PartsShowcaseFile.FILE_NAME);
        CircuitDocument read = new CircuitFormat(StandardComponentTypes.registry()).read(Files.readString(file));
        assertThat(read.components()).isNotEmpty();
    }
}
