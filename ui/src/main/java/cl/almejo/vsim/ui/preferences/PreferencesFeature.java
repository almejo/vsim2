package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.function.BiConsumer;

/** Archivo ▸ Preferencias…, placed in its own group just before "Cerrar ventana". */
public final class PreferencesFeature implements EditorExtension {

    private final BiConsumer<Component, AppPreferences> opener;

    public PreferencesFeature() {
        this(PreferencesDialog::show);
    }

    /** With the application's AI server, so «IA» shows a port given on the command line for this session. */
    public PreferencesFeature(AiServerService aiServer) {
        this((parent, preferences) -> PreferencesDialog.show(parent, preferences, aiServer));
    }

    PreferencesFeature(BiConsumer<Component, AppPreferences> opener) {
        this.opener = opener;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenu file = view.menu(MenuId.FILE);
        Action preferences = Actions.of(Messages.text("action.preferences"),
                () -> opener.accept(view, view.services().preferences()));
        JMenuItem item = new JMenuItem(preferences);
        Runnable unregisterShortcut = view.registerShortcutAction("file.preferences", preferences);
        JSeparator separator = new JPopupMenu.Separator();
        int position = insertionIndex(file, view.actions().closeWindow());
        file.add(separator, position);
        file.add(item, position + 1);
        return () -> {
            unregisterShortcut.run();
            file.remove(item);
            file.remove(separator);
        };
    }

    /** Before the separator that precedes {@code closeWindow}, or at the end when the menu has no such item. */
    static int insertionIndex(JMenu menu, Action closeWindow) {
        int index = Menus.indexOf(menu, closeWindow);
        if (index < 0) {
            return menu.getMenuComponentCount();
        }
        return index > 0 && menu.getMenuComponent(index - 1) instanceof JSeparator ? index - 1 : index;
    }
}
