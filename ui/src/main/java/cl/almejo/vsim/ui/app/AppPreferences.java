package cl.almejo.vsim.ui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** User preferences (spec §6.7, §6.10, §5.6), stored with {@code java.util.prefs}. Setters notify on real changes. */
public final class AppPreferences {

    public enum Theme { LIGHT, DARK, SYSTEM }

    static final String THEME = "theme";
    static final String COLOR_SCHEME = "colorScheme";
    static final String LANGUAGE = "language";
    static final String LIBRARY_DIRECTORY = "libraryDirectory";

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);
    private static final String NODE = "cl/almejo/vsim2";

    private final Preferences node;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public AppPreferences(Preferences node) {
        this.node = node;
    }

    public static AppPreferences system() {
        return new AppPreferences(Preferences.userRoot().node(NODE));
    }

    public static AppPreferences inMemory() {
        return new AppPreferences(new MemoryPreferences());
    }

    public Theme theme() {
        String stored = node.get(THEME, Theme.SYSTEM.name());
        try {
            return Theme.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return Theme.SYSTEM;
        }
    }

    public void setTheme(Theme theme) {
        if (theme() == theme) {
            return;
        }
        put(THEME, theme.name());
    }

    /** The circuit color scheme id chosen by the user; empty means the scheme that goes with the theme. */
    public Optional<String> colorScheme() {
        return optional(COLOR_SCHEME);
    }

    public void setColorScheme(Optional<String> schemeId) {
        put(COLOR_SCHEME, schemeId.orElse(""));
    }

    /** Language tag ({@code es}, {@code en}); empty means the system locale. Applied at the next start. */
    public Optional<String> language() {
        return optional(LANGUAGE);
    }

    public void setLanguage(Optional<String> languageTag) {
        put(LANGUAGE, languageTag.orElse(""));
    }

    public Path libraryDirectory() {
        return optional(LIBRARY_DIRECTORY).map(Path::of).orElseGet(() -> defaultLibraryDirectory(
                System.getProperty("os.name", ""), System.getenv(), Path.of(System.getProperty("user.home"))));
    }

    public boolean usesDefaultLibraryDirectory() {
        return optional(LIBRARY_DIRECTORY).isEmpty();
    }

    /** Empty restores the platform default. */
    public void setLibraryDirectory(Optional<Path> directory) {
        put(LIBRARY_DIRECTORY, directory.map(path -> path.toAbsolutePath().toString()).orElse(""));
    }

    /** Spec §5.6: XDG data dir on Linux, Application Support on macOS, %APPDATA% on Windows. */
    public static Path defaultLibraryDirectory(String osName, Map<String, String> environment, Path home) {
        String system = osName.toLowerCase(Locale.ROOT);
        if (system.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("VSim2").resolve("components");
        }
        if (system.startsWith("windows")) {
            String appData = environment.get("APPDATA");
            Path base = appData == null || appData.isBlank() ? home.resolve("AppData").resolve("Roaming")
                    : Path.of(appData);
            return base.resolve("VSim2").resolve("components");
        }
        String dataHome = environment.get("XDG_DATA_HOME");
        Path base = dataHome == null || dataHome.isBlank() ? home.resolve(".local").resolve("share") : Path.of(dataHome);
        return base.resolve("vsim2").resolve("components");
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private Optional<String> optional(String key) {
        String value = node.get(key, "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private void put(String key, String value) {
        if (value.equals(node.get(key, ""))) {
            return;
        }
        node.put(key, value);
        try {
            node.flush();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not save preference {}", key, exception);
        }
        listeners.forEach(Runnable::run);
    }
}
