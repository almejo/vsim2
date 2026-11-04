package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ExportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the export features of one editor view share: the action (off for an empty document), its item in
 * Archivo ▸ «Exportar» after the anchor the subclass picks (or, for «Imprimir…», in «Archivo» itself), the shortcut
 * registration and the document change listener. {@link #dispose()} takes all of it back. The subclass gives what the
 * action does, {@link #export()}.
 */
public abstract class FileExportFeatures {

    private final EditorView view;
    private final Action action;
    private final Runnable itemRemover;
    private final Runnable changeListener = this::updateEnabled;
    private boolean disposed;

    /**
     * An item in Archivo ▸ «Exportar».
     *
     * @param actionTextKey the bundle key of the action's text (the shortcut editor lists the action by it)
     * @param itemTextKey   the bundle key of the item's short text in the «Exportar» submenu
     * @param iconName      the action's icon, see {@link ExportIcons}
     * @param shortcutId    the id the shortcut is registered under
     * @param anchors       the actions the item goes after in the submenu, the first one present wins (null ones
     *                      skipped); else it goes last
     */
    protected FileExportFeatures(EditorView view, String actionTextKey, String itemTextKey, String iconName,
                                 String shortcutId, Action... anchors) {
        this(view, actionTextKey, iconName, action -> {
            JMenuItem item = new JMenuItem(action);
            item.setText(Messages.text(itemTextKey));
            Runnable shortcutRemover = view.registerShortcutAction(shortcutId, action);
            Runnable menuRemover;
            try {
                menuRemover = ExportMenu.add(view.menu(MenuId.FILE), item, anchors);
            } catch (RuntimeException failure) {
                shortcutRemover.run();
                throw failure;
            }
            return () -> {
                shortcutRemover.run();
                menuRemover.run();
            };
        });
    }

    /**
     * An item in «Archivo» itself, with the action's text.
     *
     * @param actionTextKey  the bundle key of the action's text
     * @param iconName       the action's icon, see {@link ExportIcons}
     * @param shortcutId     the id the shortcut is registered under
     * @param insertionIndex where the item goes in «Archivo»; see {@link #indexAfter}
     */
    protected FileExportFeatures(EditorView view, String actionTextKey, String iconName, String shortcutId,
                                 int insertionIndex) {
        this(view, actionTextKey, iconName,
                action -> Menus.install(view, MenuId.FILE, insertionIndex, action, shortcutId));
    }

    // The action runs export() only later, from the EDT, once the subclass is built.
    @SuppressWarnings("this-escape")
    private FileExportFeatures(EditorView view, String actionTextKey, String iconName,
                               Function<Action, Runnable> installer) {
        this.view = view;
        this.action = Actions.of(Messages.text(actionTextKey), () -> export());
        Icons.install(action, iconName);
        this.itemRemover = installer.apply(action);
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
        action.setEnabled(!view.openDocument().document().isEmpty() && ready());
    }

    /**
     * Whether the export can run besides having something to export (a printer to print on); true here. Asked from
     * this class's constructor too, before the subclass's fields are set, so an override must answer true then.
     */
    protected boolean ready() {
        return true;
    }

    /** Asks again whether the action is on, after what {@link #ready()} reads changed. */
    protected final void refreshEnabled() {
        if (!disposed) {
            updateEnabled();
        }
    }

    /** Takes back what the subclass added; runs once, from {@link #dispose()}, after the item and listener went. */
    protected void disposeExtras() {
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
        disposeExtras();
    }
}
