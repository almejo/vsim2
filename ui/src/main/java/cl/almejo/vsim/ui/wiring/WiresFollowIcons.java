package cl.almejo.vsim.ui.wiring;

import java.util.List;

/** The icon of the follow-mode toggle (M6.7), by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class WiresFollowIcons {

    public static final String WIRES_FOLLOW = "wires-follow";

    private WiresFollowIcons() {
    }

    /** Every icon of the follow mode, once. */
    public static List<String> names() {
        return List.of(WIRES_FOLLOW);
    }
}
