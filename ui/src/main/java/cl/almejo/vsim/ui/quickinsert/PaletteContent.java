package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanel;

import javax.swing.KeyStroke;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one opening of the command palette shows: the components of the view's palette (read live, they may change
 * while it is open) and the view's «Acciones» as they were when it opened, with the keys that open each tab now.
 *
 * @param actions          every action of the view ({@link ActionRows})
 * @param recentActions    the ones used lately, most recent first, shown when nothing is typed
 * @param componentsKey    the key of «Insertar componente…» ({@code edit.quickInsert}), if it has one
 * @param findActionKey    the key of «Buscar una acción…» ({@code help.findAction}), if it has one
 */
record PaletteContent(PalettePanel palette, PainterRegistry painters, Appearance appearance,
                      List<ActionRow> actions, List<ActionRow> recentActions, Optional<KeyStroke> componentsKey,
                      Optional<KeyStroke> findActionKey) {

    PaletteContent {
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(painters, "painters");
        Objects.requireNonNull(appearance, "appearance");
        actions = List.copyOf(actions);
        recentActions = List.copyOf(recentActions);
        Objects.requireNonNull(componentsKey, "componentsKey");
        Objects.requireNonNull(findActionKey, "findActionKey");
    }
}
