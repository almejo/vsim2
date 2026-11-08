package cl.almejo.vsim.ui.fullscreen;

import java.util.List;

/** The icon of «Modo presentación», by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class FullScreenIcons {

    /** Four corners pointing out, as in the approved mockup. */
    public static final String FULL_SCREEN = "full-screen";

    private FullScreenIcons() {
    }

    /** Every icon of presentation mode, once. */
    public static List<String> names() {
        return List.of(FULL_SCREEN);
    }
}
