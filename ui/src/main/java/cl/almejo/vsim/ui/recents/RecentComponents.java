package cl.almejo.vsim.ui.recents;

import java.util.List;
import java.util.Objects;

/**
 * The components placed most recently in this session, most recent first: one list for the whole application
 * (every window and circuit), kept in memory only, so it empties when VSim closes (spec 2026-10-21 §9.2). EDT.
 */
public final class RecentComponents {

    /** How many distinct components the list keeps (the user: «los 5 últimos»). */
    public static final int MAXIMUM = 5;

    private final RecentKeys typeIds = new RecentKeys(MAXIMUM);

    /** {@code typeId} landed in a circuit: it goes to the top, the oldest beyond {@link #MAXIMUM} drops out. */
    public void used(String typeId) {
        typeIds.used(Objects.requireNonNull(typeId, "typeId"));
    }

    /** The type ids, most recent first. */
    public List<String> typeIds() {
        return typeIds.keys();
    }

    /** Runs {@code listener} after every change of the list. Returns its removal. */
    public Runnable addListener(Runnable listener) {
        return typeIds.addListener(listener);
    }

    /** For tests: how many listeners follow the list. */
    int listenerCount() {
        return typeIds.listenerCount();
    }
}
