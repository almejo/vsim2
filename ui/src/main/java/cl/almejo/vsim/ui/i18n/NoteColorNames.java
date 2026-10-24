package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;

/** The visible names of a note's colours and of the parts of its style, shared by the inspector and the menu. */
public final class NoteColorNames {

    private NoteColorNames() {
    }

    /**
     * The visible name of {@code color} as {@code role}: {@code NONE} is «Automático» for the text (the scheme's colour)
     * and «Ninguno» for the background and the border.
     */
    public static String of(NoteStyle.Role role, NoteColor color) {
        return Messages.text(switch (color) {
            case NONE -> role == NoteStyle.Role.TEXT ? "noteStyle.automatic" : "noteStyle.none";
            case BLUE -> "noteStyle.blue";
            case GREEN -> "noteStyle.green";
            case YELLOW -> "noteStyle.yellow";
            case RED -> "noteStyle.red";
            case PURPLE -> "noteStyle.purple";
            case GRAY -> "noteStyle.gray";
            case BLACK -> "noteStyle.black";
            case WHITE -> "noteStyle.white";
        });
    }

    /** «Fondo», «Letra» or «Borde». */
    public static String of(NoteStyle.Role role) {
        return Messages.text(switch (role) {
            case BACKGROUND -> "noteStyle.background";
            case TEXT -> "noteStyle.text";
            case BORDER -> "noteStyle.border";
        });
    }
}
