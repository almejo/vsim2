package cl.almejo.vsim.ui.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The listeners of one thing, in the order they were added. A listener added or removed while the others are being
 * called takes effect from the next {@link #fire}; the thread that fires is the one that runs them.
 */
public final class Listeners<T> {

    private final List<T> listeners = new CopyOnWriteArrayList<>();

    /** Adds {@code listener} and returns the runnable that takes it back. */
    public Runnable add(T listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void remove(T listener) {
        listeners.remove(listener);
    }

    /** Calls {@code action} with every listener; a failure of one stops the rest. */
    public void fire(Consumer<T> action) {
        listeners.forEach(action);
    }

    /**
     * Calls {@code action} with every listener even when an earlier one fails; the failures come back, in order,
     * once all have run.
     */
    public List<RuntimeException> fireAll(Consumer<T> action) {
        List<RuntimeException> failures = new ArrayList<>();
        for (T listener : listeners) {
            try {
                action.accept(listener);
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        return failures;
    }

    public int size() {
        return listeners.size();
    }
}
