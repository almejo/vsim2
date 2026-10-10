package cl.almejo.vsim.ui.window;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Small helpers shared by the features that add items to the window's menus. EDT only. */
public final class Menus {

    private Menus() {
    }

    /** The position of the item of {@code action} in {@code menu}, or -1 when the menu has no such item. */
    public static int indexOf(JMenu menu, Action action) {
        for (int index = 0; index < menu.getMenuComponentCount(); index++) {
            if (menu.getMenuComponent(index) instanceof JMenuItem item && item.getAction() == action) {
                return index;
            }
        }
        return -1;
    }

    /** Runs {@code opened} each time {@code menu} opens; returns the listener so a disposer can remove it. */
    public static MenuListener onOpen(JMenu menu, Runnable opened) {
        MenuListener listener = new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                opened.run();
            }

            @Override
            public void menuDeselected(MenuEvent event) {
                // only the opening matters
            }

            @Override
            public void menuCanceled(MenuEvent event) {
                // only the opening matters
            }
        };
        menu.addMenuListener(listener);
        return listener;
    }
}
