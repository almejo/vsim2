package cl.almejo.vsim.ui.magnifier;

import java.util.List;

/** The icon of the Lupa toggle, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class MagnifierIcons {

    public static final String MAGNIFIER = "magnifier";

    private MagnifierIcons() {
    }

    /** Every icon of the Lupa, once. */
    public static List<String> names() {
        return List.of(MAGNIFIER);
    }
}
