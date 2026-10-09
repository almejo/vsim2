package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The "Ventana" menu: a "new window of this circuit" item plus every open window, refreshed on each opening. */
public final class WindowMenu {

    /** One open window as the menu lists it. */
    public record Entry(String title, boolean current, Runnable focus) {
    }

    private final JMenu menu;
    private final Supplier<List<Entry>> entries;
    private final List<Component> listed = new ArrayList<>();

    public WindowMenu(JMenu menu, Runnable newWindow, Supplier<List<Entry>> entries) {
        this.menu = menu;
        this.entries = entries;
        JMenuItem newWindowItem = new JMenuItem(Messages.text("action.newWindow"));
        newWindowItem.addActionListener(event -> newWindow.run());
        menu.add(newWindowItem);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                rebuild();
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        });
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
