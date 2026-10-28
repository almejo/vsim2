package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryFormatsTest {

    @Test
    void theListIsVsimThenLogisim() {
        assertThat(MemoryFormats.all()).extracting(MemoryFileFormat::id).containsExactly("vsim", "logisim");
    }

    @Test
    void picksTheFormatByExtensionOrFirstLine() {
        assertThat(MemoryFormats.forReading("x.hex", "3A")).containsInstanceOf(VsimMemoryFormat.class);
        assertThat(MemoryFormats.forReading("x.bin", "v2.0 raw")).containsInstanceOf(LogisimRawFormat.class);
        assertThat(MemoryFormats.forReading("x.txt", "v2.0 raw")).containsInstanceOf(LogisimRawFormat.class);
        assertThat(MemoryFormats.forReading("x.bin", "# vsim: rom")).containsInstanceOf(VsimMemoryFormat.class);
    }

    @Test
    void otherFilesAreNotMemoryFiles() {
        assertThat(MemoryFormats.forReading("x.png", "‰PNG")).isEmpty();
    }

    @Test
    void vsimExtensions() {
        assertThat(new VsimMemoryFormat().extensions()).containsExactly(".hex", ".mem", ".rom", ".ram", ".txt");
    }

    @Test
    void byIdFindsOrFails() {
        assertThat(MemoryFormats.byId("logisim")).isInstanceOf(LogisimRawFormat.class);
        assertThatThrownBy(() -> MemoryFormats.byId("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
