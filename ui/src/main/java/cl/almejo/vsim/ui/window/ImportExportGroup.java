package cl.almejo.vsim.ui.window;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.Optional;

/**
 * The group of Archivo that holds «Importar ▸» and «Exportar ▸» (in that order) between a separator of its own and the
 * next one, whichever of the two is made first. The separator is shared: made with the first submenu, removed with the
 * last.
 */
final class ImportExportGroup {

    /** The client property of «Archivo» that holds the group's separator. */
    private static final String SEPARATOR = "vsim.file.importExportGroup.separator";

    private ImportExportGroup() {
    }

    /**
     * Puts {@code menu} in the group: «Importar» right after the separator, «Exportar» right after «Importar» (or after
     * the separator when there is none). The separator is made, before the first existing separator of {@code file}
     * (or at the end of a menu without separators), when the group is empty.
     */
    static void insert(JMenu file, JMenu menu, boolean isImport) {
        int separatorIndex = indexOfSeparator(file);
        if (separatorIndex < 0) {
            separatorIndex = firstSeparatorIndex(file);
            JPopupMenu.Separator separator = new JPopupMenu.Separator();
            file.getPopupMenu().insert(separator, separatorIndex);
            file.putClientProperty(SEPARATOR, separator);
        }
        int position = separatorIndex + 1;
        if (!isImport && position < file.getMenuComponentCount()
                && file.getMenuComponent(position) instanceof JMenu next
                && Boolean.TRUE.equals(next.getClientProperty(ImportMenu.MARK))) {
            position++;
        }
        file.insert(menu, position);
    }

    /** The submenu of {@code file} marked with the client property {@code mark}, if there is one. */
    static Optional<JMenu> find(JMenu file, String mark) {
        for (Component child : file.getMenuComponents()) {
            if (child instanceof JMenu menu && Boolean.TRUE.equals(menu.getClientProperty(mark))) {
                return Optional.of(menu);
            }
        }
        return Optional.empty();
    }

    /** What takes {@code item} back out of {@code menu}, and the submenu out of the group once it is empty. */
    static Runnable removal(JMenu file, JMenu menu, JMenuItem item) {
        return () -> {
            menu.remove(item);
            if (menu.getMenuComponentCount() == 0) {
                remove(file, menu);
            }
        };
    }

    /** Takes {@code menu} out and, when no submenu of the group is left, the group's separator too. */
    static void remove(JMenu file, JMenu menu) {
        file.remove(menu);
        if (ImportMenu.find(file).isEmpty() && ExportMenu.find(file).isEmpty()
                && file.getClientProperty(SEPARATOR) instanceof Component separator) {
            file.remove(separator);
            file.putClientProperty(SEPARATOR, null);
        }
    }

    private static int indexOfSeparator(JMenu file) {
        if (file.getClientProperty(SEPARATOR) instanceof Component separator) {
            for (int index = 0; index < file.getMenuComponentCount(); index++) {
                if (file.getMenuComponent(index) == separator) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int firstSeparatorIndex(JMenu file) {
        for (int index = 0; index < file.getMenuComponentCount(); index++) {
            if (file.getMenuComponent(index) instanceof JSeparator) {
                return index;
            }
        }
        return file.getMenuComponentCount();
    }
}
