package cl.almejo.vsim.ui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    static final String RECENT = "recent";
    static final String RESTORE_SESSION = "restoreSession";
    static final String SESSION = "session";
    static final String SHORTCUTS = "shortcuts";

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
        Objects.requireNonNull(theme, "theme");
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
        Objects.requireNonNull(schemeId, "schemeId");
        put(COLOR_SCHEME, schemeId.orElse(""));
    }

    /** Language tag ({@code es}, {@code en}); empty means the system locale. Applied at the next start. */
    public Optional<String> language() {
        return optional(LANGUAGE);
    }

    public void setLanguage(Optional<String> languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        put(LANGUAGE, languageTag.orElse(""));
    }

    /** The stored directory, or the platform default when none is stored or the stored one is not a valid path here. */
    public Path libraryDirectory() {
        Optional<String> stored = optional(LIBRARY_DIRECTORY);
        if (stored.isPresent()) {
            try {
                return Path.of(stored.get());
            } catch (InvalidPathException exception) {
                LOGGER.warn("Ignoring the stored library directory {}; using the default", stored.get(), exception);
            }
        }
        return defaultLibraryDirectory(System.getProperty("os.name", ""), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    public boolean usesDefaultLibraryDirectory() {
        return optional(LIBRARY_DIRECTORY).isEmpty();
    }

    /** Empty restores the platform default. */
    public void setLibraryDirectory(Optional<Path> directory) {
        Objects.requireNonNull(directory, "directory");
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

    /** For tests: views and extensions must remove the listeners they add when disposed. */
    public int listenerCount() {
        return listeners.size();
    }

    // --- M6-B session: recent files, last session, recovery folder ---

    /** Archivo ▸ Recientes (M6-B): child node {@code recent} with entries {@code recent/0} … {@code recent/9}. */
    public Preferences recentNode() {
        return node.node(RECENT);
    }

    /** «Al iniciar, abrir los circuitos de la última sesión» (M6-B): on unless the user turned it off. */
    public boolean restoreSession() {
        return !"false".equals(node.get(RESTORE_SESSION, "true"));
    }

    public void setRestoreSession(boolean enabled) {
        if (restoreSession() == enabled) {
            return;
        }
        put(RESTORE_SESSION, Boolean.toString(enabled));
    }

    /** The last session (M6-B): child node {@code session}, entries {@code session/<document>/<window>}. */
    public Preferences sessionNode() {
        return node.node(SESSION);
    }

    /**
     * VSim's own data folder: the parent of this platform's default library folder, whatever library folder the user
     * chose. Recovery copies live in {@code dataDirectory().resolve("recovery")} (Assumption 15).
     */
    public Path dataDirectory() {
        return dataDirectory(System.getProperty("os.name", ""), System.getenv(), Path.of(System.getProperty("user.home")));
    }

    public static Path dataDirectory(String osName, Map<String, String> environment, Path home) {
        return defaultLibraryDirectory(osName, environment, home).getParent();
    }

    // M6-C shortcuts (overview contract C4)

    /** Shortcut overrides: key = catalog id, value = {@code KeyStroke.toString()} form, {@code ""} = no shortcut. */
    public Preferences shortcutsNode() {
        return node.node(SHORTCUTS);
    }

    /** Saves the shortcut overrides and tells every listener (key {@code shortcuts}); every window re-applies. */
    public void shortcutsChanged() {
        try {
            shortcutsNode().flush();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not save the keyboard shortcuts", exception);
        }
        notifyListeners(SHORTCUTS);
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
        notifyListeners(key);
    }

    /**
     * Every listener hears of the change even when an earlier one fails (a theme or library listener must not keep
     * the others from following it); the failures are logged once all have run, and the setter returns normally.
     */
    private void notifyListeners(String key) {
        List<RuntimeException> failures = new ArrayList<>();
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        for (RuntimeException failure : failures) {
            LOGGER.error("A listener failed to follow the change of preference {}", key, failure);
        }
    }
}
