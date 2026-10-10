package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MenusTest {

    private static Action action(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
            }
        };
    }

    @Test
    void findsTheItemOfAnActionOrMinusOne() {
        JMenu menu = new JMenu("Archivo");
        Action open = action("Abrir");
        menu.add(action("Nuevo"));
        menu.addSeparator();
        menu.add(open);
        assertThat(Menus.indexOf(menu, open)).isEqualTo(2);
        assertThat(Menus.indexOf(menu, action("Otra"))).isEqualTo(-1);
    }

    @Test
    void runsOnEveryOpeningUntilTheListenerIsRemoved() {
        JMenu menu = new JMenu("Ventana");
        AtomicInteger openings = new AtomicInteger();
        MenuListener listener = Menus.onOpen(menu, openings::incrementAndGet);
        MenuEvent event = new MenuEvent(menu);
        listener.menuSelected(event);
        listener.menuDeselected(event);
        listener.menuCanceled(event);
        assertThat(openings).hasValue(1);
        assertThat(menu.getMenuListeners()).contains(listener);
        menu.removeMenuListener(listener);
        assertThat(menu.getMenuListeners()).doesNotContain(listener);
    }
}
