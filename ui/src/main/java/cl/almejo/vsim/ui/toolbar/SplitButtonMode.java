package cl.almejo.vsim.ui.toolbar;

import javax.swing.Icon;
import java.util.Objects;

/**
 * One mode of a {@link SplitButton}.
 *
 * @param id          stable id stored in the preferences (never shown)
 * @param icon        shown on the main button while the mode is current, and beside its item in the ▾ menu
 * @param text        the item's text in the ▾ menu
 * @param description the main button's tooltip while the mode is current
 */
public record SplitButtonMode(String id, Icon icon, String text, String description) {

    public SplitButtonMode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(description, "description");
    }
}
