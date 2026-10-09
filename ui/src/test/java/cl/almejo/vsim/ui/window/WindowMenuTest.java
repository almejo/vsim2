package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowMenuTest {

    private final List<WindowMenu.Entry> entries = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private JMenu menu;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        menu = new JMenu("Ventana");
    }

    private WindowMenu windowMenu() {
        return new WindowMenu(menu, () -> calls.add("new"), () -> List.copyOf(entries));
    }

    private WindowMenu.Entry entry(String title, boolean current) {
        return new WindowMenu.Entry(title, current, () -> calls.add(title));
    }

    @Test
    void listsTheNewWindowItemAndEveryOpenWindow() {
        entries.add(entry("sumador — 1", true));
        entries.add(entry("sumador — 2", false));
        windowMenu();
        assertThat(texts()).containsExactly("Nueva ventana de este circuito", "-", "sumador — 1", "sumador — 2");
        assertThat(((JCheckBoxMenuItem) menu.getMenuComponent(2)).isSelected()).isTrue();
        assertThat(((JCheckBoxMenuItem) menu.getMenuComponent(3)).isSelected()).isFalse();
        click(0);
        click(3);
        assertThat(calls).containsExactly("new", "sumador — 2");
    }

    @Test
    void openingTheMenuRebuildsTheListWithoutDuplicates() {
        entries.add(entry("sumador — 1", true));
        windowMenu();
        entries.add(entry("Sin título — 1", false));
        openMenu();
        openMenu();
        assertThat(texts()).containsExactly("Nueva ventana de este circuito", "-", "sumador — 1", "Sin título — 1");
    }

    @Test
    void rebuildingKeepsItemsAddedByOthers() {
        menu.add(new JMenuItem("Otra cosa"));
        entries.add(entry("sumador — 1", true));
        WindowMenu windowMenu = windowMenu();
        windowMenu.rebuild();
        assertThat(texts()).containsExactly("Otra cosa", "Nueva ventana de este circuito", "-", "sumador — 1");
    }

    private void openMenu() {
        for (MenuListener listener : menu.getMenuListeners()) {
            listener.menuSelected(new MenuEvent(menu));
        }
    }

    private void click(int index) {
        JMenuItem item = (JMenuItem) menu.getMenuComponent(index);
        for (ActionListener listener : item.getActionListeners()) {
            listener.actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    private List<String> texts() {
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < menu.getMenuComponentCount(); index++) {
            texts.add(menu.getMenuComponent(index) instanceof JMenuItem item ? item.getText() : "-");
        }
        return texts;
    }
}
