package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
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
     * Adds {@code item} at the end of the «Importar» submenu of {@code file}. A missing submenu is made first in the group it
     * shares with «Exportar» ({@link ImportExportGroup}), after the saving items.
     *
     * @return what takes the item back; it also removes the submenu and its separator once the submenu is empty
     */
    public static Runnable add(JMenu file, JMenuItem item) {
        JMenu menu = find(file).orElseGet(() -> create(file));
        menu.add(item);
        return () -> {
            menu.remove(item);
            if (menu.getMenuComponentCount() == 0) {
                ImportExportGroup.remove(file, menu);
            }
        };
    }

    private static JMenu create(JMenu file) {
        JMenu menu = new JMenu(Messages.text("menu.file.import"));
        menu.putClientProperty(MARK, Boolean.TRUE);
        ImportExportGroup.insert(file, menu, true);
        return menu;
    }
}
