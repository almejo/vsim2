package cl.almejo.vsim.ui.app;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The windows in «Modo presentación» now: while any is, the interface takes the presentation size (Preferencias,
 * Aspecto, «En modo presentación»). {@code presenting} hears whether any window presents each time that may change.
 * A closed window is forgotten even when it never said it left the mode. EDT only.
 *
 * @param <W> the window type
 */
final class PresentingWindows<W> {

    private final Set<W> windows = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<Boolean> presenting;

    PresentingWindows(Consumer<Boolean> presenting) {
        this.presenting = Objects.requireNonNull(presenting, "presenting");
    }

    /** {@code window} entered ({@code active}) or left the mode. */
    void changed(W window, boolean active) {
        if (active) {
            windows.add(window);
        } else {
            windows.remove(window);
        }
        presenting.accept(!windows.isEmpty());
    }

    /** {@code window} closed: it presents no more. */
    void closed(W window) {
        if (windows.remove(window)) {
            presenting.accept(!windows.isEmpty());
        }
    }
}
