package cl.almejo.vsim.ui.inline;

import java.util.List;

/** The icons of the inline text editor (M6.5), by file name under {@code icons/}. */
public final class InlineIcons {

    public static final String CONFIRM = "inline-confirm";
    public static final String CANCEL = "inline-cancel";

    private InlineIcons() {
    }

    /** Every inline editor icon, once. */
    public static List<String> names() {
        return List.of(CONFIRM, CANCEL);
    }
}
