package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.function.BiConsumer;

/** Archivo ▸ Preferencias…, placed in its own group just before "Cerrar ventana". */
public final class PreferencesFeature implements EditorExtension {

    private final BiConsumer<Component, AppPreferences> opener;

    public PreferencesFeature() {
        this(PreferencesDialog::show);
    }

    PreferencesFeature(BiConsumer<Component, AppPreferences> opener) {
        this.opener = opener;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenu file = view.menu(MenuId.FILE);
        Action preferences = new AbstractAction(Messages.text("action.preferences")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                opener.accept(view, view.services().preferences());
            }
        };
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
        for (int index = 0; index < menu.getMenuComponentCount(); index++) {
            if (menu.getMenuComponent(index) instanceof JMenuItem item && item.getAction() == closeWindow) {
                return index > 0 && menu.getMenuComponent(index - 1) instanceof JSeparator ? index - 1 : index;
            }
        }
        return menu.getMenuComponentCount();
    }
}
