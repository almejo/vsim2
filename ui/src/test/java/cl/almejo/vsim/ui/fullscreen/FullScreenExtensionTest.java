package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver ▸ «Modo presentación (pantalla completa)» (F11), a check item, and its toggle at the end of the toolbar,
 * pressed while the mode is on and saying how to leave.
 */
class FullScreenExtensionTest {

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new FullScreenExtension()));
        view = fixture.addTab();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private JCheckBoxMenuItem viewItem(EditorView target) {
        JMenu viewMenu = target.menu(MenuId.VIEW);
        Component last = viewMenu.getMenuComponent(viewMenu.getMenuComponentCount() - 1);
        assertThat(last).isInstanceOf(JCheckBoxMenuItem.class);
        return (JCheckBoxMenuItem) last;
    }

    private JToggleButton toolbarToggle(EditorView target) {
        Component[] components = target.toolBar().getComponents();
        Component last = components[components.length - 1];
        assertThat(last).isInstanceOf(JToggleButton.class);
        return (JToggleButton) last;
    }

    @Test
    void theCatalogHasF11ForTheModeInTheVerMenu() {
        Optional<ShortcutEntry> entry = ShortcutCatalog.find(FullScreenExtension.SHORTCUT_ID);
        assertThat(entry).isPresent();
        assertThat(entry.get().group()).isEqualTo(MenuId.VIEW);
        assertThat(entry.get().defaultStroke()).contains(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        assertThat(view.shortcutActions()).containsKey(FullScreenExtension.SHORTCUT_ID);
    }

    @Test
    void theShortcutsGiveTheVerItemF11() {
        try (FullScreenFixture withShortcuts = new FullScreenFixture(
                List.of(new FullScreenExtension(), new ShortcutsExtension()))) {
            EditorView target = withShortcuts.addTab();
            assertThat(viewItem(target).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        }
    }

    @Test
    void theVerItemIsLastAndCheckedWhileTheModeIsOn() {
        JCheckBoxMenuItem item = viewItem(view);
        assertThat(item.getText()).isEqualTo("Modo presentación (pantalla completa)");
        assertThat(item.isSelected()).isFalse();
        item.doClick();
        assertThat(fixture.features.isActive()).isTrue();
        assertThat(item.isSelected()).isTrue();
        item.doClick();
        assertThat(fixture.features.isActive()).as("choosing it again leaves").isFalse();
        assertThat(item.isSelected()).isFalse();
    }

    @Test
    void theToolbarToggleIsPressedWhileTheModeIsOnAndSaysHowToLeave() {
        JToggleButton toggle = toolbarToggle(view);
        assertThat(toggle.getToolTipText()).isEqualTo("Modo presentación (F11)");
        toggle.doClick();
        assertThat(fixture.features.isActive()).isTrue();
        assertThat(toggle.isSelected()).isTrue();
        assertThat(toggle.getToolTipText()).isEqualTo("Salir del modo presentación (F11 o Esc)");
        toggle.doClick();
        assertThat(fixture.features.isActive()).isFalse();
        assertThat(toggle.isSelected()).isFalse();
        assertThat(toggle.getToolTipText()).isEqualTo("Modo presentación (F11)");
    }

    @Test
    void everyTabOfTheWindowShowsTheSameState() {
        EditorView second = fixture.addTab();
        fixture.features.enter();
        assertThat(viewItem(view).isSelected()).isTrue();
        assertThat(toolbarToggle(second).isSelected()).isTrue();
    }

    @Test
    void withoutAWindowTheItemIsThereButDisabled() {
        try (EditorFixture plain = EditorFixture.create()) {
            Runnable removal = new FullScreenExtension().install(plain.view());
            JCheckBoxMenuItem item = viewItem(plain.view());
            assertThat(item.isEnabled()).isFalse();
            removal.run();
        }
    }

    @Test
    void theRemovalTakesBackTheItemTheToggleAndTheShortcut() {
        try (EditorFixture plain = EditorFixture.create()) {
            EditorView target = plain.view();
            int menuItems = target.menu(MenuId.VIEW).getMenuComponentCount();
            int toolbarComponents = target.toolBar().getComponentCount();
            Runnable removal = new FullScreenExtension().install(target);
            removal.run();
            assertThat(target.menu(MenuId.VIEW).getMenuComponentCount()).isEqualTo(menuItems);
            assertThat(target.toolBar().getComponentCount()).isEqualTo(toolbarComponents);
            assertThat(target.shortcutActions()).doesNotContainKey(FullScreenExtension.SHORTCUT_ID);
        }
    }
}
