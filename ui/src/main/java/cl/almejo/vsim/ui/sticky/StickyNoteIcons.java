package cl.almejo.vsim.ui.sticky;

import java.util.List;

/** The icons of the sticky notes, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class StickyNoteIcons {

    public static final String TOOL = "sticky-note-tool";

    private StickyNoteIcons() {
    }

    /** Every sticky note icon, once. */
    public static List<String> names() {
        return List.of(TOOL);
    }
}
