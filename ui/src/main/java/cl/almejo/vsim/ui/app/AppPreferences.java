package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.core.io.LegacyGateMigration;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.print.PageGeometry;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.tools.WireMode;
import java.awt.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/** User preferences (spec §6.7, §6.10, §5.6), stored with {@code java.util.prefs}. Setters notify on real changes. */
public final class AppPreferences {

    public enum Theme { LIGHT, DARK, SYSTEM }

    /** How the canvas background shows the grid: dots (as always), soft lines, or nothing. */
    public enum GridStyle { DOTS, LINES, NONE }

    // --- Ajustar y guías ---

    /** «Ajustar»: the magnet as a whole; on unless the user turned it off. */
    public boolean snapToAlignments() {
        return flag(SNAP_TO_ALIGNMENTS, true);
    }

    public void setSnapToAlignments(boolean snap) {
        putFlag(SNAP_TO_ALIGNMENTS, snap);
    }

    /** «Ajustar a ▸ Piezas»: edges and centres of other parts; on by default. */
    public boolean snapToParts() {
        return flag(SNAP_TO_PARTS, true);
    }

    public void setSnapToParts(boolean snap) {
        putFlag(SNAP_TO_PARTS, snap);
    }

    /** «Ajustar a ▸ Guías»; on by default. */
    public boolean snapToGuides() {
        return flag(SNAP_TO_GUIDES, true);
    }

    public void setSnapToGuides(boolean snap) {
        putFlag(SNAP_TO_GUIDES, snap);
    }

    /** «Ajustar a ▸ Líneas de la cuadrícula»; off by default. */
    public boolean snapToGridLines() {
        return flag(SNAP_TO_GRID_LINES, false);
    }

    public void setSnapToGridLines(boolean snap) {
        putFlag(SNAP_TO_GRID_LINES, snap);
    }

    /** «Mostrar guías»: the rulers and the guides; on by default. */
    public boolean showGuides() {
        return flag(SHOW_GUIDES, true);
    }

    public void setShowGuides(boolean show) {
        putFlag(SHOW_GUIDES, show);
    }

    /**
     * «Mostrar hojas de impresión»: where the printed sheets fall, drawn over the circuit; off by default (a tool the
     * user turns on to arrange for print). The AI may layer it in one window.
     */
    public boolean showSheets() {
        return flag(SHOW_SHEETS, false);
    }

    public void setShowSheets(boolean show) {
        putFlag(SHOW_SHEETS, show);
    }

    /** «Valores de los buses»: the value written above each bus (canvas and exports); on by default. */
    public boolean showBusValues() {
        return flag(SHOW_BUS_VALUES, true);
    }

    public void setShowBusValues(boolean show) {
        putFlag(SHOW_BUS_VALUES, show);
    }

    /** «Resaltar la red al pasar»: hovering a wire highlights its net and shows the signal card; off by default. */
    public boolean highlightNetOnHover() {
        return flag(HIGHLIGHT_NET_ON_HOVER, false);
    }

    public void setHighlightNetOnHover(boolean highlight) {
        putFlag(HIGHLIGHT_NET_ON_HOVER, highlight);
    }

    /** «Mostrar los atajos al usarlos»: a chip with the shortcut of each action run; off by default. */
    public boolean showUsedShortcuts() {
        return flag(SHOW_USED_SHORTCUTS, false);
    }

    public void setShowUsedShortcuts(boolean show) {
        putFlag(SHOW_USED_SHORTCUTS, show);
    }

    /**
     * The version of VSim whose «Qué hay de nuevo» bubbles the user last saw (or that had none); empty on a first
     * install.
     */
    public Optional<String> newsShownVersion() {
        return optional(NEWS_SHOWN_VERSION);
    }

    public void setNewsShownVersion(String version) {
        Objects.requireNonNull(version, "version");
        put(NEWS_SHOWN_VERSION, version);
    }

    /** The id of the group Archivo ▸ Preferencias… showed last, so it opens there next time; empty when never. */
    public Optional<String> lastPreferencesGroup() {
        return optional(PREFERENCES_LAST_GROUP);
    }

    public void setLastPreferencesGroup(String groupId) {
        Objects.requireNonNull(groupId, "groupId");
        put(PREFERENCES_LAST_GROUP, groupId);
    }

    /** «Mostrar al iniciar» of the tip of the day (also in Preferencias ▸ General); on by default. */
    public boolean showTipAtStart() {
        return flag(SHOW_TIP_AT_START, true);
    }

    public void setShowTipAtStart(boolean show) {
        putFlag(SHOW_TIP_AT_START, show);
    }

