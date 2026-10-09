package cl.almejo.vsim.ui.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SwingLibraryDialogsTest {

    @TempDir
    Path directory;

    /** Headless (or a desktop without a file manager): no exception, the caller falls back to a message. */
    @Test
    void showingAFolderWithoutADesktopSaysItCouldNot() throws Exception {
        Path file = Files.writeString(directory.resolve("inverter.vsimcomponent.json"), "{}");

        assertThat(SwingLibraryDialogs.openFolder(file)).isFalse();
    }
}
