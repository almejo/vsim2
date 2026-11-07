package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.window.InspectorState;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Plain preference keys of a {@link WindowState} (spec §4.2, contract C4: no JSON) and of a session tab
 * ({@link TabState}), the stored path of an entry and the numbered child nodes that hold lists. A missing or bad value
 * means "no state", never an exception.
 */
final class SessionKeys {

    /** The file of a Recientes entry, a session tab or (before tabs) a session document. */
    static final String PATH = "path";
    static final String X = "x";
    static final String Y = "y";
    static final String WIDTH = "width";
    static final String HEIGHT = "height";
    static final String MAXIMIZED = "maximized";
    static final String ZOOM = "zoom";
    static final String CENTER_X = "centerX";
    static final String CENTER_Y = "centerY";
    /** A session window's tab nodes: {@code tab0}, {@code tab1}, … */
    static final String TAB = "tab";
    /** The definition a component tab shows (absent on a main tab), its name then, and the instance it showed. */
    static final String DEFINITION = "definition";
    static final String DEFINITION_NAME = "name";
    static final String CONTEXT = "context";
    /** The index of a session window's active tab. */
    static final String ACTIVE = "active";
    /** A session window's folded documents: {@code folded0}, {@code folded1}, … */
    static final String FOLDED = "folded";
    /** A session window's left-column panels folded to their header, comma-separated: {@code circuit,palette}. */
    static final String FOLDED_PANELS = "foldedPanels";

    /** A session window's right panel: whether it is open, its section's id and its width in pixels. */
    static final String INSPECTOR_OPEN = "inspectorOpen";
    static final String INSPECTOR_TAB = "inspectorTab";
    static final String INSPECTOR_WIDTH = "inspectorWidth";

    private SessionKeys() {
    }

    static void writeWindowState(Preferences node, WindowState state) {
        writeBounds(node, state);
        writeView(node, state.view());
    }

    static Optional<WindowState> readWindowState(Preferences node) {
        return readView(node).flatMap(view -> readBounds(node, view));
    }

    /** The place and size of a window, without a view (a session window's view is its active tab's). */
    static void writeBounds(Preferences node, WindowState state) {
        node.putInt(X, state.x());
        node.putInt(Y, state.y());
        node.putInt(WIDTH, state.width());
        node.putInt(HEIGHT, state.height());
        node.putBoolean(MAXIMIZED, state.maximized());
    }

    /** The bounds of {@link #writeBounds}, with {@code view}. */
    static Optional<WindowState> readBounds(Preferences node, ViewState view) {
        try {
            return Optional.of(new WindowState(Integer.parseInt(required(node, X)), Integer.parseInt(required(node, Y)),
                    Integer.parseInt(required(node, WIDTH)), Integer.parseInt(required(node, HEIGHT)),
                    parseBoolean(required(node, MAXIMIZED)), view));
        } catch (IllegalArgumentException exception) { // NumberFormatException included
            return Optional.empty();
        }
    }

    static void writeInspector(Preferences node, InspectorState state) {
        node.putBoolean(INSPECTOR_OPEN, state.open());
        node.put(INSPECTOR_TAB, state.selectedTab());
        node.putInt(INSPECTOR_WIDTH, state.width());
    }

    /**
     * The right panel of {@link #writeInspector}; empty when none of its keys is there (a session written before it).
     * A missing or broken key takes the initial panel's value, an unknown section {@code properties}, the width is
     * clamped ({@link InspectorState#fromSession}).
     */
    static Optional<InspectorState> readInspector(Preferences node) {
        String open = node.get(INSPECTOR_OPEN, null);
        String tab = node.get(INSPECTOR_TAB, null);
        String width = node.get(INSPECTOR_WIDTH, null);
        if (open == null && tab == null && width == null) {
            return Optional.empty();
        }
        InspectorState initial = InspectorState.initial();
        boolean readOpen = open == null ? initial.open() : switch (open) {
            case "true" -> true;
            case "false" -> false;
            default -> initial.open();
        };
        int readWidth = initial.width();
        try {
            readWidth = width == null ? readWidth : Integer.parseInt(width);
        } catch (NumberFormatException exception) {
            // the initial width stays
        }
        return Optional.of(InspectorState.fromSession(readOpen, tab == null ? initial.selectedTab() : tab, readWidth));
    }

