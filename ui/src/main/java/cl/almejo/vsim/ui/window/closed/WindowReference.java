package cl.almejo.vsim.ui.window.closed;

import java.lang.ref.WeakReference;
import java.util.Optional;

/** The window a tab closed in, held weakly: a closed window is never kept alive by the stack of closed tabs. */
public final class WindowReference {

    private static final WindowReference NONE = new WindowReference(null);

    private final WeakReference<Object> window;

    private WindowReference(Object window) {
        this.window = new WeakReference<>(window);
    }

    public static WindowReference of(Object window) {
        return new WindowReference(window);
    }

    /** No window (it is not known or does not matter). */
    public static WindowReference none() {
        return NONE;
    }

    /** The window while something else still holds it; the caller checks whether it is still open. */
    public Optional<Object> window() {
        return Optional.ofNullable(window.get());
    }
}
