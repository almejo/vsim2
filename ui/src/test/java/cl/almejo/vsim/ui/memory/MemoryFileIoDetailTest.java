package cl.almejo.vsim.ui.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** An unreadable memory file has a one-line detail that can sit inside another message without repeating its name. */
class MemoryFileIoDetailTest {

    @TempDir
    Path directory;

    @Test
    void theDetailOfAnIoFailureIsOneLineWithoutTheWholeMessage() {
        Path missing = directory.resolve("rom.hex");
        assertThatThrownBy(() -> MemoryFileIo.read(missing)).isInstanceOfSatisfying(MemoryFileIo.Unreadable.class,
                unreadable -> {
                    assertThat(unreadable.getMessage()).contains("\n");
                    assertThat(unreadable.detail()).startsWith("Detalle técnico: ").doesNotContain("\n")
                            .doesNotContain("No se pudo leer");
                });
    }

    @Test
    void aFailureWithOnlyOneSentenceUsesItAsItsDetail() throws Exception {
        Path notUtf8 = Files.write(directory.resolve("bad.hex"), new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThatThrownBy(() -> MemoryFileIo.read(notUtf8)).isInstanceOfSatisfying(MemoryFileIo.Unreadable.class,
                unreadable -> assertThat(unreadable.detail()).isEqualTo(unreadable.getMessage()));
    }
}
