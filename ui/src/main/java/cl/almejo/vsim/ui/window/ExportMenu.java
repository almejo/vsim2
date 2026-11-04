package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.Optional;

/**
 * Archivo ▸ «Exportar ▸», shared by the extensions that export the circuit («Imagen…», «PDF…», «Circuito de Logisim
 * (.circ) (beta)…»). Like {@link ImportMenu}: found by a client property, never by its translated text; the first
 * extension that adds an item makes it and the last one to take its item back removes it, whatever the order. It
 * sits right after «Importar» in the group of its own ({@link ImportExportGroup}), after the saving items.
 */
public final class ExportMenu {

    /** The client property that marks the submenu. */
    static final String MARK = "vsim.file.exportMenu";

    private ExportMenu() {
    }

    /** The «Exportar» submenu of {@code file}, when an extension made it. */
    public static Optional<JMenu> find(JMenu file) {
        for (Component child : file.getMenuComponents()) {
            if (child instanceof JMenu menu && Boolean.TRUE.equals(menu.getClientProperty(MARK))) {
                return Optional.of(menu);
            }
        }
        return Optional.empty();
    }

    /**
     * Adds {@code item} to the «Exportar» submenu of {@code file}, right after the first of {@code anchors} (null ones
     * skipped) that is in it, else at the end.
     *
     * @return what takes the item back; it also removes the submenu once it is empty
     */
    public static Runnable add(JMenu file, JMenuItem item, Action... anchors) {
        JMenu menu = find(file).orElseGet(() -> create(file));
        int index = menu.getMenuComponentCount();
        for (Action anchor : anchors) {
            int anchorIndex = anchor == null ? -1 : Menus.indexOf(menu, anchor);
            if (anchorIndex >= 0) {
                index = anchorIndex + 1;
                break;
            }
        }
        menu.add(item, index);
        return () -> {
            menu.remove(item);
            if (menu.getMenuComponentCount() == 0) {
                ImportExportGroup.remove(file, menu);
            }
        };
    }

    private static JMenu create(JMenu file) {
        JMenu menu = new JMenu(Messages.text("menu.file.export"));
        menu.putClientProperty(MARK, Boolean.TRUE);
        ImportExportGroup.insert(file, menu, false);
        return menu;
    }
}
