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
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Archivo ▸ Preferencias…, placed in its own group just before "Cerrar ventana". The command palette also lists one
 * entry per group of the dialog ({@link Actions#PALETTE_ENTRIES}, in the order of {@link PreferencesDialog#GROUP_IDS}),
 * which opens it on that group; the menu item opens it on the last group seen.
 */
public final class PreferencesFeature implements EditorExtension {

    /** Opens the dialog on a group (an id of {@link PreferencesDialog#GROUP_IDS}); empty means the last one seen. */
    @FunctionalInterface
    interface Opener {
        void open(Component parent, AppPreferences preferences, Optional<String> groupId);
    }

    private final Opener opener;

    public PreferencesFeature() {
        this(PreferencesDialog::show);
    }

    /** With the application's AI server, so «IA» shows a port given on the command line for this session. */
    public PreferencesFeature(AiServerService aiServer) {
        this((parent, preferences, groupId) -> PreferencesDialog.show(parent, preferences, aiServer, groupId));
    }

    PreferencesFeature(Opener opener) {
        this.opener = opener;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenu file = view.menu(MenuId.FILE);
        Action preferences = Actions.of(Messages.text("action.preferences"),
                () -> opener.open(view, view.services().preferences(), Optional.empty()));
        preferences.putValue(Actions.PALETTE_ENTRIES, groupEntries(view));
        JSeparator separator = new JPopupMenu.Separator();
        int position = insertionIndex(file, view.actions().closeTab());
        file.add(separator, position);
        Runnable itemRemover = Menus.install(view, MenuId.FILE, position + 1, preferences, "file.preferences");
        return () -> {
            itemRemover.run();
            file.remove(separator);
        };
    }

    /** One entry per group for the command palette («Preferencias ▸ Atajos de teclado»), opening the dialog there. */
    private List<Action> groupEntries(EditorView view) {
        List<Action> entries = new ArrayList<>();
        for (String groupId : PreferencesDialog.GROUP_IDS) {
            Action entry = Actions.of(Messages.format("commandPalette.preferencesTab",
                            Messages.text(PreferencesDialog.titleKey(groupId))),
                    () -> opener.open(view, view.services().preferences(), Optional.of(groupId)));
            entry.putValue(Actions.PALETTE_ENTRY_ID, groupId);
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    /** Before the separator that precedes {@code closeTab}, or at the end when the menu has no such item. */
    static int insertionIndex(JMenu menu, Action closeTab) {
        int index = Menus.indexOf(menu, closeTab);
        if (index < 0) {
            return menu.getMenuComponentCount();
        }
        return index > 0 && menu.getMenuComponent(index - 1) instanceof JSeparator ? index - 1 : index;
    }
}
