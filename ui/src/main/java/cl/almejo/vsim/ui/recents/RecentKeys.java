package cl.almejo.vsim.ui.recents;

import cl.almejo.vsim.ui.app.Listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A short list of the things used most recently, most recent first, by key (a component's type id, a palette
 * action's key): the recent components and the recent actions of the command palette. Kept in memory only. EDT.
 */
public final class RecentKeys {

    private final int maximum;
    private final List<String> keys = new ArrayList<>();
    private final Listeners<Runnable> listeners = new Listeners<>();

    /** A list that keeps at most {@code maximum} distinct keys. */
    public RecentKeys(int maximum) {
        this.maximum = maximum;
    }

    /** {@code key} was used: it goes to the top, the oldest beyond the maximum drops out. */
    public void used(String key) {
        Objects.requireNonNull(key, "key");
        if (!keys.isEmpty() && keys.getFirst().equals(key)) {
            return;
        }
        keys.remove(key);
        keys.addFirst(key);
        if (keys.size() > maximum) {
            keys.removeLast();
        }
        listeners.fire(Runnable::run);
    }

    /** The keys, most recent first. */
    public List<String> keys() {
        return List.copyOf(keys);
    }

    /** Runs {@code listener} after every change of the list. Returns its removal. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** For tests: how many listeners follow the list. */
    public int listenerCount() {
        return listeners.size();
    }
}
