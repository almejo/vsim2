package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void installAddsTheItemAndTheShortcutAndOneRemoverUndoesBoth() {
        EditorView view = new EditorView(OpenDocument.createEmpty(StandardComponentTypes.registry()),
                PainterRegistry.standard(), ColorScheme.LIGHT, UiTestSupport.NO_WINDOW);
        JMenu menu = view.menu(MenuId.TOOLS);
        int before = menu.getMenuComponentCount();
        Action first = action("Primera");
        Action second = action("Segunda");
        Runnable firstRemover = Menus.install(view, MenuId.TOOLS, -1, first, "test.first");
        Runnable secondRemover = Menus.install(view, MenuId.TOOLS, 0, second, "test.second");
        assertThat(Menus.indexOf(menu, second)).isZero();
        assertThat(Menus.indexOf(menu, first)).isEqualTo(before + 1);
        assertThat(view.shortcutActions()).containsEntry("test.first", first).containsEntry("test.second", second);
        secondRemover.run();
        firstRemover.run();
        assertThat(menu.getMenuComponentCount()).isEqualTo(before);
        assertThat(view.shortcutActions()).doesNotContainKeys("test.first", "test.second");
        view.dispose();
    }

    @Test
    void aFailedInstallLeavesTheMenuAsItWas() {
        EditorView view = new EditorView(OpenDocument.createEmpty(StandardComponentTypes.registry()),
                PainterRegistry.standard(), ColorScheme.LIGHT, UiTestSupport.NO_WINDOW);
        JMenu menu = view.menu(MenuId.TOOLS);
        Action first = action("Primera");
        Runnable firstRemover = Menus.install(view, MenuId.TOOLS, -1, first, "test.first");
        int before = menu.getMenuComponentCount();
        assertThatThrownBy(() -> Menus.install(view, MenuId.TOOLS, 0, action("Repetida"), "test.first"))
                .isInstanceOf(RuntimeException.class);
        assertThat(menu.getMenuComponentCount()).isEqualTo(before);
        assertThat(view.shortcutActions()).containsEntry("test.first", first);
        firstRemover.run();
        view.dispose();
    }
}
