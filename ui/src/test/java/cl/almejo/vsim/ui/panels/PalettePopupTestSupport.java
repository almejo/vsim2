package cl.almejo.vsim.ui.panels;

import javax.swing.JPopupMenu;
import java.util.Optional;

/** Lets tests outside this package build the palette's right-click menu of an entry, contributors included. */
public final class PalettePopupTestSupport {

    private PalettePopupTestSupport() {
    }

    /** The menu a right-click on the visible entry with {@code typeId} opens (empty: no menu there). */
    public static Optional<JPopupMenu> popupFor(PalettePanel palette, String typeId) {
        return palette.popupFor(palette.pathOf(typeId));
    }
}
