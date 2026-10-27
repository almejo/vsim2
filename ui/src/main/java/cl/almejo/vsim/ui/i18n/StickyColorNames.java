package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.document.StickyColor;

/** The visible names of the sticky note paper colours, shared by the note's menu and the Propiedades panel. */
public final class StickyColorNames {

    private StickyColorNames() {
    }

    /** The visible name of {@code color}. */
    public static String of(StickyColor color) {
        return Messages.text(switch (color) {
            case YELLOW -> "stickyNotes.color.yellow";
            case PINK -> "stickyNotes.color.pink";
            case BLUE -> "stickyNotes.color.blue";
            case GREEN -> "stickyNotes.color.green";
            case ORANGE -> "stickyNotes.color.orange";
        });
    }
}
