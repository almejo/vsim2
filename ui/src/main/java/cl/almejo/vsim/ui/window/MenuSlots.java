package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.Optional;

/**
 * Puts items into their {@link MenuSlot}: each item is found again by a client property, never by its translated text
 * or by an index, so the features of Ver, Herramientas and Lógica install and uninstall in any order. Separators are
 * the helper's own, rebuilt after every change between neighbours of different groups; a submenu is made by its first
 * item and removed with its last one (like {@link ExportMenu}). EDT only.
 */
public final class MenuSlots {

    /** The client property that holds the {@link MenuSlot} of an item or submenu placed here. */
    static final String SLOT = "vsim.menu.slot";
    /** The client property that marks the separators this helper made. */
    static final String SEPARATOR = "vsim.menu.slotSeparator";

    private MenuSlots() {
    }

    /** The submenu {@code submenu} under {@code menu} (the bar's menu of its {@link MenuSlot#menu()}), when made. */
    public static Optional<JMenu> find(JMenu menu, MenuSlot submenu) {
        if (!submenu.isSubmenu()) {
            throw new IllegalArgumentException(submenu + " is not a submenu");
        }
        Optional<JMenu> container = submenu.parent().isEmpty() ? Optional.of(menu)
                : find(menu, submenu.parent().orElseThrow());
        return container.flatMap(holder -> submenuIn(holder, submenu));
    }

    private static Optional<JMenu> submenuIn(JMenu holder, MenuSlot submenu) {
        for (Component child : holder.getMenuComponents()) {
            if (child instanceof JMenu candidate && candidate.getClientProperty(SLOT) == submenu) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Like {@link #add(JMenu, MenuSlot, JMenuItem)} in {@code view}'s menu of the slot. */
    public static Runnable add(EditorView view, MenuSlot slot, JMenuItem item) {
        return add(view.menu(slot.menu()), slot, item);
    }

    /**
     * Adds {@code item} to {@code slot} under {@code menu} (the bar's menu of {@link MenuSlot#menu()}), after the items
     * already in that slot, making the submenus on the way.
     *
     * @return what takes the item back, and with it every submenu it leaves empty; calling it twice does nothing
     */
    public static Runnable add(JMenu menu, MenuSlot slot, JMenuItem item) {
        if (slot.isSubmenu()) {
            throw new IllegalArgumentException(slot + " is a submenu: add items to its slots");
        }
        item.putClientProperty(SLOT, slot);
        JMenu container = containerOf(menu, slot);
        place(container, item);
        return () -> {
            if (item.getParent() == container.getPopupMenu()) {
                takeOut(menu, container, item);
            }
        };
    }

    /**
     * Adds a plain item of {@code action} to {@code slot} and offers the action to the shortcut editor under
     * {@code shortcutId}, like {@link Menus#install}. The remover undoes both, the shortcut first.
     */
    public static Runnable install(EditorView view, MenuSlot slot, Action action, String shortcutId) {
        Runnable shortcutRemover = view.registerShortcutAction(shortcutId, action);
        Runnable itemRemover;
        try {
            itemRemover = add(view, slot, new JMenuItem(action));
        } catch (RuntimeException failure) {
            shortcutRemover.run();
            throw failure;
        }
        return () -> {
            shortcutRemover.run();
            itemRemover.run();
        };
    }

    private static JMenu containerOf(JMenu menu, MenuSlot slot) {
        if (slot.parent().isEmpty()) {
            return menu;
        }
        MenuSlot submenuSlot = slot.parent().orElseThrow();
        return find(menu, submenuSlot).orElseGet(() -> newSubmenu(menu, submenuSlot));
    }

    private static JMenu newSubmenu(JMenu menu, MenuSlot submenuSlot) {
        JMenu submenu = new JMenu(Messages.text(submenuSlot.titleKey().orElseThrow()));
        submenu.putClientProperty(SLOT, submenuSlot);
        place(containerOf(menu, submenuSlot), submenu);
        return submenu;
    }

    /** Inserts {@code entry} before the first sibling of a later slot, then lays the separators again. */
    private static void place(JMenu container, JComponent entry) {
        removeSeparators(container);
        MenuSlot slot = (MenuSlot) entry.getClientProperty(SLOT);
        int index = container.getMenuComponentCount();
        for (int position = 0; position < container.getMenuComponentCount(); position++) {
            if (slotOf(container.getMenuComponent(position)) instanceof MenuSlot sibling
                    && sibling.ordinal() > slot.ordinal()) {
                index = position;
                break;
            }
        }
        container.add(entry, index);
        addSeparators(container);
    }

    private static void takeOut(JMenu menu, JMenu container, JComponent entry) {
        container.remove(entry);
        removeSeparators(container);
        addSeparators(container);
        if (container != menu && container.getMenuComponentCount() == 0) {
            MenuSlot submenuSlot = (MenuSlot) container.getClientProperty(SLOT);
            JMenu parent = submenuSlot.parent().isEmpty() ? menu
                    : find(menu, submenuSlot.parent().orElseThrow()).orElseThrow();
            takeOut(menu, parent, container);
        }
    }

    private static void removeSeparators(JMenu container) {
        for (Component child : container.getMenuComponents()) {
            if (child instanceof JComponent component && Boolean.TRUE.equals(component.getClientProperty(SEPARATOR))) {
                container.remove(component);
            }
        }
    }

    private static void addSeparators(JMenu container) {
        MenuSlot previous = null;
        for (int position = 0; position < container.getMenuComponentCount(); position++) {
            if (slotOf(container.getMenuComponent(position)) instanceof MenuSlot slot) {
                if (previous != null && previous.group() != slot.group()) {
                    JPopupMenu.Separator separator = new JPopupMenu.Separator();
                    separator.putClientProperty(SEPARATOR, Boolean.TRUE);
                    container.add(separator, position);
                    position++;
                }
                previous = slot;
            }
        }
    }

    private static MenuSlot slotOf(Component component) {
        return component instanceof JComponent swing && swing.getClientProperty(SLOT) instanceof MenuSlot slot
                ? slot : null;
    }
}
