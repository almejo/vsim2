package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * How each circuit file's window was split (Dividir la vista, spec §7): the dividers and every pane's zoom and centre,
 * keyed by the place: the file's absolute path in any spelling ({@link FileNames#normalized}) and, for a component
 * tab, the definition whose body it shows (spec §10.6); newest first, at most {@link #CAPACITY} places. Kept in the app
 * preferences under {@link AppPreferences#splitLayoutsNode()}, never in the circuit file: {@code splitLayouts/<n>}
 * holds {@code path}, {@code definition} (absent for the main circuit), {@code horizontal} and {@code vertical} (absent when that
 * divider is not there) and {@code zoom.<k>}, {@code centerX.<k>}, {@code centerY.<k>} for pane {@code k} in reading
 * order. A malformed or out-of-range entry is ignored silently; a storage failure is logged, never thrown. Stateless:
 * every call reads the preferences, so all windows see the same entries. EDT only.
 */
public final class SplitLayoutStore {

    public static final int CAPACITY = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(SplitLayoutStore.class);
    static final String PATH = "path";
    static final String DEFINITION = "definition";
    static final String HORIZONTAL = "horizontal";
    static final String VERTICAL = "vertical";
    static final String ZOOM = "zoom.";
    static final String CENTER_X = "centerX.";
    static final String CENTER_Y = "centerY.";

    /** A file's main circuit (empty definition) or one of its components' bodies (a component tab). */
    private record Place(Path path, Optional<String> definitionId) {
    }

    private record Entry(Place place, PaneLayout layout) {
    }

    private final AppPreferences preferences;

    public SplitLayoutStore(AppPreferences preferences) {
        this.preferences = preferences;
    }

    /** The layout remembered for {@code absoluteFile}'s main circuit; empty when there is none or it is unreadable. */
    public Optional<PaneLayout> layoutFor(Path absoluteFile) {
        return layoutFor(absoluteFile, Optional.empty());
    }

    /**
     * The layout remembered for the body of {@code definitionId} in {@code absoluteFile} (empty: its main circuit);
     * empty when there is none or it is unreadable.
     */
    public Optional<PaneLayout> layoutFor(Path absoluteFile, Optional<String> definitionId) {
        Place place = place(absoluteFile, definitionId);
        return read().stream().filter(entry -> entry.place().equals(place)).findFirst().map(Entry::layout);
    }

    /** {@link #remember(Path, Optional, PaneLayout)} for the file's main circuit. */
    public void remember(Path absoluteFile, PaneLayout layout) {
        remember(absoluteFile, Optional.empty(), layout);
    }

    /**
     * Remembers {@code layout} for the place (the file's main circuit, or the body of {@code definitionId}) as the
     * newest entry, dropping the oldest beyond {@link #CAPACITY}; a single pane without dividers forgets the place
     * instead (there is nothing to restore).
     */
    public void remember(Path absoluteFile, Optional<String> definitionId, PaneLayout layout) {
        if (!layout.isSplit()) {
            forget(absoluteFile, definitionId);
            return;
        }
        Place place = place(absoluteFile, definitionId);
        if (place.path().toString().length() > Preferences.MAX_VALUE_LENGTH) {
            LOGGER.warn("Not remembering the split of a path of {} characters", place.path().toString().length());
            return;
        }
        List<Entry> entries = new ArrayList<>(read());
        entries.removeIf(entry -> entry.place().equals(place));
        entries.add(0, new Entry(place, layout));
        write(entries);
    }

    /** Forgets the layout of {@code absoluteFile}'s main circuit; nothing when it has none. */
    public void forget(Path absoluteFile) {
        forget(absoluteFile, Optional.empty());
    }

    /** Forgets the layout of the place; nothing when it has none. */
    public void forget(Path absoluteFile, Optional<String> definitionId) {
        Place place = place(absoluteFile, definitionId);
        List<Entry> entries = new ArrayList<>(read());
        if (entries.removeIf(entry -> entry.place().equals(place))) {
            write(entries);
        }
    }

    private static Place place(Path absoluteFile, Optional<String> definitionId) {
        return new Place(FileNames.normalized(absoluteFile), definitionId);
    }

    private List<Entry> read() {
        List<Entry> entries = new ArrayList<>();
        try {
            Preferences node = preferences.splitLayoutsNode();
            for (String name : numbered(node.childrenNames())) {
                if (entries.size() == CAPACITY) {
                    break;
                }
                readEntry(node.node(name)).filter(read -> entries.stream()
                        .noneMatch(entry -> entry.place().equals(read.place()))).ifPresent(entries::add);
            }
        } catch (BackingStoreException | RuntimeException exception) {
            LOGGER.warn("Could not read the remembered splits", exception);
        }
        return entries;
    }

    private static Optional<Entry> readEntry(Preferences node) {
        try {
            String stored = node.get(PATH, "");
            if (stored.isBlank()) {
                return Optional.empty();
            }
            Path path = FileNames.normalized(Path.of(stored));
            Optional<String> definitionId = Optional.ofNullable(node.get(DEFINITION, null))
                    .filter(id -> !id.isBlank());
            OptionalDouble horizontal = fraction(node.get(HORIZONTAL, null));
            OptionalDouble vertical = fraction(node.get(VERTICAL, null));
            int dividers = (horizontal.isPresent() ? 1 : 0) + (vertical.isPresent() ? 1 : 0);
            int count = dividers == 0 ? 1 : dividers == 1 ? 2 : 4;
            if (count == 1 || node.get(ZOOM + count, null) != null) {
                return Optional.empty(); // a single pane is never stored; more views than panes is not ours
            }
            List<ViewState> views = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                views.add(new ViewState(number(node, ZOOM + index), number(node, CENTER_X + index),
                        number(node, CENTER_Y + index)));
            }
            return Optional.of(new Entry(new Place(path, definitionId), new PaneLayout(horizontal, vertical, views)));
        } catch (IllegalArgumentException exception) { // NumberFormatException and InvalidPathException included
            return Optional.empty();
        }
    }

    private void write(List<Entry> entries) {
        try {
            Preferences node = preferences.splitLayoutsNode();
            for (String name : node.childrenNames()) {
                node.node(name).removeNode();
            }
            for (int index = 0; index < Math.min(entries.size(), CAPACITY); index++) {
                writeEntry(node.node(Integer.toString(index)), entries.get(index));
            }
            node.flush();
        } catch (BackingStoreException | RuntimeException exception) {
            LOGGER.warn("Could not save the remembered splits", exception);
        }
    }

    private static void writeEntry(Preferences node, Entry entry) {
        PaneLayout layout = entry.layout();
        node.put(PATH, entry.place().path().toString());
        entry.place().definitionId().ifPresent(definitionId -> node.put(DEFINITION, definitionId));
        layout.horizontalDivider().ifPresent(fraction -> node.putDouble(HORIZONTAL, fraction));
        layout.verticalDivider().ifPresent(fraction -> node.putDouble(VERTICAL, fraction));
        for (int index = 0; index < layout.panes().size(); index++) {
            ViewState view = layout.panes().get(index);
            node.putDouble(ZOOM + index, view.zoom());
            node.putDouble(CENTER_X + index, view.centerX());
            node.putDouble(CENTER_Y + index, view.centerY());
        }
    }

    /** Empty when absent; a value outside (0, 1) or not a number is malformed (PaneLayout rejects it). */
    private static OptionalDouble fraction(String stored) {
        return stored == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(stored));
    }

    private static double number(Preferences node, String key) {
        String stored = node.get(key, null);
        if (stored == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return Double.parseDouble(stored);
    }

    /** The names that are plain non-negative numbers ({@code 0}, {@code 1}, …, not {@code 007}), in numeric order. */
    private static List<String> numbered(String[] names) {
        List<String> numbered = new ArrayList<>();
        for (String name : names) {
            if (name.matches("0|[1-9][0-9]{0,8}")) {
                numbered.add(name);
            }
        }
        numbered.sort(Comparator.comparingInt(Integer::parseInt));
        return numbered;
    }
}
