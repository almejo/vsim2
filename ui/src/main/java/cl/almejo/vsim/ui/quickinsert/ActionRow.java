package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Action;
import java.util.Objects;
import java.util.Optional;

/**
 * One entry of the command palette's «Acciones» tab: a menu item (or a toolbar button no menu has) as the user sees
 * it now.
 *
 * @param name           the menu text, as shown («Imagen de la ventana…»)
 * @param menuPath       the menus that lead to it («Archivo ▸ Exportar»)
 * @param shortcut       the key that runs it now, already described («Ctrl+Mayús+S»), if any
 * @param enabled        whether it can run now; a disabled row is shown grey and never runs
 * @param disabledReason why it cannot run, only for a disabled row
 * @param selected       the state of a check or radio item; empty for a plain one
 * @param action         what choosing the row does (a click on the item, so a check item flips)
 */
record ActionRow(String name, String menuPath, Optional<String> shortcut, boolean enabled,
                 Optional<String> disabledReason, Optional<Boolean> selected, Action action) {

    ActionRow {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(menuPath, "menuPath");
        Objects.requireNonNull(shortcut, "shortcut");
        Objects.requireNonNull(disabledReason, "disabledReason");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(action, "action");
    }

    /**
     * The whole path with the name («Archivo ▸ Exportar ▸ Imagen de la ventana…»): what {@link RecentActions}
     * remembers, the same in every window.
     */
    String key() {
        return Messages.format("commandPalette.path", menuPath, name);
    }
}
