package cl.almejo.vsim.ui.window;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The list of open windows at the end of the "Ventana" menu, refreshed on each opening. The items already in the
 * menu («Nueva vista», from {@code EditorActions}) stay above it.
 */
public final class WindowMenu {

    /** One open window as the menu lists it. */
    public record Entry(String title, boolean current, Runnable focus) {
    }

    private final JMenu menu;
    private final Supplier<List<Entry>> entries;
    private final List<Component> listed = new ArrayList<>();

    public WindowMenu(JMenu menu, Supplier<List<Entry>> entries) {
        this.menu = menu;
        this.entries = entries;
        Menus.onOpen(menu, this::rebuild);
        rebuild();
    }

    /** Replaces the listed windows with the current ones; other items of the menu stay. */
    public void rebuild() {
        listed.forEach(menu::remove);
        listed.clear();
        List<Entry> current = entries.get();
        if (current.isEmpty()) {
            return;
        }
        JPopupMenu.Separator separator = new JPopupMenu.Separator();
        menu.add(separator);
        listed.add(separator);
        for (Entry entry : current) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(entry.title(), entry.current());
            item.addActionListener(event -> entry.focus().run());
            menu.add(item);
            listed.add(item);
        }
    }
}
