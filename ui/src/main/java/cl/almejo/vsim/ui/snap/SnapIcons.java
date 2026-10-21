package cl.almejo.vsim.ui.snap;

import java.util.List;

/** The icon of the «Ajustar» toggle, a magnet, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class SnapIcons {

    public static final String SNAP = "snap";

    private SnapIcons() {
    }

    /** Every icon of «Ajustar», once. */
    public static List<String> names() {
        return List.of(SNAP);
    }
}
