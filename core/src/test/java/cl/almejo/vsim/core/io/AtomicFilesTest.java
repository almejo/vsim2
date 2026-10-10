package cl.almejo.vsim.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AtomicFilesTest {

    @TempDir
    Path directory;

    private long filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void writesANewFileAndReplacesAnExistingOne() throws IOException {
        Path target = directory.resolve("circuit.vsim");
        AtomicFiles.writeString(target, "first");
        assertThat(Files.readString(target)).isEqualTo("first");
        AtomicFiles.writeString(target, "second");
        assertThat(Files.readString(target)).isEqualTo("second");
        assertThat(filesIn(directory)).isEqualTo(1);
    }

    @Test
    void keepsThePermissionsOfTheFileItReplaces() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path target = directory.resolve("circuit.vsim");
        Files.writeString(target, "old");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r-----"));
        AtomicFiles.writeString(target, "new");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target))).isEqualTo("rw-r-----");
    }

    @Test
    void aFailedMoveLeavesTheTargetAndNoTemporaryFile() throws IOException {
        Path target = Files.createDirectory(directory.resolve("busy.vsim"));
        Files.writeString(target.resolve("inside"), "keeps the folder non-empty");
        assertThatThrownBy(() -> AtomicFiles.writeString(target, "content")).isInstanceOf(IOException.class);
        assertThat(Files.isDirectory(target)).isTrue();
        assertThat(filesIn(directory)).isEqualTo(1);
    }
}
