package cl.almejo.vsim.ui.ai.present;

import java.util.List;

/** The icon of the «Presentar» toggle (IA.2), by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class PresentationIcons {

    public static final String PRESENT = "ai-present";

    private PresentationIcons() {
    }

    /** Every icon of «Presentar», once. */
    public static List<String> names() {
        return List.of(PRESENT);
    }
}
