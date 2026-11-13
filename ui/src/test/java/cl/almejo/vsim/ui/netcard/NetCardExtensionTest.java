package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.busvalues.BusValuesExtension;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetCardExtensionTest {

    private static final String NAME = "Resaltar la red al pasar";
    private final AppPreferences preferences = AppPreferences.inMemory();
    private EditorFixture fixture;
    private EditorView view;
    private EditorView otherView;
    private Runnable uninstall;
    private Runnable otherUninstall;
    private Runnable busValuesUninstall;
    private Runnable otherBusValuesUninstall;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        view = fixture.view();
        otherView = fixture.otherView();
        BusValuesExtension busValues = new BusValuesExtension(); // the item goes right after its «Valores de los buses»
        busValuesUninstall = busValues.install(view);
        otherBusValuesUninstall = busValues.install(otherView);
        NetCardExtension extension = new NetCardExtension();
        uninstall = extension.install(view);
        otherUninstall = extension.install(otherView);
    }

    @AfterEach
    void close() {
        uninstall.run();
        otherUninstall.run();
        busValuesUninstall.run();
        otherBusValuesUninstall.run();
        fixture.close();
    }

    private static List<JCheckBoxMenuItem> items(EditorView target) {
        List<JCheckBoxMenuItem> found = new ArrayList<>();
        for (Component child : target.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && NAME.equals(item.getText())) {
                found.add(item);
            }
        }
        return found;
    }

    @Test
    void theItemIsOffByDefaultRightAfterTheBusValues() {
        assertThat(AppPreferences.inMemory().highlightNetOnHover()).isFalse();
        assertThat(items(view)).hasSize(1);
        assertThat(items(view).get(0).isSelected()).isEqualTo(preferences.highlightNetOnHover());
        Component[] components = view.menu(MenuId.VIEW).getMenuComponents();
        int index = List.of(components).indexOf(items(view).get(0));
        assertThat(((JMenuItem) components[index - 1]).getText()).isEqualTo("Valores de los buses");
    }

    @Test
    void clickingWritesThePreferenceAndEveryViewFollows() {
        items(view).get(0).doClick();
        assertThat(preferences.highlightNetOnHover()).isTrue();
        assertThat(items(otherView).get(0).isSelected()).isTrue();

        preferences.setHighlightNetOnHover(false);
        assertThat(items(view).get(0).isSelected()).isFalse();
    }

    @Test
    void theActionIsRegisteredForShortcutsWithoutADefaultKey() {
        assertThat(view.shortcutActions()).containsKey("view.highlightNet");
        assertThat(ShortcutCatalog.find("view.highlightNet").orElseThrow().defaultStroke()).isEmpty();
    }

    @Test
    void uninstallingTakesBackTheItemAndTheShortcutOfThatViewOnly() {
        uninstall.run();

        assertThat(items(view)).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("view.highlightNet");
        assertThat(items(otherView)).hasSize(1);
    }
}
