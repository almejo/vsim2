package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.ui.paint.TextFont;
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

    /** The key that inverts the follow mode while dragging (M6.7): Shift by default, Alt, or none. */
    public enum InvertModifier {
        SHIFT, ALT, NONE;

        /** Whether this key is down given the state of the Shift and Alt keys; {@link #NONE} never is. */
        public boolean isHeld(boolean shiftDown, boolean altDown) {
            return switch (this) {
                case SHIFT -> shiftDown;
                case ALT -> altDown;
                case NONE -> false;
            };
        }
    }

    static final String THEME = "theme";
    static final String COLOR_SCHEME = "colorScheme";
    static final String LANGUAGE = "language";
    static final String LIBRARY_DIRECTORY = "libraryDirectory";
    static final String RECENT = "recent";
    static final String RESTORE_SESSION = "restoreSession";
    static final String SESSION = "session";
    static final String SHORTCUTS = "shortcuts";
    static final String STEP_UNITS = "simulation.stepUnits";
    static final String EXPORT_DIRECTORY = "export.directory";
    static final String EXPORT_SCALE = "export.scale";
    static final String EXPORT_PRINT_BACKGROUND = "export.printBackground";
    static final String TIDY_REMOVE_STUBS = "tidyRemoveStubs";
    static final String TIDY_MERGE_SEGMENTS = "tidyMergeSegments";
    static final String TIDY_FLATTEN_DETOURS = "tidyFlattenDetours";
    static final String WIRES_FOLLOW = "wiresFollow";
    static final String INVERT_MODIFIER = "invertModifier";
    static final String TEXT_FONT_FAMILY = "textFontFamily";
    static final String TEXT_FONT_SIZE = "textFontSize";

    /** «Avanzar 10 t» (M6.1-B): the fixed step until the user picks another size. */
    public static final long DEFAULT_STEP_UNITS = 10;
    public static final long MAXIMUM_STEP_UNITS = 1_000_000_000;

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

    /** Time units of the fixed step «Avanzar N t» (M6.1-B); the default when nothing valid is stored. */
    public long stepUnits() {
        try {
            long stored = Long.parseLong(node.get(STEP_UNITS, ""));
            return stored >= 1 && stored <= MAXIMUM_STEP_UNITS ? stored : DEFAULT_STEP_UNITS;
        } catch (NumberFormatException exception) {
            return DEFAULT_STEP_UNITS;
        }
    }

    /** 1 to {@link #MAXIMUM_STEP_UNITS} (IllegalArgumentException otherwise); every view renames its fixed step. */
    public void setStepUnits(long units) {
        if (units < 1 || units > MAXIMUM_STEP_UNITS) {
            throw new IllegalArgumentException("step units out of range: " + units);
        }
        put(STEP_UNITS, Long.toString(units));
    }

    // --- M6.2 export image ---

    /** The folder of the last image export; empty until the first one. */
    public Optional<Path> exportDirectory() {
        return optional(EXPORT_DIRECTORY).flatMap(stored -> {
            try {
                return Optional.of(Path.of(stored));
            } catch (InvalidPathException exception) {
                LOGGER.warn("Ignoring the stored export directory {}", stored, exception);
                return Optional.empty();
            }
        });
    }

    public void setExportDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        put(EXPORT_DIRECTORY, directory.toAbsolutePath().toString());
    }

    /** The PNG scale (1, 2 or 4) the export dialog offers first; 2 until the user picks another. */
    public int exportScale() {
        try {
            int stored = Integer.parseInt(node.get(EXPORT_SCALE, ""));
            return stored == 1 || stored == 2 || stored == 4 ? stored : 2;
        } catch (NumberFormatException exception) {
            return 2;
        }
    }

    public void setExportScale(int scale) {
        if (scale != 1 && scale != 2 && scale != 4) {
            throw new IllegalArgumentException("export scale must be 1, 2 or 4: " + scale);
        }
        put(EXPORT_SCALE, Integer.toString(scale));
    }

    /** «Fondo blanco para imprimir»: on unless the user turned it off. */
    public boolean exportPrintBackground() {
        return !"false".equals(node.get(EXPORT_PRINT_BACKGROUND, "true"));
    }

    public void setExportPrintBackground(boolean enabled) {
        put(EXPORT_PRINT_BACKGROUND, Boolean.toString(enabled));
    }

    // --- M6.2 tidy wires ---

    /** The options of Editar ▸ Ordenar cables: stubs and merges on, detours off until the user turns them on. */
    public TidyOptions tidyOptions() {
        return new TidyOptions(!"false".equals(node.get(TIDY_REMOVE_STUBS, "true")),
                !"false".equals(node.get(TIDY_MERGE_SEGMENTS, "true")),
                "true".equals(node.get(TIDY_FLATTEN_DETOURS, "false")));
    }

    public void setTidyOptions(TidyOptions options) {
        Objects.requireNonNull(options, "options");
        put(TIDY_REMOVE_STUBS, Boolean.toString(options.removeStubs()));
        put(TIDY_MERGE_SEGMENTS, Boolean.toString(options.mergeSegments()));
        put(TIDY_FLATTEN_DETOURS, Boolean.toString(options.flattenDetours()));
    }

    // --- M6.7 wires follow moves ---

    /** «Los cables siguen al mover»: on unless the user turned it off. */
    public boolean wiresFollow() {
        return !"false".equals(node.get(WIRES_FOLLOW, "true"));
    }

    public void setWiresFollow(boolean follow) {
        put(WIRES_FOLLOW, Boolean.toString(follow));
    }

    /** The key that inverts the follow mode while dragging; Shift when unset or unreadable. */
    public InvertModifier invertModifier() {
        try {
            return InvertModifier.valueOf(node.get(INVERT_MODIFIER, InvertModifier.SHIFT.name()));
        } catch (IllegalArgumentException exception) {
            return InvertModifier.SHIFT;
        }
    }

    public void setInvertModifier(InvertModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        put(INVERT_MODIFIER, modifier.name());
    }

    // --- M6.8 text font ---

    /**
     * The font of labels, texts and frame titles; {@link TextFont#DEFAULT} until chosen. A family that is not installed
     * here is kept (the font falls back when painting); an unreadable size gives the default size.
     */
    public TextFont textFont() {
        String family = optional(TEXT_FONT_FAMILY).orElse(TextFont.DEFAULT.family());
        int size;
        try {
            size = Integer.parseInt(node.get(TEXT_FONT_SIZE, ""));
        } catch (NumberFormatException exception) {
            size = TextFont.DEFAULT.size();
        }
        if (size < TextFont.MINIMUM_SIZE || size > TextFont.MAXIMUM_SIZE) {
            size = TextFont.DEFAULT.size();
        }
        return new TextFont(family, size);
    }

    /** Stores the family and the size; listeners hear one change, and none when both stay the same. */
    public void setTextFont(TextFont textFont) {
        Objects.requireNonNull(textFont, "textFont");
        boolean familyChanged = store(TEXT_FONT_FAMILY, textFont.family());
        boolean sizeChanged = store(TEXT_FONT_SIZE, Integer.toString(textFont.size()));
        if (familyChanged || sizeChanged) {
            notifyListeners(TEXT_FONT_FAMILY);
        }
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
        if (store(key, value)) {
            notifyListeners(key);
        }
    }

    /** Saves {@code value} without telling the listeners; false when it was already stored. */
    private boolean store(String key, String value) {
        if (value.equals(node.get(key, ""))) {
            return false;
        }
        node.put(key, value);
        try {
            node.flush();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not save preference {}", key, exception);
        }
        return true;
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
