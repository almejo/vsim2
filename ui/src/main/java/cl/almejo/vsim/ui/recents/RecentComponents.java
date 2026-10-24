package cl.almejo.vsim.ui.recents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The components placed most recently in this session, most recent first: one list for the whole application
 * (every window and circuit), kept in memory only, so it empties when VSim closes (spec 2026-10-21 §9.2). EDT.
 */
public final class RecentComponents {

    /** How many distinct components the list keeps (the user: «los 5 últimos»). */
    public static final int MAXIMUM = 5;

    private final List<String> typeIds = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** {@code typeId} landed in a circuit: it goes to the top, the oldest beyond {@link #MAXIMUM} drops out. */
    public void used(String typeId) {
        Objects.requireNonNull(typeId, "typeId");
        if (!typeIds.isEmpty() && typeIds.getFirst().equals(typeId)) {
            return;
        }
        typeIds.remove(typeId);
        typeIds.addFirst(typeId);
        if (typeIds.size() > MAXIMUM) {
            typeIds.removeLast();
        }
        listeners.forEach(Runnable::run);
    }

    /** The type ids, most recent first. */
    public List<String> typeIds() {
        return List.copyOf(typeIds);
    }

    /** Runs {@code listener} after every change of the list. Returns its removal. */
    public Runnable addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    /** For tests: how many listeners follow the list. */
    int listenerCount() {
        return listeners.size();
    }
}
