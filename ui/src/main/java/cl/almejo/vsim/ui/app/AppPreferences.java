package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.ScaleMode;
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
import java.util.function.BooleanSupplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** User preferences (spec §6.7, §6.10, §5.6), stored with {@code java.util.prefs}. Setters notify on real changes. */
public final class AppPreferences {

    public enum Theme { LIGHT, DARK, SYSTEM }

    /** How the canvas background shows the grid: dots (as always), soft lines, or nothing. */
    public enum GridStyle { DOTS, LINES, NONE }

    // --- Ajustar y guías ---

    /** «Ajustar»: the magnet as a whole; on unless the user turned it off. */
    public boolean snapToAlignments() {
        return !"false".equals(node.get(SNAP_TO_ALIGNMENTS, "true"));
    }

    public void setSnapToAlignments(boolean snap) {
        put(SNAP_TO_ALIGNMENTS, Boolean.toString(snap));
    }

    /** «Ajustar a ▸ Piezas»: edges and centres of other parts; on by default. */
    public boolean snapToParts() {
        return !"false".equals(node.get(SNAP_TO_PARTS, "true"));
    }

    public void setSnapToParts(boolean snap) {
        put(SNAP_TO_PARTS, Boolean.toString(snap));
    }

    /** «Ajustar a ▸ Guías»; on by default. */
    public boolean snapToGuides() {
        return !"false".equals(node.get(SNAP_TO_GUIDES, "true"));
    }

    public void setSnapToGuides(boolean snap) {
        put(SNAP_TO_GUIDES, Boolean.toString(snap));
    }

    /** «Ajustar a ▸ Líneas de la cuadrícula»; off by default. */
    public boolean snapToGridLines() {
        return "true".equals(node.get(SNAP_TO_GRID_LINES, "false"));
    }

    public void setSnapToGridLines(boolean snap) {
        put(SNAP_TO_GRID_LINES, Boolean.toString(snap));
    }

    /** «Mostrar guías»: the rulers and the guides; on by default. */
    public boolean showGuides() {
        return !"false".equals(node.get(SHOW_GUIDES, "true"));
    }

    public void setShowGuides(boolean show) {
        put(SHOW_GUIDES, Boolean.toString(show));
    }

    /** «Cuadrícula ▸»: dots unless the user chose otherwise (or the stored value is unreadable). */
    public GridStyle gridStyle() {
        return enumValue(GRID_STYLE, GridStyle.class, GridStyle.DOTS);
    }

    public void setGridStyle(GridStyle style) {
        Objects.requireNonNull(style, "style");
        put(GRID_STYLE, style.name());
    }

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
    static final String SNAP_TO_ALIGNMENTS = "snap.enabled";
    static final String SNAP_TO_PARTS = "snap.parts";
    static final String SNAP_TO_GUIDES = "snap.guides";
    static final String SNAP_TO_GRID_LINES = "snap.gridLines";
    static final String SHOW_GUIDES = "guides.show";
    static final String GRID_STYLE = "grid.style";
    static final String INVERT_MODIFIER = "invertModifier";
    static final String TEXT_FONT_FAMILY = "textFontFamily";
    static final String TEXT_FONT_SIZE = "textFontSize";
    static final String COLOR_SCHEMES = "colorSchemes";
    static final String ANIMATE_VIEW_MOVES = "animateViewMoves";
    static final String FIND_MATCH_CASE = "find.matchCase";
    static final String FIND_REGEX = "find.regex";
    static final String SIMPLIFY_EXPRESSIONS = "expressions.simplify";
    static final String PRINT_PAPER = "print.paper";
    static final String PRINT_ORIENTATION = "print.orientation";
    static final String PRINT_SCALE = "print.scale";
    static final String PRINT_WITH_VALUES = "print.withValues";
    static final String PRINT_BLACK_AND_WHITE = "print.blackAndWhite";
    static final String PRINT_HEADER_FOOTER = "print.headerFooter";
    static final String PRINT_DIRECTORY = "print.directory";
    private static final String PRINT_FIT = "fit";

