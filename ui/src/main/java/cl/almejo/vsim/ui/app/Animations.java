package cl.almejo.vsim.ui.app;

/**
 * The one place that answers «may VSim animate?»: Preferencias ▸ «Animaciones» (a VSim-only setting, «Sí» by default;
 * nothing reads the desktop's own switch). Everything that moves or fades asks here, at the start of its effect and at
 * every frame, so the setting applies live; with «No» the thing appears or disappears at once, and what carries
 * information stays on screen, still.
 */
public final class Animations {

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
}
