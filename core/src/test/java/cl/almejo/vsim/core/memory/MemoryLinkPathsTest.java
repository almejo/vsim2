package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLinkPathsTest {

    @TempDir
    Path folder;

    @Test
    void theFileNameDropsEveryFolderOfAStoredLink() {
        assertThat(MemoryLinkPaths.fileName("/home/maria/cursos/rom.hex")).isEqualTo("rom.hex");
        assertThat(MemoryLinkPaths.fileName("programas/rom.hex")).isEqualTo("rom.hex");
        assertThat(MemoryLinkPaths.fileName("C:\\datos\\rom.hex")).isEqualTo("rom.hex");
        assertThat(MemoryLinkPaths.fileName("rom.hex")).isEqualTo("rom.hex");
    }

    @Test
    void aStoredLinkIsRelativeToTheCircuitsFolderAndResolvesBack() {
        Path circuit = folder.resolve("curso/cpu.vsim");
        Path file = folder.resolve("curso/programas/rom.hex");

        String stored = MemoryLinkPaths.storedLinkPath(file, Optional.of(circuit));

        assertThat(stored).isEqualTo("programas/rom.hex");
        assertThat(MemoryLinkPaths.resolve(stored, Optional.of(circuit))).isEqualTo(file);
    }
}
