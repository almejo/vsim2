package cl.almejo.vsim.ui.busvalues;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusValuesFeaturesTest {

    private static final String NAME = "Valores de los buses";
    private final AppPreferences preferences = AppPreferences.inMemory();
    private EditorFixture fixture;
    private EditorView view;
    private EditorView otherView;
    private BusValuesFeatures features;
    private BusValuesFeatures otherFeatures;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        view = fixture.view();
        otherView = fixture.otherView();
        features = new BusValuesFeatures(view);
        otherFeatures = new BusValuesFeatures(otherView);
    }

    @AfterEach
    void close() {
        features.dispose();
        otherFeatures.dispose();
        fixture.close();
    }

    private static List<JCheckBoxMenuItem> viewItems(EditorView target) {
        List<JCheckBoxMenuItem> found = new ArrayList<>();
        for (Component child : target.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && NAME.equals(item.getText())) {
                found.add(item);
            }
        }
        return found;
    }

    private static List<AbstractButton> buttons(EditorView target) {
        List<AbstractButton> found = new ArrayList<>();
        for (Component child : target.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && NAME.equals(button.getAction().getValue(Action.NAME))) {
                found.add(button);
            }
        }
        return found;
    }

    @Test
    void theBusValuesAreOnByDefaultInTheViewMenuAndTheToolbar() {
        assertThat(preferences.showBusValues()).isTrue();
        assertThat(viewItems(view)).hasSize(1);
        assertThat(viewItems(view).get(0).isSelected()).isTrue();
        assertThat(buttons(view)).hasSize(1);
        assertThat(buttons(view).get(0).isSelected()).isTrue();
        assertThat(buttons(view).get(0).getToolTipText()).isEqualTo(NAME);
    }

    @Test
    void theToolbarToggleSitsNextToTheOneOfTheWiresThatFollow() {
        List<Component> components = List.of(view.toolBar().getComponents());
        assertThat(components.indexOf(buttons(view).get(0))).isGreaterThan(0);
    }

    @Test
    void theItemAndTheButtonFollowThePreferenceInEveryPane() {
        preferences.setShowBusValues(false);

        for (EditorView each : List.of(view, otherView)) {
            assertThat(viewItems(each).get(0).isSelected()).isFalse();
            assertThat(buttons(each).get(0).isSelected()).isFalse();
        }
        preferences.setShowBusValues(true);
        for (EditorView each : List.of(view, otherView)) {
            assertThat(viewItems(each).get(0).isSelected()).isTrue();
            assertThat(buttons(each).get(0).isSelected()).isTrue();
        }
    }

    @Test
    void clickingInOneViewChangesThePreferenceAndTheOtherViewFollows() {
        buttons(view).get(0).doClick();
        assertThat(preferences.showBusValues()).isFalse();
        assertThat(viewItems(otherView).get(0).isSelected()).isFalse();

        viewItems(otherView).get(0).doClick();
        assertThat(preferences.showBusValues()).isTrue();
        assertThat(buttons(view).get(0).isSelected()).isTrue();
    }

    @Test
    void theActionIsRegisteredForShortcutsWithoutADefaultKey() {
        assertThat(view.shortcutActions()).containsEntry("view.busValues", features.toggleAction());
        assertThat(ShortcutCatalog.find("view.busValues").orElseThrow().defaultStroke()).isEmpty();
    }

    @Test
    void disposingTakesBackTheItemTheButtonAndTheShortcut() {
        features.dispose();

        assertThat(viewItems(view)).isEmpty();
        assertThat(buttons(view)).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("view.busValues");
        assertThat(viewItems(otherView)).hasSize(1);
    }
}
