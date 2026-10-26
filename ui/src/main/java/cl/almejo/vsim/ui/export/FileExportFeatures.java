package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JMenu;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What the export features of one editor view share: the action (off for an empty document), its «Archivo» menu item
 * at the index the subclass picks, the shortcut registration and the document change listener.
 * {@link #dispose()} takes all of it back. The subclass gives what the action does, {@link #export()}.
 */
public abstract class FileExportFeatures {

    private final EditorView view;
    private final Action action;
    private final Runnable itemRemover;
    private final Runnable changeListener = this::updateEnabled;
    private boolean disposed;

    /**
     * @param actionTextKey the bundle key of the action's text
     * @param iconName      the action's icon, see {@link ExportIcons}
     * @param shortcutId    the id the shortcut is registered under
     * @param insertionIndex where the menu item goes in «Archivo»; see {@link #indexAfter}
     */
    // The action runs export() only later, from the EDT, once the subclass is built.
    @SuppressWarnings("this-escape")
    protected FileExportFeatures(EditorView view, String actionTextKey, String iconName, String shortcutId,
                                 int insertionIndex) {
        this.view = view;
        this.action = Actions.of(Messages.text(actionTextKey), () -> export());
        Icons.install(action, iconName);
        this.itemRemover = Menus.install(view, MenuId.FILE, insertionIndex, action, shortcutId);
        view.openDocument().addChangeListener(changeListener);
        updateEnabled();
    }

    /** Right after the first of {@code anchors} (null ones skipped) that is in «Archivo», else at the end. */
    protected static int indexAfter(EditorView view, Action... anchors) {
        JMenu menu = view.menu(MenuId.FILE);
        for (Action anchor : anchors) {
            int index = anchor == null ? -1 : Menus.indexOf(menu, anchor);
            if (index >= 0) {
                return index + 1;
            }
        }
        return menu.getMenuComponentCount();
    }

    public final Action action() {
        return action;
    }

    protected final EditorView view() {
        return view;
    }

    private void updateEnabled() {
        action.setEnabled(!view.openDocument().document().isEmpty());
    }

    /** False once disposed or while the action is off (an empty document): the export must then do nothing. */
    protected final boolean available() {
        return !disposed && action.isEnabled();
    }

    /** The preferred folder, else the document's folder, else the user's home. */
    protected final Path startDirectory(Optional<Path> preferred) {
        return view.openDocument().startDirectory(preferred);
    }

    /** What the action does; starts with {@code if (!available()) return;}. */
    protected abstract void export();

    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        itemRemover.run();
        view.openDocument().removeChangeListener(changeListener);
    }
}
