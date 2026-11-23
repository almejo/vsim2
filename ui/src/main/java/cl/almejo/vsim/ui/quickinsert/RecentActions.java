package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.recents.RecentKeys;

import java.util.List;
import java.util.Optional;

/**
 * The actions run most recently from the command palette, most recent first: one list for the whole application
 * (every window), kept in memory only, like {@code RecentComponents}. An action is remembered by its
 * {@link ActionRow#key()}, so the same menu item of another window counts as the same action. EDT.
 */
final class RecentActions {

    /** How many actions the list keeps, as many as the recent components. */
    static final int MAXIMUM = 5;

    private final RecentKeys keys = new RecentKeys(MAXIMUM);

    /** {@code key} was run: it goes to the top, the oldest beyond {@link #MAXIMUM} drops out. */
    void used(String key) {
        keys.used(key);
    }

    /** The keys, most recent first. */
    List<String> keys() {
        return keys.keys();
    }

    /** The rows of {@code rows} (one view's) that were used recently, most recent first; gone ones are skipped. */
    List<ActionRow> rowsIn(List<ActionRow> rows) {
        return keys.keys().stream().map(key -> rows.stream().filter(row -> row.key().equals(key)).findFirst())
                .flatMap(Optional::stream).toList();
    }

    /** Runs {@code listener} after every change of the list. Returns its removal. */
    Runnable addListener(Runnable listener) {
        return keys.addListener(listener);
    }
}
