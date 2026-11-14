package cl.almejo.vsim.ui.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserFoldersTest {

    @TempDir
    Path home;

    private Path userDirs(String content) throws Exception {
        Path config = Files.createDirectories(home.resolve(".config"));
        return Files.writeString(config.resolve("user-dirs.dirs"), content);
    }

    @Test
    void linuxUsesTheLocalizedXdgDocumentsFolder() throws Exception {
        Path documentos = Files.createDirectories(home.resolve("Documentos"));
        userDirs("# comment\nXDG_DESKTOP_DIR=\"$HOME/Escritorio\"\nXDG_DOCUMENTS_DIR=\"$HOME/Documentos\"\n");

        assertThat(UserFolders.documents("Linux", Map.of(), home, () -> null)).isEqualTo(documentos);
    }

    @Test
    void linuxReadsTheConfigFolderFromXdgConfigHome() throws Exception {
        Path config = Files.createDirectories(home.resolve("elsewhere"));
        Files.writeString(config.resolve("user-dirs.dirs"), "XDG_DOCUMENTS_DIR=\"$HOME/Papeles\"\n");
        Path papeles = Files.createDirectories(home.resolve("Papeles"));

        assertThat(UserFolders.documents("Linux", Map.of("XDG_CONFIG_HOME", config.toString()), home, () -> null))
                .isEqualTo(papeles);
    }

    @Test
    void linuxAcceptsAnAbsoluteXdgPath() throws Exception {
        Path absolute = Files.createDirectories(home.resolve("absolute"));
        userDirs("XDG_DOCUMENTS_DIR=\"" + absolute + "\"\n");

        assertThat(UserFolders.documents("Linux", Map.of(), home, () -> null)).isEqualTo(absolute);
    }

    @Test
    void linuxFallsBackToHomeDocumentsWhenTheXdgFolderIsMissing() throws Exception {
        userDirs("XDG_DOCUMENTS_DIR=\"$HOME/Gone\"\n");
        Path documents = Files.createDirectories(home.resolve("Documents"));

        assertThat(UserFolders.documents("Linux", Map.of(), home, () -> null)).isEqualTo(documents);
    }

    @Test
    void linuxWithoutAnyDocumentsFolderUsesHome() {
        assertThat(UserFolders.documents("Linux", Map.of(), home, () -> null)).isEqualTo(home);
    }

    @Test
    void anXdgDocumentsFolderEqualToHomeMeansHome() throws Exception {
        userDirs("XDG_DOCUMENTS_DIR=\"$HOME/\"\n");
        Files.createDirectories(home.resolve("Documents"));

        assertThat(UserFolders.documents("Linux", Map.of(), home, () -> null)).isEqualTo(home);
    }

    @Test
    void macUsesDocumentsInHome() throws Exception {
        Path documents = Files.createDirectories(home.resolve("Documents"));

        assertThat(UserFolders.documents("Mac OS X", Map.of(), home, () -> null)).isEqualTo(documents);
    }

    @Test
    void windowsUsesTheKnownFolderEvenWhenRedirected() throws Exception {
        Path oneDrive = Files.createDirectories(home.resolve("OneDrive").resolve("Documentos"));
        Files.createDirectories(home.resolve("Documents"));

        assertThat(UserFolders.documents("Windows 11", Map.of(), home, () -> oneDrive)).isEqualTo(oneDrive);
    }

    @Test
    void windowsFallsBackToHomeDocumentsWhenTheKnownFolderIsMissing() throws Exception {
        Path documents = Files.createDirectories(home.resolve("Documents"));

        assertThat(UserFolders.documents("Windows 11", Map.of(), home, () -> home.resolve("gone")))
                .isEqualTo(documents);
    }

    @Test
    void theRealOneIsAnExistingFolder() {
        assertThat(UserFolders.documents()).isDirectory();
    }
}
