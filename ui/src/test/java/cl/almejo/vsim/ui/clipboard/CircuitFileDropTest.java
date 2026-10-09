package cl.almejo.vsim.ui.clipboard;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitFileDropTest {

    private final JPanel target = new JPanel();

    @Test
    void keepsOnlyCircuitFiles() {
        FileListTransferable files = new FileListTransferable(List.of(new File("/tmp/a.vsim"),
                new File("/tmp/B.JSON"), new File("/tmp/notes.txt"), new File("/tmp/image.png")));
        assertThat(CircuitFileDrop.circuitFiles(files)).containsExactly(Path.of("/tmp/a.vsim"), Path.of("/tmp/B.JSON"));
    }

    @Test
    void keepsTheFilesTheFilterAccepts() {
        FileListTransferable files = new FileListTransferable(List.of(new File("/tmp/a.vsim"),
                new File("/tmp/notes.txt")));
        assertThat(CircuitFileDrop.files(files, path -> path.toString().endsWith(".txt")))
                .containsExactly(Path.of("/tmp/notes.txt"));
        assertThat(CircuitFileDrop.files(new StringSelection("/tmp/notes.txt"), path -> true)).isEmpty();
    }

    @Test
    void somethingThatIsNotAFileListHasNoFiles() {
        assertThat(CircuitFileDrop.circuitFiles(new StringSelection("/tmp/a.vsim"))).isEmpty();
    }

    @Test
    void importPassesTheCircuitFilesToTheOpener() {
        List<Path> opened = new ArrayList<>();
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(target,
                new FileListTransferable(List.of(new File("/tmp/a.vsim"), new File("/tmp/notes.txt"))));
        assertThat(CircuitFileDrop.importData(support, opened::addAll)).isTrue();
        assertThat(opened).containsExactly(Path.of("/tmp/a.vsim"));
    }

    @Test
    void aDropWithoutCircuitFilesIsRefusedAndOpensNothing() {
        List<Path> opened = new ArrayList<>();
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(target,
                new FileListTransferable(List.of(new File("/tmp/notes.txt"))));
        assertThat(CircuitFileDrop.importData(support, opened::addAll)).isFalse();
        assertThat(opened).isEmpty();
    }

    @Test
    void theWindowHandlerAcceptsFileListsOnly() {
        List<Path> opened = new ArrayList<>();
        FileDropHandler handler = new FileDropHandler(opened::addAll);
        assertThat(handler.canImport(new TransferHandler.TransferSupport(target,
                new FileListTransferable(List.of(new File("/tmp/a.vsim")))))).isTrue();
        assertThat(handler.canImport(new TransferHandler.TransferSupport(target, new StringSelection("hola"))))
                .isFalse();
        assertThat(handler.importData(new TransferHandler.TransferSupport(target,
                new FileListTransferable(List.of(new File("/tmp/a.vsim")))))).isTrue();
        assertThat(opened).containsExactly(Path.of("/tmp/a.vsim"));
    }
}
