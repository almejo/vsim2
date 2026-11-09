package cl.almejo.vsim.ui.quickinsert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The actions run most recently from the command palette, most recent first: one list for the whole application
 * (every window), kept in memory only, like {@code RecentComponents}. An action is remembered by its
 * {@link ActionRow#key()}, so the same menu item of another window counts as the same action. EDT.
 */
final class RecentActions {

    /** How many actions the list keeps, as many as the recent components. */
    static final int MAXIMUM = 5;

    private final List<String> keys = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** {@code key} was run: it goes to the top, the oldest beyond {@link #MAXIMUM} drops out. */
    void used(String key) {
        Objects.requireNonNull(key, "key");
        if (!keys.isEmpty() && keys.getFirst().equals(key)) {
            return;
        }
        keys.remove(key);
        keys.addFirst(key);
        if (keys.size() > MAXIMUM) {
            keys.removeLast();
        }
        listeners.forEach(Runnable::run);
    }

    /** The keys, most recent first. */
    List<String> keys() {
        return List.copyOf(keys);
    }

    /** The rows of {@code rows} (one view's) that were used recently, most recent first; gone ones are skipped. */
    List<ActionRow> rowsIn(List<ActionRow> rows) {
        return keys.stream().map(key -> rows.stream().filter(row -> row.key().equals(key)).findFirst())
                .flatMap(Optional::stream).toList();
    }

    /** Runs {@code listener} after every change of the list. Returns its removal. */
    Runnable addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }
}
