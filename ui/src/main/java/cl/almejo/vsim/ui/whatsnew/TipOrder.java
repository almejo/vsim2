package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which tip of the day comes, over the tips file's order: VSim remembers the ids of the tips already shown (not an
 * index), so the next one is the first unseen in file order and a tip added in a newer version comes first. Once every
 * tip was seen the round starts again from the top. ‹ › walk the file order and wrap around. There is no counter: the
 * card never says how many tips there are (the user, 2026-11-10).
 */
final class TipOrder {

    private final List<String> ids;
    private final AppPreferences preferences;

    /** {@code ids}: the tips in file order, at least one. */
    TipOrder(List<String> ids, AppPreferences preferences) {
        this.ids = List.copyOf(ids);
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        if (this.ids.isEmpty()) {
            throw new IllegalArgumentException("no tips");
        }
    }

    /**
     * The index of the tip to show first: the first one not seen yet; when every tip was seen, the stored set is
     * forgotten and the round starts again at the first tip.
     */
    int chooseFirst() {
        Set<String> seen = preferences.seenTips();
        for (int index = 0; index < ids.size(); index++) {
            if (!seen.contains(ids.get(index))) {
                return index;
            }
        }
        preferences.setSeenTips(Set.of());
        return 0;
    }

    /** The tip at {@code index} was shown: it counts as seen. */
    void shown(int index) {
        Set<String> seen = new HashSet<>(preferences.seenTips());
        if (seen.add(ids.get(index))) {
            preferences.setSeenTips(seen);
        }
    }

    /** The tip after {@code index}, the first one after the last. */
    int after(int index) {
        return (index + 1) % ids.size();
    }

    /** The tip before {@code index}, the last one before the first. */
    int before(int index) {
        return (index + ids.size() - 1) % ids.size();
    }
}
