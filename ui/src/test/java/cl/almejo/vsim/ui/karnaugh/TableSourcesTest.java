package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JFileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TableSourcesTest {

    @TempDir
    Path directory;
    private final List<Path> remembered = new ArrayList<>();

    @BeforeEach
    void messages() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theChooserStartsInTheSharedFolderAndRemembersTheChosenFile() throws Exception {
        Path table = Files.writeString(directory.resolve("tabla.csv"), "A,F\n0,1\n1,0\n");
        List<File> startFolders = new ArrayList<>();
        TableSources.Chooser chooser = TableSources.fileChooser(() -> directory, remembered::add,
                (fileChooser, parent) -> {
                    startFolders.add(fileChooser.getCurrentDirectory());
                    fileChooser.setSelectedFile(table.toFile());
                    return JFileChooser.APPROVE_OPTION;
                });

        Optional<Path> chosen = chooser.choose(null);

        assertThat(startFolders).containsExactly(directory.toFile());
        assertThat(chosen).contains(table);
        assertThat(remembered).containsExactly(table);
    }

    @Test
    void cancellingRemembersNothing() {
        TableSources.Chooser chooser = TableSources.fileChooser(() -> directory, remembered::add,
                (fileChooser, parent) -> JFileChooser.CANCEL_OPTION);

        assertThat(chooser.choose(null)).isEmpty();
        assertThat(remembered).isEmpty();
    }
}