    /** The calendar day the tip of the day last showed by itself; empty when never (or unreadable). */
    public Optional<LocalDate> tipShownDay() {
        try {
            return optional(TIP_SHOWN_DAY).map(LocalDate::parse);
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    public void setTipShownDay(LocalDate day) {
        Objects.requireNonNull(day, "day");
        put(TIP_SHOWN_DAY, day.toString());
    }

    /** The ids of the tips of the day already shown; empty once every tip was seen and the round starts again. */
    public Set<String> seenTips() {
        return read(SEEN_TIPS, "").lines().filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    /** One id per line, sorted so the stored text does not depend on the set's order. */
    public void setSeenTips(Set<String> tipIds) {
        for (String tipId : tipIds) {
            if (tipId.isBlank() || tipId.contains("\n") || tipId.contains("\r")) {
                throw new IllegalArgumentException("A tip id must be nonblank and single-line: " + tipId);
            }
        }
        put(SEEN_TIPS, String.join("\n", new TreeSet<>(tipIds)));
    }

    /** «Cuadrícula ▸»: dots unless the user chose otherwise (or the stored value is unreadable). */
    public GridStyle gridStyle() {
        return enumValue(GRID_STYLE, GridStyle.class, GridStyle.DOTS);
    }

    public void setGridStyle(GridStyle style) {
        Objects.requireNonNull(style, "style");
        put(GRID_STYLE, style.name());
    }

    /** «Cuadrícula ▸ Separación ▸»: the units between the grid lines, also where they attract; 2 by default. */
    public int gridLineSpacing() {
        try {
            int stored = Integer.parseInt(read(GRID_LINE_SPACING, ""));
            return GRID_LINE_SPACINGS.contains(stored) ? stored : DEFAULT_GRID_LINE_SPACING;
        } catch (NumberFormatException exception) {
            return DEFAULT_GRID_LINE_SPACING;
        }
    }

    /** One of {@link #GRID_LINE_SPACINGS} (IllegalArgumentException otherwise). */
    public void setGridLineSpacing(int spacing) {
        if (!GRID_LINE_SPACINGS.contains(spacing)) {
            throw new IllegalArgumentException("grid line spacing " + spacing + " is not one of "
                    + GRID_LINE_SPACINGS);
        }
        put(GRID_LINE_SPACING, Integer.toString(spacing));
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

    /** The accessibility lens's shape (Preferencias, Accesibilidad, Lupa): a circle by default, or a rounded rectangle. */
    public enum LensShape {
        ROUND, RECTANGLE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The accessibility lens's size: its radius in logical pixels (the rectangle is 1.5 x that wide, 1 x high). */
    public enum LensSize {
        SMALL(70), MEDIUM(100), LARGE(140);

        private final int radius;

        LensSize(int radius) {
            this.radius = radius;
        }

        public int radius() {
            return radius;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final String LENS_SHAPE = "lens.shape";
    static final String LENS_SIZE = "lens.size";
    static final String THEME = "theme";
    static final String COLOR_SCHEME = "colorScheme";
    static final String LANGUAGE = "language";
    private static final String SPLIT_BUTTON_PREFIX = "splitButton.";
    static final String FULL_SCREEN_WAVEFORMS_PINNED = "fullScreen.waveformsPinned";
    static final String LIBRARY_DIRECTORY = "libraryDirectory";
    static final String RECENT = "recent";
    static final String RESTORE_SESSION = "restoreSession";
    static final String SESSION = "session";
    static final String SHORTCUTS = "shortcuts";
    static final String STEP_UNITS = "simulation.stepUnits";
    static final String EXPORT_DIRECTORY = "export.directory";
    static final String LOGISIM_REPORT_WIDTH = "logisim.reportWidth";
    static final String LOGISIM_REPORT_HEIGHT = "logisim.reportHeight";
    static final String EXPORT_SCALE = "export.scale";
    static final String EXPORT_PRINT_BACKGROUND = "export.printBackground";
    static final String EXPORT_INCLUDE_STICKY_NOTES = "export.includeStickyNotes";
    static final String TIDY_SEPARATE_OVERLAPS = "tidySeparateOverlaps";
    static final String TIDY_ALIGN_COLUMNS = "tidyAlignColumns";
    static final String TIDY_STRAIGHTEN = "tidyStraighten";
    static final String TIDY_REMOVE_STUBS = "tidyRemoveStubs";
    static final String TIDY_MERGE_SEGMENTS = "tidyMergeSegments";
    static final String TIDY_FLATTEN_DETOURS = "tidyFlattenDetours";
    static final String WIRES_FOLLOW = "wiresFollow";
    static final String WIRE_MODE = "wireMode";
    static final String SNAP_TO_ALIGNMENTS = "snap.enabled";
    static final String SNAP_TO_PARTS = "snap.parts";
    static final String SNAP_TO_GUIDES = "snap.guides";
    static final String SNAP_TO_GRID_LINES = "snap.gridLines";
    static final String SHOW_GUIDES = "guides.show";
    static final String SHOW_BUS_VALUES = "view.busValues";
    static final String HIGHLIGHT_NET_ON_HOVER = "view.highlightNet";
    static final String SHOW_SHEETS = "sheets.show";
    static final String SHOW_USED_SHORTCUTS = "shortcutEcho.show";
    static final String NEWS_SHOWN_VERSION = "whatsNew.shownVersion";
    static final String PREFERENCES_LAST_GROUP = "preferences.lastGroup";
    static final String SHOW_TIP_AT_START = "tip.showAtStart";
    static final String TIP_SHOWN_DAY = "tip.shownDay";
    static final String SEEN_TIPS = "tip.seen";
    static final String GRID_STYLE = "grid.style";
    static final String SPLIT_LAYOUTS = "splitLayouts";
    private static final String CIRCUIT_TREE_SPLIT = "ui.circuitTree.split";
    static final String TERMINAL_WINDOWS = "terminal-windows";
    static final String GRID_LINE_SPACING = "grid.lineSpacing";
    static final String INVERT_MODIFIER = "invertModifier";
    static final String INTERFACE_ZOOM = "interface.zoom";
    static final String INTERFACE_FONT_FAMILY = "interface.fontFamily";
    static final String PRESENTATION_INTERFACE_ZOOM = "interface.presentationZoom";
    static final String TEXT_FONT_FAMILY = "textFontFamily";
    static final String TEXT_FONT_SIZE = "textFontSize";
    static final String COLOR_SCHEMES = "colorSchemes";
    static final String ANIMATIONS = "animations";
    static final String ANIMATE_VIEW_MOVES = "animateViewMoves";
    static final String FIND_MATCH_CASE = "find.matchCase";
    static final String FIND_REGEX = "find.regex";
    static final String SIMPLIFY_EXPRESSIONS = "expressions.simplify";
    static final String EXTERNAL_EDITOR = "memory.externalEditor";
    static final String PRINT_PAPER = "print.paper";
    static final String PRINT_ORIENTATION = "print.orientation";
    static final String PRINT_SCALE = "print.scale";
    static final String PRINT_WITH_VALUES = "print.withValues";
    static final String PRINT_BLACK_AND_WHITE = "print.blackAndWhite";
    static final String PRINT_HEADER_FOOTER = "print.headerFooter";
    static final String PRINT_INCLUDE_STICKY_NOTES = "print.includeStickyNotes";
    static final String PRINT_SHEET_GUIDES = "print.sheetGuides";
    static final String PRINT_SHOW_DATE = "print.showDate";
    static final String PRINT_SHOW_FILE_NAME = "print.showFileName";
    static final String PRINT_SHOW_SHEET_NUMBER = "print.showSheetNumber";
    static final String PRINT_SKIP_EMPTY_SHEETS = "print.skipEmptySheets";
    static final String PRINT_DIRECTORY = "print.directory";
    static final String PRINT_MARGIN_POINTS = "print.marginPoints";
    private static final String PRINT_FIT = "fit";
    static final String AI_PORT = "ai.port";
    static final String AI_SECRET = "ai.secret";
    static final String AI_START_WITH_VSIM = "ai.startWithVsim";
    static final String AI_PRESENTING = "ai.presenting";
    static final String AI_PACE = "ai.pace";
    static final String AI_PAUSE_MILLIS = "ai.pauseMillis";
    /** Spec §9.1: high, uncommon, inside IANA's unassigned 47002-47099 (checked 2026-10-21). */
    public static final int DEFAULT_AI_PORT = 47017;
    private static final int MINIMUM_AI_PORT = 1024;
    private static final int MAXIMUM_PORT = 65_535;

    /** «Avanzar 10 t» (M6.1-B): the fixed step until the user picks another size. */
    /** The spacings «Cuadrícula ▸ Separación ▸» offers, in grid units. */
    public static final List<Integer> GRID_LINE_SPACINGS = List.of(2, 4, 5, 10);
    public static final int DEFAULT_GRID_LINE_SPACING = 2;
    public static final long DEFAULT_STEP_UNITS = 10;
    public static final long MAXIMUM_STEP_UNITS = 1_000_000_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);
    private static final String NODE = "cl/almejo/vsim2";

    private final Preferences node;
    private final Listeners<Runnable> listeners;
    /** Null but for the preferences of one window ({@link #layered()}). */
    private final PreferenceLayer layer;
    /** The layers of every window over the same saved preferences (shared; a closed window's layer goes away). */
    private final Set<PreferenceLayer> windowLayers;

    public AppPreferences(Preferences node) {
        this.node = node;
        this.listeners = new Listeners<>();
        this.layer = null;
        this.windowLayers = Collections.newSetFromMap(new WeakHashMap<>());
    }

    /** The same saved preferences and listeners as {@code saved}, read through a new layer of one window. */
    private AppPreferences(AppPreferences saved) {
        this.node = saved.node;
        this.listeners = saved.listeners;
        this.layer = new PreferenceLayer(() -> notifyListeners("layer"));
        this.windowLayers = saved.windowLayers;
        windowLayers.add(layer);
    }

    /**
     * The preferences of one window (spec §7): the same saved values and listeners, read through a new, empty
     * {@link PreferenceLayer} that only this window sees. Writing through it saves as always (and replaces a layered
     * value of the same setting); every listener hears a layer change and re-reads, the other windows reading the
     * saved values.
     */
    public AppPreferences layered() {
        return new AppPreferences(this);
    }

    /** The layer of a window's preferences ({@link #layered()}); empty for the saved preferences themselves. */
    public Optional<PreferenceLayer> layer() {
        return Optional.ofNullable(layer);
    }

    public static AppPreferences system() {
        return new AppPreferences(Preferences.userRoot().node(NODE));
    }

    public static AppPreferences inMemory() {
        return new AppPreferences(new MemoryPreferences());
    }

    public Theme theme() {
        return enumValue(THEME, Theme.class, Theme.SYSTEM);
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

    /** The mode id a split button ({@code preferenceId}) was last left on; empty when never chosen. */
    public Optional<String> splitButtonMode(String preferenceId) {
        return optional(SPLIT_BUTTON_PREFIX + preferenceId);
    }

    public void setSplitButtonMode(String preferenceId, String modeId) {
        Objects.requireNonNull(modeId, "modeId");
        put(SPLIT_BUTTON_PREFIX + preferenceId, modeId);
    }

    /**
     * Whether the waveform panel stays docked in presentation mode (its pin) instead of hiding behind the bottom
     * edge; off until the user pins it, then remembered for the next time.
     */
    public boolean fullScreenWaveformsPinned() {
        return "true".equals(read(FULL_SCREEN_WAVEFORMS_PINNED, ""));
    }

    public void setFullScreenWaveformsPinned(boolean pinned) {
        if (fullScreenWaveformsPinned() == pinned) {
            return;
        }
        putFlag(FULL_SCREEN_WAVEFORMS_PINNED, pinned);
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

    /**
     * Ordered palette favourites, shared by all windows; unavailable component ids remain stored. Ids saved before
     * the gates took parameters read as today's entries ({@code nand2} is {@code nand}, {@code and3} is {@code and}).
     */
    public List<String> paletteFavorites() {
        return read("palette.favourites", "").lines().filter(id -> !id.isBlank())
                .map(id -> LegacyGateMigration.paletteIdFor(id).orElse(id)).distinct().toList();
    }

    /** Saves stable type ids rather than names, so renaming a user component preserves its favourite. */
    public void setPaletteFavorites(List<String> typeIds) {
        List<String> unique = List.copyOf(typeIds).stream().distinct().toList();
        for (String typeId : unique) {
            if (typeId.isBlank() || typeId.contains("\n") || typeId.contains("\r")) {
                throw new IllegalArgumentException("A favourite must have a nonblank, single-line type id");
            }
        }
        put("palette.favourites", String.join("\n", unique));
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
            long stored = Long.parseLong(read(STEP_UNITS, ""));
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

    // --- Importar y exportar con Logisim ---

    /** The smallest size of the report window that is remembered; a smaller stored one is ignored. */
    private static final int MINIMUM_REPORT_SIDE = 200;
    private static final int MAXIMUM_REPORT_SIDE = 10_000;

    /** The size the user gave the import / export report window; empty until they resize it (or if unreadable). */
    public Optional<java.awt.Dimension> logisimReportSize() {
        try {
            int width = Integer.parseInt(read(LOGISIM_REPORT_WIDTH, ""));
            int height = Integer.parseInt(read(LOGISIM_REPORT_HEIGHT, ""));
            boolean usable = width >= MINIMUM_REPORT_SIDE && width <= MAXIMUM_REPORT_SIDE
                    && height >= MINIMUM_REPORT_SIDE && height <= MAXIMUM_REPORT_SIDE;
            return usable ? Optional.of(new java.awt.Dimension(width, height)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public void setLogisimReportSize(java.awt.Dimension size) {
        Objects.requireNonNull(size, "size");
        store(LOGISIM_REPORT_WIDTH, Integer.toString(size.width));
        store(LOGISIM_REPORT_HEIGHT, Integer.toString(size.height));
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
            int stored = Integer.parseInt(read(EXPORT_SCALE, ""));
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
        return flag(EXPORT_PRINT_BACKGROUND, true);
    }

    public void setExportPrintBackground(boolean enabled) {
        putFlag(EXPORT_PRINT_BACKGROUND, enabled);
    }

    /** «Incluir notas adhesivas» in the image export: off unless the user turned it on. */
    public boolean exportIncludeStickyNotes() {
        return flag(EXPORT_INCLUDE_STICKY_NOTES, false);
    }

    public void setExportIncludeStickyNotes(boolean enabled) {
        putFlag(EXPORT_INCLUDE_STICKY_NOTES, enabled);
    }

    // --- Exportar a PDF e imprimir ---

    /** «Incluir notas adhesivas» in the PDF: off unless the user turned it on. */
    public boolean printIncludeStickyNotes() {
        return flag(PRINT_INCLUDE_STICKY_NOTES, false);
    }

    public void setPrintIncludeStickyNotes(boolean enabled) {
        putFlag(PRINT_INCLUDE_STICKY_NOTES, enabled);
    }

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
                flag(PRINT_WITH_VALUES, true),
                flag(PRINT_BLACK_AND_WHITE, false),
                flag(PRINT_HEADER_FOOTER, false),
                title,
                flag(PRINT_INCLUDE_STICKY_NOTES, false),
                flag(PRINT_SHEET_GUIDES, defaults.sheetGuides()),
                flag(PRINT_SHOW_DATE, defaults.showDate()),
                flag(PRINT_SHOW_FILE_NAME, defaults.showFileName()),
                flag(PRINT_SHOW_SHEET_NUMBER, defaults.showSheetNumber()),
                flag(PRINT_SKIP_EMPTY_SHEETS, defaults.skipEmptySheets()));
    }

    private ScaleMode printScale(ScaleMode fallback) {
        String stored = read(PRINT_SCALE, PRINT_FIT);
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

    /** A stored flag: with a default of true only "false" turns it off, with false only "true" turns it on. */
    private boolean flag(String key, boolean defaultValue) {
        return defaultValue ? !"false".equals(read(key, "true")) : "true".equals(read(key, "false"));
    }

    private void putFlag(String key, boolean value) {
        put(key, Boolean.toString(value));
    }

    private <E extends Enum<E>> E enumValue(String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, read(key, fallback.name()));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /**
     * Remembers everything of {@code options} but the scope and the title; called when a PDF was saved or a print
     * went out.
     */
    public void setPrintOptions(PrintOptions options) {
        Objects.requireNonNull(options, "options");
        put(PRINT_PAPER, options.paper().name());
        put(PRINT_ORIENTATION, options.orientation().name());
        put(PRINT_SCALE, switch (options.scaleMode()) {
            case ScaleMode.FitToOnePage fit -> PRINT_FIT;
            case ScaleMode.Percentage percentage -> Integer.toString(percentage.percent());
        });
        putFlag(PRINT_WITH_VALUES, options.withValues());
        putFlag(PRINT_BLACK_AND_WHITE, options.blackAndWhite());
        putFlag(PRINT_HEADER_FOOTER, options.headerFooter());
        putFlag(PRINT_INCLUDE_STICKY_NOTES, options.includeStickyNotes());
        putFlag(PRINT_SHEET_GUIDES, options.sheetGuides());
        putFlag(PRINT_SHOW_DATE, options.showDate());
        putFlag(PRINT_SHOW_FILE_NAME, options.showFileName());
        putFlag(PRINT_SHOW_SHEET_NUMBER, options.showSheetNumber());
        putFlag(PRINT_SKIP_EMPTY_SHEETS, options.skipEmptySheets());
    }

    /**
     * The blank border of the sheets the canvas lays out, in points: 1 cm until a print on a printer with a wider
     * hardware margin (or a PDF saved with one) remembers it, so the canvas shows what the next print gives. Never less
     * than 1 cm (the least any sheet is laid out with): an unreadable, negative or narrower stored value reads as 1 cm.
     */
    public double printMarginPoints() {
        try {
            double stored = Double.parseDouble(read(PRINT_MARGIN_POINTS, ""));
            return Double.isFinite(stored) ? Math.max(PageGeometry.CENTIMETRE_POINTS, stored)
                    : PageGeometry.CENTIMETRE_POINTS;
        } catch (NumberFormatException exception) {
            return PageGeometry.CENTIMETRE_POINTS;
        }
    }

    /** Remembers the margin of a successful print or PDF; {@code marginPoints} must be finite and {@code >= 0}. */
    public void setPrintMarginPoints(double marginPoints) {
        if (!Double.isFinite(marginPoints) || marginPoints < 0) {
            throw new IllegalArgumentException("marginPoints must be finite and >= 0: " + marginPoints);
        }
        put(PRINT_MARGIN_POINTS, Double.toString(marginPoints));
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

    /**
     * The options of Editar ▸ Ordenar circuito: separating, aligning and straightening on, stubs and merges on, detours off until the
     * user turns them on.
     */
    public CircuitTidyOptions tidyOptions() {
        return new CircuitTidyOptions(flag(TIDY_SEPARATE_OVERLAPS, true),
                flag(TIDY_ALIGN_COLUMNS, true), flag(TIDY_STRAIGHTEN, true),
                new TidyOptions(flag(TIDY_REMOVE_STUBS, true),
                        flag(TIDY_MERGE_SEGMENTS, true),
                        flag(TIDY_FLATTEN_DETOURS, false)));
    }

    public void setTidyOptions(CircuitTidyOptions options) {
        Objects.requireNonNull(options, "options");
        putFlag(TIDY_SEPARATE_OVERLAPS, options.separateOverlaps());
        putFlag(TIDY_ALIGN_COLUMNS, options.alignColumns());
        putFlag(TIDY_STRAIGHTEN, options.straighten());
        putFlag(TIDY_REMOVE_STUBS, options.wires().removeStubs());
        putFlag(TIDY_MERGE_SEGMENTS, options.wires().mergeSegments());
        putFlag(TIDY_FLATTEN_DETOURS, options.wires().flattenDetours());
    }

    // --- M6.7 wires follow moves ---

    /** «Los cables siguen al mover»: on unless the user turned it off. */
    public boolean wiresFollow() {
        return flag(WIRES_FOLLOW, true);
    }

    public void setWiresFollow(boolean follow) {
        putFlag(WIRES_FOLLOW, follow);
    }

    /**
     * How Selección draws a wire from a pin or a wire (Editar ▸ «Dibujar cables»): {@link WireMode#CONTINUOUS} unless
     * the user chose «Por trozos»; one for the whole app.
     */
    public WireMode wireMode() {
        return WireMode.of(read(WIRE_MODE, WireMode.CONTINUOUS.id()));
    }

    public void setWireMode(WireMode mode) {
        Objects.requireNonNull(mode, "mode");
        put(WIRE_MODE, mode.id());
    }

    /** The key that inverts the follow mode while dragging; Shift when unset or unreadable. */
    public InvertModifier invertModifier() {
        return enumValue(INVERT_MODIFIER, InvertModifier.class, InvertModifier.SHIFT);
    }

    public void setInvertModifier(InvertModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        put(INVERT_MODIFIER, modifier.name());
    }

    // --- Lupa ---

    /** The lens's shape; round when unset or unreadable. */
    public LensShape lensShape() {
        return lowerCaseEnumValue(LENS_SHAPE, LensShape.class, LensShape.ROUND);
    }

    public void setLensShape(LensShape shape) {
        Objects.requireNonNull(shape, "shape");
        put(LENS_SHAPE, shape.id());
    }

    /** The lens's size; medium when unset or unreadable. */
    public LensSize lensSize() {
        return lowerCaseEnumValue(LENS_SIZE, LensSize.class, LensSize.MEDIUM);
    }

    public void setLensSize(LensSize size) {
        Objects.requireNonNull(size, "size");
        put(LENS_SIZE, size.id());
    }

    private <E extends Enum<E>> E lowerCaseEnumValue(String key, Class<E> type, E fallback) {
        String stored = read(key, "");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(stored)) {
                return constant;
            }
        }
        return fallback;
    }

    // --- Animaciones ---

    /**
     * Preferencias ▸ «Animaciones»: whether VSim animates at all (on by default). A VSim-only setting: nothing reads
     * the desktop's own switch. Ask {@link Animations#enabled} rather than this, the one point every animation obeys.
     */
    public boolean animations() {
        return flag(ANIMATIONS, true);
    }

    public void setAnimations(boolean animations) {
        if (animations() != animations) {
            putFlag(ANIMATIONS, animations);
        }
    }

    // --- Buscar en el circuito: animated view moves ---

    /**
     * «Animar los movimientos de la vista»: the view travels (an eased pan or a «fly to») instead of jumping when the
     * app takes it somewhere; on by default. The choice is kept with «Animaciones: No», which still wins: ask
     * {@link Animations#viewMoves}.
     */
    public boolean animateViewMoves() {
        return flag(ANIMATE_VIEW_MOVES, true);
    }

    public void setAnimateViewMoves(boolean animate) {
        if (animateViewMoves() == animate) {
            return;
        }
        putFlag(ANIMATE_VIEW_MOVES, animate);
    }

    /** The height of the circuit tree above the palette, in pixels; empty until the user drags the divider. */
    public java.util.OptionalInt circuitTreeSplit() {
        try {
            int stored = Integer.parseInt(read(CIRCUIT_TREE_SPLIT, ""));
            return stored > 0 ? java.util.OptionalInt.of(stored) : java.util.OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return java.util.OptionalInt.empty();
        }
    }

    public void setCircuitTreeSplit(int height) {
        if (height > 0 && circuitTreeSplit().orElse(-1) != height) {
            put(CIRCUIT_TREE_SPLIT, Integer.toString(height));
        }
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
        putFlag(FIND_MATCH_CASE, matchCase);
    }

    /** «.*» of the find bar and the component tree's filter: the text is a regular expression. Off by default. */
    public boolean findRegex() {
        return node.getBoolean(FIND_REGEX, false);
    }

    public void setFindRegex(boolean regex) {
        if (findRegex() == regex) {
            return;
        }
        putFlag(FIND_REGEX, regex);
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
        putFlag(SIMPLIFY_EXPRESSIONS, simplify);
    }

    // --- Memoria: editor externo ---

    /**
     * «Editor externo»: the command that opens a memory's linked file (a program and its arguments; the file goes
     * last). Empty means the system's editor.
     */
    public Optional<String> externalEditor() {
        return optional(EXTERNAL_EDITOR);
    }

    public void setExternalEditor(Optional<String> command) {
        Objects.requireNonNull(command, "command");
        put(EXTERNAL_EDITOR, command.map(String::strip).orElse(""));
    }

    // --- Interface zoom and font (Preferencias, Aspecto, Ventana) ---

    /** The sizes «Tamaño de la interfaz» offers, in percent of the look and feel's own size. */
    public static final List<Integer> INTERFACE_ZOOMS = List.of(100, 125, 150, 175, 200);
    /** «En modo presentación: Sin cambio»: presentation mode keeps the interface's size. */
    public static final int PRESENTATION_ZOOM_UNCHANGED = 0;
    /** What presentation mode raises the interface to until the user chooses (the projector in the back row). */
    public static final int DEFAULT_PRESENTATION_ZOOM = 150;

    /** The whole interface's size in percent (menus, panels, dialogs); 100 when unset or not one of the offered. */
    public int interfaceZoom() {
        return storedZoom(INTERFACE_ZOOM, 100, false);
    }

    public void setInterfaceZoom(int percent) {
        requireOffered(percent, false);
        put(INTERFACE_ZOOM, Integer.toString(percent));
    }

    /** The interface's font family; empty is the look and feel's own. */
    public Optional<String> interfaceFontFamily() {
        return optional(INTERFACE_FONT_FAMILY);
    }

    public void setInterfaceFontFamily(Optional<String> family) {
        Objects.requireNonNull(family, "family");
        put(INTERFACE_FONT_FAMILY, family.map(String::strip).orElse(""));
    }

    /**
     * The interface's size while a window is in «Modo presentación», in percent, or
     * {@link #PRESENTATION_ZOOM_UNCHANGED}; it never makes the interface smaller than {@link #interfaceZoom()}.
     */
    public int presentationInterfaceZoom() {
        return storedZoom(PRESENTATION_INTERFACE_ZOOM, DEFAULT_PRESENTATION_ZOOM, true);
    }

    public void setPresentationInterfaceZoom(int percent) {
        requireOffered(percent, true);
        put(PRESENTATION_INTERFACE_ZOOM, Integer.toString(percent));
    }

    private int storedZoom(String key, int fallback, boolean unchangedAllowed) {
        try {
            int stored = Integer.parseInt(read(key, ""));
            return INTERFACE_ZOOMS.contains(stored) || (unchangedAllowed && stored == PRESENTATION_ZOOM_UNCHANGED)
                    ? stored : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static void requireOffered(int percent, boolean unchangedAllowed) {
        if (!INTERFACE_ZOOMS.contains(percent) && !(unchangedAllowed && percent == PRESENTATION_ZOOM_UNCHANGED)) {
            throw new IllegalArgumentException("Not an offered interface size: " + percent);
        }
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
            size = Integer.parseInt(read(TEXT_FONT_SIZE, ""));
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

    /** How each recent file's window was split (Dividir la vista): child node {@code splitLayouts}. */
    public Preferences splitLayoutsNode() {
        return node.node(SPLIT_LAYOUTS);
    }

    /** Where each Terminal window was and how big (spec §9): child node {@code terminal-windows}. */
    public Preferences terminalWindowsNode() {
        return node.node(TERMINAL_WINDOWS);
    }

    /** «Al iniciar, abrir los circuitos de la última sesión» (M6-B): on unless the user turned it off. */
    public boolean restoreSession() {
        return flag(RESTORE_SESSION, true);
    }

    public void setRestoreSession(boolean enabled) {
        if (restoreSession() == enabled) {
            return;
        }
        putFlag(RESTORE_SESSION, enabled);
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

    // --- IA (MCP) ---

    /** The port the AI server listens on (spec §9.1): the default until the user changes it; VSim never does. */
    public int aiPort() {
        try {
            int stored = Integer.parseInt(read(AI_PORT, ""));
            return isUserPort(stored) ? stored : DEFAULT_AI_PORT;
        } catch (NumberFormatException exception) {
            return DEFAULT_AI_PORT;
        }
    }

    /** 1024 to 65535 (IllegalArgumentException otherwise): below are the system's ports. */
    public void setAiPort(int port) {
        if (!isUserPort(port)) {
            throw new IllegalArgumentException("port " + port + " is not between 1024 and 65535");
        }
        put(AI_PORT, Integer.toString(port));
    }

    /** The remembered secret Claude Code sends; empty until the first start makes one (spec §9). */
    public Optional<String> aiSecret() {
        return optional(AI_SECRET);
    }

    public void setAiSecret(String secret) {
        Objects.requireNonNull(secret, "secret");
        put(AI_SECRET, secret);
    }

    /** Whether the AI server starts with VSim; off by default (spec §9.1). */
    public boolean aiStartWithVsim() {
        return flag(AI_START_WITH_VSIM, false);
    }

    public void setAiStartWithVsim(boolean start) {
        putFlag(AI_START_WITH_VSIM, start);
    }

    /** Whether the AI presents its work («Presentar», spec §4); off by default. */
    public boolean aiPresenting() {
        return flag(AI_PRESENTING, false);
    }

    public void setAiPresenting(boolean presenting) {
        putFlag(AI_PRESENTING, presenting);
    }

    /** «Paso a paso» by default (spec §5). */
    public PresentationPace aiPace() {
        return enumValue(AI_PACE, PresentationPace.class, PresentationPace.STEP_BY_STEP);
    }

    /**
     * The user's pace, saved; it also replaces the pace the AI asked for its task in every window (the AI works in
     * the circuit's first window, the user may pick it in another one: the user's choice wins).
     */
    public void setAiPace(PresentationPace pace) {
        Objects.requireNonNull(pace, "pace");
        putEverywhere(AI_PACE, pace.name());
    }

    /** The pause between steps in «De corrido», 300 to 5000 ms (clamped); 1000 by default. */
    public int aiPauseMillis() {
        try {
            return clampPause(Integer.parseInt(read(AI_PAUSE_MILLIS, "")));
        } catch (NumberFormatException exception) {
            return (int) PresentationSettings.DEFAULT_PAUSE.toMillis();
        }
    }

    /** The user's pause, saved; like {@link #setAiPace}, it replaces the AI's in every window. */
    public void setAiPauseMillis(int millis) {
        putEverywhere(AI_PAUSE_MILLIS, Integer.toString(clampPause(millis)));
    }

    private static int clampPause(int millis) {
        int minimum = (int) PresentationSettings.MINIMUM_PAUSE.toMillis();
        int maximum = (int) PresentationSettings.MAXIMUM_PAUSE.toMillis();
        return Math.max(minimum, Math.min(maximum, millis));
    }

    private static boolean isUserPort(int port) {
        return port >= MINIMUM_AI_PORT && port <= MAXIMUM_PORT;
    }

    /** The shape editor's independent magnet, enabled initially. */
    public boolean shapeEditorSnap() {
        return Boolean.parseBoolean(read("shapeEditor.snap", "true"));
    }

    public void setShapeEditorSnap(boolean enabled) {
        putFlag("shapeEditor.snap", enabled);
    }

    /** Whether the shape editor shows its grid (its toolbar toggle), on initially. */
    public boolean shapeEditorGrid() {
        return Boolean.parseBoolean(read("shapeEditor.grid", "true"));
    }

    public void setShapeEditorGrid(boolean shown) {
        putFlag("shapeEditor.grid", shown);
    }

    /** The shape editor's last bounds, kept separately from the circuit viewport; none before its first use. */
    public Optional<Rectangle> shapeEditorBounds() {
        String[] values = read("shapeEditor.bounds", "").split(",");
        try {
            if (values.length == 4) {
                return Optional.of(new Rectangle(Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                        Math.max(640, Integer.parseInt(values[2])), Math.max(480, Integer.parseInt(values[3]))));
            }
        } catch (NumberFormatException exception) {
            LOGGER.debug("Ignoring invalid shape editor bounds", exception);
        }
        return Optional.empty();
    }

    public void setShapeEditorBounds(Rectangle bounds) {
        put("shapeEditor.bounds", bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
    }

    private Optional<String> optional(String key) {
        String value = read(key, "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** The layered value of {@code key} when this window has one, else the saved one. */
    private String read(String key, String fallback) {
        if (layer != null) {
            Optional<String> layered = layer.valueOf(key);
            if (layered.isPresent()) {
                return layered.get();
            }
        }
        return node.get(key, fallback);
    }

    private void put(String key, String value) {
        // the user's own choice in this window replaces what was layered there, and is saved
        boolean wasLayered = layer != null && layer.forget(key);
        if (store(key, value) || wasLayered) {
            notifyListeners(key);
        }
    }

    /** Like {@link #put}, replacing what was layered for {@code key} in every window, not only this one. */
    private void putEverywhere(String key, String value) {
        boolean wasLayered = false;
        for (PreferenceLayer windowLayer : List.copyOf(windowLayers)) {
            wasLayered |= windowLayer.forget(key);
        }
        if (store(key, value) || wasLayered) {
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
        for (RuntimeException failure : listeners.fireAll(Runnable::run)) {
            LOGGER.error("A listener failed to follow the change of preference {}", key, failure);
        }
    }
}
