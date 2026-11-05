package cl.almejo.vsim.ui.window;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The list of open tabs at the end of the "Ventana" menu, grouped by window, refreshed on each opening (spec §4.8).
 * The items already in the menu (from {@code EditorActions} and the extensions) stay above it.
 */
public final class WindowMenu {

    /** One open tab as the menu lists it, or ({@code header}) the name of a window above its tabs. */
    public record Entry(String title, boolean current, Runnable focus, boolean header) {

        public Entry(String title, boolean current, Runnable focus) {
            this(title, current, focus, false);
        }

        /** «Ventana 2» above that window's tabs: shown, never chosen. */
        public static Entry header(String title) {
            return new Entry(title, false, () -> {
            }, true);
        }
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

    /** Replaces the listed tabs with the current ones; other items of the menu stay. */
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
            JMenuItem item = itemFor(entry);
            menu.add(item);
            listed.add(item);
        }
    }

    /** The menu item of {@code entry}: a disabled label for a header, a check item for a tab. */
    public static JMenuItem itemFor(Entry entry) {
        if (entry.header()) {
            JMenuItem header = new JMenuItem(entry.title());
            header.setEnabled(false);
            return header;
        }
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(entry.title(), entry.current());
        item.addActionListener(event -> entry.focus().run());
        return item;
    }
}