    /** «Avanzar 10 t» (M6.1-B): the fixed step until the user picks another size. */
    public static final long DEFAULT_STEP_UNITS = 10;
    public static final long MAXIMUM_STEP_UNITS = 1_000_000_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);
    private static final String NODE = "cl/almejo/vsim2";

    private final Preferences node;
    private final BooleanSupplier systemReducesMotion;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public AppPreferences(Preferences node) {
        this(node, () -> false);
    }

    /** @param systemReducesMotion whether the desktop asks for less motion: the default of the animated view moves */
    AppPreferences(Preferences node, BooleanSupplier systemReducesMotion) {
        this.node = node;
        this.systemReducesMotion = systemReducesMotion;
    }

    public static AppPreferences system() {
        return new AppPreferences(Preferences.userRoot().node(NODE), ReducedMotion::requested);
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

    // --- Exportar a PDF e imprimir ---

    /**
     * The options «Exportar a PDF…» opens with: the remembered paper, orientation, scale and appearance switches, the
     * whole circuit and {@code title}. What to export and the title depend on the circuit and are never remembered;
     * unknown or corrupt values give the defaults of {@link PrintOptions#defaults}.
     */
    public PrintOptions printOptions(String title) {
        PrintOptions defaults = PrintOptions.defaults(title);
        return new PrintOptions(new PrintScope.WholeCircuit(),
                enumValue(PRINT_PAPER, Paper.class, defaults.paper()),
                enumValue(PRINT_ORIENTATION, PageOrientation.class, defaults.orientation()),
                printScale(defaults.scaleMode()),
                !"false".equals(node.get(PRINT_WITH_VALUES, "true")),
                "true".equals(node.get(PRINT_BLACK_AND_WHITE, "false")),
                "true".equals(node.get(PRINT_HEADER_FOOTER, "false")),
                title);
    }

    private ScaleMode printScale(ScaleMode fallback) {
        String stored = node.get(PRINT_SCALE, PRINT_FIT);
        if (PRINT_FIT.equals(stored)) {
            return new ScaleMode.FitToOnePage();
        }
        try {
            int percent = Integer.parseInt(stored);
            return percent >= ScaleMode.MINIMUM_PERCENT && percent <= ScaleMode.MAXIMUM_PERCENT
                    ? new ScaleMode.Percentage(percent) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E enumValue(String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, node.get(key, fallback.name()));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /** Remembers everything of {@code options} but the scope and the title; called only when a PDF was saved. */
    public void setPrintOptions(PrintOptions options) {
        Objects.requireNonNull(options, "options");
        put(PRINT_PAPER, options.paper().name());
        put(PRINT_ORIENTATION, options.orientation().name());
        put(PRINT_SCALE, switch (options.scaleMode()) {
            case ScaleMode.FitToOnePage fit -> PRINT_FIT;
            case ScaleMode.Percentage percentage -> Integer.toString(percentage.percent());
        });
        put(PRINT_WITH_VALUES, Boolean.toString(options.withValues()));
        put(PRINT_BLACK_AND_WHITE, Boolean.toString(options.blackAndWhite()));
        put(PRINT_HEADER_FOOTER, Boolean.toString(options.headerFooter()));
    }

    /** The folder of the last saved PDF; empty until the first one. */
    public Optional<Path> printDirectory() {
        return optional(PRINT_DIRECTORY).flatMap(stored -> {
            try {
                return Optional.of(Path.of(stored));
            } catch (InvalidPathException exception) {
                LOGGER.warn("Ignoring the stored PDF directory {}", stored, exception);
                return Optional.empty();
            }
        });
    }

    public void setPrintDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        put(PRINT_DIRECTORY, directory.toAbsolutePath().toString());
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

    // --- Buscar en el circuito: animated view moves ---

    /**
     * «Animar los movimientos de la vista»: the view travels (an eased pan or a «fly to») instead of jumping when the
     * app takes it somewhere. On by default, unless the desktop asks for reduced motion; a stored choice wins.
     */
    public boolean animateViewMoves() {
        String stored = node.get(ANIMATE_VIEW_MOVES, "");
        if (stored.isEmpty()) {
            return !systemReducesMotion.getAsBoolean();
        }
        return !"false".equals(stored);
    }

    public void setAnimateViewMoves(boolean animate) {
        if (animateViewMoves() == animate) {
            return;
        }
        put(ANIMATE_VIEW_MOVES, Boolean.toString(animate));
    }

    // --- Buscar en el circuito: match options ---

    /** «Aa» of the find bar and the component tree's filter: match upper and lower case (and accents). Off by default. */
    public boolean findMatchCase() {
        return node.getBoolean(FIND_MATCH_CASE, false);
    }

    public void setFindMatchCase(boolean matchCase) {
        if (findMatchCase() == matchCase) {
            return;
        }
        put(FIND_MATCH_CASE, Boolean.toString(matchCase));
    }

    /** «.*» of the find bar and the component tree's filter: the text is a regular expression. Off by default. */
    public boolean findRegex() {
        return node.getBoolean(FIND_REGEX, false);
    }

    public void setFindRegex(boolean regex) {
        if (findRegex() == regex) {
            return;
        }
        put(FIND_REGEX, Boolean.toString(regex));
    }

    // --- Circuito desde expresiones ---

    /** «Simplificar» of «Circuito desde expresiones»: minimize the outputs, not build them as written. On by default. */
    public boolean simplifyExpressions() {
        return node.getBoolean(SIMPLIFY_EXPRESSIONS, true);
    }

    public void setSimplifyExpressions(boolean simplify) {
        if (simplifyExpressions() == simplify) {
            return;
        }
        put(SIMPLIFY_EXPRESSIONS, Boolean.toString(simplify));
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

    // --- M6.8 custom colour schemes ---

    /** The user's colour schemes: child node {@code colorSchemes}, one child per scheme (see CustomColorSchemes). */
    public Preferences colorSchemesNode() {
        return node.node(COLOR_SCHEMES);
    }

    /** Saves the custom colour schemes and tells every listener (key {@code colorSchemes}). */
    public void colorSchemesChanged() {
        try {
            colorSchemesNode().flush();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not save the colour schemes", exception);
        }
        notifyListeners(COLOR_SCHEMES);
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
