package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.Optional;

/**
 * Archivo ▸ «Importar ▸», shared by the extensions that import something («Componente…», «Tabla de verdad desde
 * Excel/CSV…»). The submenu is found by a client property, never by its translated text. The first extension that adds
 * an item makes it; the last one to take its item back removes it, whatever the order.
 */
public final class ImportMenu {

    /** The client property that marks the submenu. */
    static final String MARK = "vsim.file.importMenu";
    /** The client property of the submenu that holds the separator inserted with it. */
    private static final String SEPARATOR = "vsim.file.importMenu.separator";

    private ImportMenu() {
    }

    /** The «Importar» submenu of {@code file}, when an extension made it. */
    public static Optional<JMenu> find(JMenu file) {
        for (Component child : file.getMenuComponents()) {
            if (child instanceof JMenu menu && Boolean.TRUE.equals(menu.getClientProperty(MARK))) {
                return Optional.of(menu);
            }
        }
        return Optional.empty();
    }

    /**
     * Adds {@code item} at the end of the «Importar» submenu of {@code file}. A missing submenu is made with a separator
     * before it, right before the first separator of the menu (after the saving and exporting items), or at the end
     * of a menu without separators.
     *
     * @return what takes the item back; it also removes the submenu and its separator once the submenu is empty
     */
    public static Runnable add(JMenu file, JMenuItem item) {
        JMenu menu = find(file).orElseGet(() -> create(file));
        menu.add(item);
        return () -> {
            menu.remove(item);
            if (menu.getMenuComponentCount() == 0) {
                file.remove(menu);
                if (menu.getClientProperty(SEPARATOR) instanceof Component separator) {
                    file.remove(separator);
                }
            }
        };
    }

    private static JMenu create(JMenu file) {
        JMenu menu = new JMenu(Messages.text("menu.file.import"));
        menu.putClientProperty(MARK, Boolean.TRUE);
        int position = file.getMenuComponentCount();
        for (int index = 0; index < file.getMenuComponentCount(); index++) {
            if (file.getMenuComponent(index) instanceof JSeparator) {
                position = index;
                break;
            }
        }
        JPopupMenu.Separator separator = new JPopupMenu.Separator();
        file.getPopupMenu().insert(separator, position);
        file.insert(menu, position + 1);
        menu.putClientProperty(SEPARATOR, separator);
        return menu;
    }
}
