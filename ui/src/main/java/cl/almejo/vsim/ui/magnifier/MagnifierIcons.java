package cl.almejo.vsim.ui.magnifier;

import java.util.List;

/** The icon of the «Mirar adentro» toggle, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class MagnifierIcons {

    public static final String EYE = "look-inside";

    private MagnifierIcons() {
    }

    /** Every icon of «Mirar adentro», once. */
    public static List<String> names() {
        return List.of(EYE);
    }
}
