package cl.almejo.vsim.ui.wiring;

import java.util.List;

/** The icon of «Ordenar cables» (M6.2), by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class TidyIcons {

    public static final String TIDY_WIRES = "tidy-wires";

    private TidyIcons() {
    }

    /** Every tidy icon, once. */
    public static List<String> names() {
        return List.of(TIDY_WIRES);
    }
}
