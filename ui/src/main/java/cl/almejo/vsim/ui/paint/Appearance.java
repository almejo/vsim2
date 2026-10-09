package cl.almejo.vsim.ui.paint;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** The circuit colors in use, shared by every window; listeners repaint when the scheme changes (spec §6.7). */
public final class Appearance {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile ColorScheme scheme;

    public Appearance(ColorScheme scheme) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
    }

    public ColorScheme scheme() {
        return scheme;
    }

    /** Use it on the EDT; listeners run only when the scheme really changes. */
    public void setScheme(ColorScheme next) {
        Objects.requireNonNull(next, "scheme");
        if (next.equals(scheme)) {
            return;
        }
        scheme = next;
        listeners.forEach(Runnable::run);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** For tests: views must remove what they add when disposed. */
    public int listenerCount() {
        return listeners.size();
    }
}
