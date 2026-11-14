package cl.almejo.vsim.ui.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.filechooser.FileSystemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The user's standard folders as each operating system defines them. Every file chooser starts in
 * {@link #documents()} when it has nothing better (the current circuit's folder, a remembered one).
 */
public final class UserFolders {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserFolders.class);
    private static final String XDG_DOCUMENTS = "XDG_DOCUMENTS_DIR";

    private UserFolders() {
    }

    /**
     * The user's Documents folder: on Linux the XDG one (localized, e.g. {@code ~/Documentos}), on Windows the
     * Documents known folder (it may be redirected to OneDrive), on macOS {@code ~/Documents}; the home folder when
     * there is none. Always an existing folder unless the home folder itself is missing.
     */
    public static Path documents() {
        return documents(System.getProperty("os.name", ""), System.getenv(), Path.of(System.getProperty("user.home")),
                UserFolders::windowsDocuments);
    }

    static Path documents(String osName, Map<String, String> environment, Path home, Supplier<Path> windowsDocuments) {
        String system = osName.toLowerCase(Locale.ROOT);
        Optional<Path> standard;
        if (system.startsWith("windows")) {
            standard = Optional.ofNullable(windowsDocuments.get());
        } else if (system.startsWith("mac")) {
            standard = Optional.empty();
        } else {
            standard = xdgDocuments(environment, home);
            if (standard.filter(home::equals).isPresent()) {
                return home; // the XDG spec: a folder set to $HOME means the user has none
            }
        }
        return Stream.concat(standard.stream(), Stream.of(home.resolve("Documents")))
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(home);
    }

    private static Optional<Path> xdgDocuments(Map<String, String> environment, Path home) {
        String configHome = environment.getOrDefault("XDG_CONFIG_HOME", "");
        Path config = configHome.isBlank() ? home.resolve(".config") : Path.of(configHome);
        Path userDirs = config.resolve("user-dirs.dirs");
        if (!Files.isRegularFile(userDirs)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(userDirs)) {
                String trimmed = line.strip();
                if (!trimmed.startsWith(XDG_DOCUMENTS + "=")) {
                    continue;
                }
                String value = trimmed.substring(XDG_DOCUMENTS.length() + 1);
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.equals("$HOME") || value.startsWith("$HOME/")) {
                    String rest = value.substring("$HOME".length()).replaceFirst("^/+", "");
                    return Optional.of(rest.isEmpty() ? home : home.resolve(rest).normalize());
                }
                return value.startsWith("/") ? Optional.of(Path.of(value).normalize()) : Optional.empty();
            }
        } catch (IOException | InvalidPathException exception) {
            LOGGER.warn("Could not read the XDG user folders from {}", userDirs, exception);
        }
        return Optional.empty();
    }

    private static Path windowsDocuments() {
        File folder = FileSystemView.getFileSystemView().getDefaultDirectory();
        return folder == null ? null : folder.toPath();
    }
}
