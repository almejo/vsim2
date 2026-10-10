package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Plain preference keys of a {@link WindowState} (spec §4.2, contract C4: no JSON), the stored path of an entry and the
 * numbered child nodes that hold lists. A missing or bad value means "no state", never an exception.
 */
final class SessionKeys {

    /** The file of a Recientes entry or a session document. */
    static final String PATH = "path";
    static final String X = "x";
    static final String Y = "y";
    static final String WIDTH = "width";
    static final String HEIGHT = "height";
    static final String MAXIMIZED = "maximized";
    static final String ZOOM = "zoom";
    static final String CENTER_X = "centerX";
    static final String CENTER_Y = "centerY";

    private SessionKeys() {
    }

    static void writeWindowState(Preferences node, WindowState state) {
        node.putInt(X, state.x());
        node.putInt(Y, state.y());
        node.putInt(WIDTH, state.width());
        node.putInt(HEIGHT, state.height());
        node.putBoolean(MAXIMIZED, state.maximized());
        node.putDouble(ZOOM, state.view().zoom());
        node.putDouble(CENTER_X, state.view().centerX());
        node.putDouble(CENTER_Y, state.view().centerY());
    }

    static Optional<WindowState> readWindowState(Preferences node) {
        try {
            ViewState view = new ViewState(Double.parseDouble(required(node, ZOOM)),
                    Double.parseDouble(required(node, CENTER_X)), Double.parseDouble(required(node, CENTER_Y)));
            return Optional.of(new WindowState(Integer.parseInt(required(node, X)), Integer.parseInt(required(node, Y)),
                    Integer.parseInt(required(node, WIDTH)), Integer.parseInt(required(node, HEIGHT)),
                    parseBoolean(required(node, MAXIMIZED)), view));
        } catch (IllegalArgumentException exception) { // NumberFormatException included
            return Optional.empty();
        }
    }

    /** The stored {@link #PATH} of {@code node}; empty when it is missing, blank or not a path. */
    static Optional<Path> readPath(Preferences node) {
        String stored = node.get(PATH, "");
        if (stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(stored));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    /** Removes every child of {@code node}, before a list is written again from the start. */
    static void removeChildren(Preferences node) throws BackingStoreException {
        for (String name : node.childrenNames()) {
            node.node(name).removeNode();
        }
    }

    /** The names that are plain non-negative numbers ({@code 0}, {@code 1}, …, not {@code 007}), in numeric order. */
    static List<String> numbered(String[] names) {
        List<String> numbered = new ArrayList<>();
        for (String name : names) {
            try {
                int index = Integer.parseInt(name);
                if (index >= 0 && name.equals(Integer.toString(index))) {
                    numbered.add(name);
                }
            } catch (NumberFormatException exception) {
                // not one of ours: skipped
            }
        }
        numbered.sort(Comparator.comparingInt(Integer::parseInt));
        return numbered;
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
