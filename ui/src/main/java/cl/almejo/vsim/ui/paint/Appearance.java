package cl.almejo.vsim.ui.paint;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** The circuit colors in use, shared by every window; listeners repaint when the scheme changes (spec §6.7). */
public final class Appearance {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> zoomListeners = new CopyOnWriteArrayList<>();
    private volatile ColorScheme scheme;
    private volatile int interfaceZoom = 100;

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

    /** The interface size installed now, in percent (100 until the theme manager installs another). */
    public int interfaceZoom() {
        return interfaceZoom;
    }

    /** Use it on the EDT, after the look and feel took the size; the zoom listeners run only when it changes. */
    public void setInterfaceZoom(int percent) {
        if (percent <= 0) {
            throw new IllegalArgumentException("percent must be positive: " + percent);
        }
        if (percent == interfaceZoom) {
            return;
        }
        interfaceZoom = percent;
        zoomListeners.forEach(Runnable::run);
    }

    public void addZoomListener(Runnable listener) {
        zoomListeners.add(listener);
    }

    public void removeZoomListener(Runnable listener) {
        zoomListeners.remove(listener);
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
