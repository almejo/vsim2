package cl.almejo.vsim.ui.window;

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
        menu = new JMenu("Ventana");
    }

    /** The Ventana menu as EditorActions builds it: «Nueva vista» is already there. */
    private WindowMenu windowMenu() {
        JMenuItem newView = new JMenuItem("Nueva vista");
        newView.addActionListener(event -> calls.add("new"));
        menu.add(newView);
        return new WindowMenu(menu, () -> List.copyOf(entries));
    }

    private WindowMenu.Entry entry(String title, boolean current) {
        return new WindowMenu.Entry(title, current, () -> calls.add(title));
    }

    @Test
    void listsEveryOpenWindowAfterTheItemsAlreadyInTheMenu() {
        entries.add(entry("sumador (1)", true));
        entries.add(entry("sumador (2)", false));
        windowMenu();
        assertThat(texts()).containsExactly("Nueva vista", "-", "sumador (1)", "sumador (2)");
        assertThat(((JCheckBoxMenuItem) menu.getMenuComponent(2)).isSelected()).isTrue();
        assertThat(((JCheckBoxMenuItem) menu.getMenuComponent(3)).isSelected()).isFalse();
        click(0);
        click(3);
        assertThat(calls).containsExactly("new", "sumador (2)");
    }

    @Test
    void openingTheMenuRebuildsTheListWithoutDuplicates() {
        entries.add(entry("sumador", true));
        windowMenu();
        entries.add(entry("Sin título", false));
        openMenu();
        openMenu();
        assertThat(texts()).containsExactly("Nueva vista", "-", "sumador", "Sin título");
    }

    @Test
    void rebuildingKeepsItemsAddedByOthers() {
        menu.add(new JMenuItem("Otra cosa"));
        entries.add(entry("sumador", true));
        WindowMenu windowMenu = windowMenu();
        windowMenu.rebuild();
        assertThat(texts()).containsExactly("Otra cosa", "Nueva vista", "-", "sumador");
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