    static void writeTab(Preferences node, TabState tab) {
        node.put(PATH, tab.path().toAbsolutePath().toString());
        tab.definitionId().ifPresent(definitionId -> node.put(DEFINITION, definitionId));
        tab.definitionName().ifPresent(name -> node.put(DEFINITION_NAME, name));
        tab.contextPath().ifPresent(context -> node.put(CONTEXT, context));
        writeView(node, tab.view());
    }

    /** A tab of {@link #writeTab}; empty without a path or a view. A component tab without a name is named by id. */
    static Optional<TabState> readTab(Preferences node) {
        Optional<Path> path = readPath(node);
        Optional<ViewState> view = readView(node);
        if (path.isEmpty() || view.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> definitionId = Optional.ofNullable(node.get(DEFINITION, null)).filter(id -> !id.isBlank());
        Optional<String> name = definitionId.map(id -> node.get(DEFINITION_NAME, id));
        Optional<String> context = definitionId.flatMap(id -> Optional.ofNullable(node.get(CONTEXT, null)))
                .filter(stored -> !stored.isBlank());
        return Optional.of(new TabState(path.get(), definitionId, name, context, view.get()));
    }

    private static void writeView(Preferences node, ViewState view) {
        node.putDouble(ZOOM, view.zoom());
        node.putDouble(CENTER_X, view.centerX());
        node.putDouble(CENTER_Y, view.centerY());
    }

    private static Optional<ViewState> readView(Preferences node) {
        try {
            return Optional.of(new ViewState(Double.parseDouble(required(node, ZOOM)),
                    Double.parseDouble(required(node, CENTER_X)), Double.parseDouble(required(node, CENTER_Y))));
        } catch (IllegalArgumentException exception) { // NumberFormatException included
            return Optional.empty();
        }
    }

    /** The stored {@link #PATH} of {@code node}; empty when it is missing, blank or not a path. */
    static Optional<Path> readPath(Preferences node) {
        return readPath(node, PATH);
    }

    /** The path stored under {@code key}; empty when it is missing, blank or not a path. */
    static Optional<Path> readPath(Preferences node, String key) {
        String stored = node.get(key, "");
        if (stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(stored));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    /**
     * Whether {@code path} fits in a preference value. Checked before a list is rewritten: a value that is too long
     * would make {@code put} throw after the old list was already removed.
     */
    static boolean fitsInPreferences(Path path) {
        return path.toString().length() <= Preferences.MAX_VALUE_LENGTH;
    }

    /** Removes every child of {@code node}, before a list is written again from the start. */
    static void removeChildren(Preferences node) throws BackingStoreException {
        for (String name : node.childrenNames()) {
            node.node(name).removeNode();
        }
    }

    /** The names that are plain non-negative numbers ({@code 0}, {@code 1}, …, not {@code 007}), in numeric order. */
    static List<String> numbered(String[] names) {
        return numbered(names, "");
    }

    /**
     * The names that are {@code prefix} followed by a plain non-negative number ({@code tab0}, {@code tab1}, …, not
     * {@code tab007}), in numeric order.
     */
    static List<String> numbered(String[] names, String prefix) {
        List<String> numbered = new ArrayList<>();
        for (String name : names) {
            if (number(name, prefix) >= 0) {
                numbered.add(name);
            }
        }
        numbered.sort(Comparator.comparingInt(name -> number(name, prefix)));
        return numbered;
    }

    /** The number after {@code prefix} in {@code name}; -1 when the name is not one of ours. */
    private static int number(String name, String prefix) {
        if (!name.startsWith(prefix)) {
            return -1;
        }
        String digits = name.substring(prefix.length());
        try {
            int index = Integer.parseInt(digits);
            return index >= 0 && digits.equals(Integer.toString(index)) ? index : -1;
        } catch (NumberFormatException exception) {
            return -1; // not one of ours: skipped
        }
    }

    private static String required(Preferences node, String key) {
        String value = node.get(key, null);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private static boolean parseBoolean(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + value);
        };
    }
}
