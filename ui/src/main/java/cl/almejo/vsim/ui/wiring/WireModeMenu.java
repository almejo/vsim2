package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.tools.WireMode;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.Map;

/**
 * Editar ▸ «Dibujar cables» for one editor view: two radio items, «Continuo» and «Por trozos», that choose how
 * Selección draws a wire from a pin or a wire ({@link AppPreferences#wireMode()}). The mode is one for the whole app:
 * the items follow a change made anywhere (another tab or window). A path in progress keeps going; the mode applies to
 * the next one. {@link #dispose()} takes the listener back.
 */
final class WireModeMenu {

    private final AppPreferences preferences;
    private final Map<WireMode, Action> actions;
    private final JMenu menu = new JMenu(Messages.text("menu.wireMode"));
    private final Runnable preferencesListener = this::refresh;

    WireModeMenu(AppPreferences preferences) {
        this.preferences = preferences;
        this.actions = Map.of(
                WireMode.CONTINUOUS, modeAction(WireMode.CONTINUOUS, "action.wireModeContinuous",
                        WireModeIcons.CONTINUOUS),
                WireMode.PIECES, modeAction(WireMode.PIECES, "action.wireModePieces", WireModeIcons.PIECES));
        ButtonGroup group = new ButtonGroup();
        for (WireMode mode : WireMode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(actions.get(mode));
            group.add(item);
            menu.add(item);
        }
        refresh();
        preferences.addListener(preferencesListener);
    }

    private Action modeAction(WireMode mode, String nameKey, String iconName) {
        Action action = Actions.of(Messages.text(nameKey), null, () -> preferences.setWireMode(mode));
        action.putValue(Action.SMALL_ICON, Icons.get(iconName, Icons.MENU_SIZE));
        return action;
    }

    /** The «Dibujar cables» submenu. */
    JMenu menu() {
        return menu;
    }

    /** The action of the item for {@code mode} (offered to the shortcut editor; no key by default). */
    Action action(WireMode mode) {
        return actions.get(mode);
    }

    /** Marks the item of the mode the preferences hold now. */
    private void refresh() {
        WireMode chosen = preferences.wireMode();
        actions.forEach((mode, action) -> action.putValue(Action.SELECTED_KEY, mode == chosen));
    }

    void dispose() {
        preferences.removeListener(preferencesListener);
    }
}
