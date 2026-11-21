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

    @Test
    void aReadOnlyTargetIsReplacedAndStaysReadOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path target = directory.resolve("definition.vsimc");
        Files.writeString(target, "old");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
        AtomicFiles.writeString(target, "new");
        assertThat(Files.readString(target)).isEqualTo("new");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target))).isEqualTo("r--r--r--");
        assertThat(filesIn(directory)).isEqualTo(1);
    }

    @Test
    void savingThroughASymbolicLinkKeepsTheLinkAndWritesTheRealFile() throws IOException {
        Path real = Files.createDirectories(directory.resolve("real")).resolve("circuit.vsim");
        Files.writeString(real, "old");
        Path link = directory.resolve("link.vsim");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "links cannot be created here");
        }

        AtomicFiles.writeString(link, "new");

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(real)).isEqualTo("new");
        assertThat(filesIn(real.getParent())).isEqualTo(1);
    }

    @Test
    void whenNoTemporaryFileFitsInTheFolderAnExistingWritableTargetIsWrittenDirectly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        assumeTrue(!"root".equals(System.getProperty("user.name")));
        Path folder = Files.createDirectories(directory.resolve("locked"));
        Path target = folder.resolve("circuit.vsim");
        Files.writeString(target, "old");
        folder.toFile().setWritable(false);
        try {
            AtomicFiles.writeString(target, "new");
            assertThat(Files.readString(target)).isEqualTo("new");
            Path missing = folder.resolve("absent.vsim");
            assertThatThrownBy(() -> AtomicFiles.writeString(missing, "x"))
                    .isInstanceOf(IOException.class).hasMessageContaining(folder.toString())
                    .hasMessageNotContaining(".vsim2-");
        } finally {
            folder.toFile().setWritable(true);
        }
    }
}
