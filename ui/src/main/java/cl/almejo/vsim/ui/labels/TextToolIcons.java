package cl.almejo.vsim.ui.labels;

import java.util.List;

/** The icons of the «Texto» tool, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class TextToolIcons {

    public static final String TOOL = "text-tool";

    private TextToolIcons() {
    }

    /** Every «Texto» tool icon, once. */
    public static List<String> names() {
        return List.of(TOOL);
    }
}
