package cl.almejo.vsim.ui.app;

/**
 * The one place that answers «may the UI animate?» for the small UI animations (bars sliding in, notices fading, the
 * capture flash). Animations off means the thing appears or disappears at once.
 */
public final class Animations {

    private Animations() {
    }

    /**
     * Always {@code true} for now. The pending global «Animaciones» setting will rewire this body (reading
     * {@code preferences}); callers already go through here so none has to change.
     */
    public static boolean enabled(AppPreferences preferences) {
        return true;
    }
}
