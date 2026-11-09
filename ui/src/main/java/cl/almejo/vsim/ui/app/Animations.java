package cl.almejo.vsim.ui.app;

import javax.swing.UIManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place that answers «may VSim animate?»: Preferencias ▸ «Animaciones» (a VSim-only setting, «Sí» by default;
 * nothing reads the desktop's own switch). Everything that moves or fades asks here, at the start of its effect and at
 * every frame, so the setting applies live; with «No» the thing appears or disappears at once, and what carries
 * information stays on screen, still.
 */
public final class Animations {

    /** FlatLaf's switch for the eased mouse-wheel scrolling of every scroll pane (on in its defaults). */
    static final String SMOOTH_SCROLLING = "ScrollPane.smoothScrolling";

    private Animations() {
    }

    /** Whether VSim animates now: Preferencias ▸ «Animaciones». */
    public static boolean enabled(AppPreferences preferences) {
        return preferences.animations();
    }

    /**
     * Whether the view travels (an eased pan, a «fly to») instead of jumping: only with «Animaciones» and «Animar los
     * movimientos de la vista» both on.
     */
    public static boolean viewMoves(AppPreferences preferences) {
        return enabled(preferences) && preferences.animateViewMoves();
    }

    /**
     * Makes the look and feel's eased mouse-wheel scrolling (lists, trees, panels) follow «Animaciones»: on with «Sí»,
     * off with «No», live (FlatLaf reads it at every wheel event). Returns what stops following it.
     */
    public static Runnable followInScrolling(AppPreferences preferences) {
        Runnable apply = () -> UIManager.put(SMOOTH_SCROLLING, enabled(preferences));
        apply.run();
        return onChange(preferences, apply);
    }

    /**
     * Runs {@code changed} each time «Animaciones» changes (only then: other preferences do not call it). Returns what
     * stops following it.
     */
    public static Runnable onChange(AppPreferences preferences, Runnable changed) {
        AtomicBoolean shown = new AtomicBoolean(enabled(preferences));
        Runnable listener = () -> {
            boolean now = enabled(preferences);
            if (shown.getAndSet(now) != now) {
                changed.run();
            }
        };
        preferences.addListener(listener);
        return () -> preferences.removeListener(listener);
    }
}
