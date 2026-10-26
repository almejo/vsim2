package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WiresFollowFeaturesTest {

    private static final String NAME = "Los cables siguen al mover";
    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorFixture fixture;
    private EditorView view;
    private EditorView otherView;
    private WiresFollowFeatures features;
    private WiresFollowFeatures otherFeatures;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        view = fixture.view();
        otherView = fixture.otherView();
        features = new WiresFollowFeatures(view);
        otherFeatures = new WiresFollowFeatures(otherView);
    }

    @AfterEach
    void close() {
        features.dispose();
        otherFeatures.dispose();
        fixture.close();
    }

    private static JCheckBoxMenuItem editItem(EditorView target) {
        for (java.awt.Component child : target.menu(MenuId.EDIT).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && NAME.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no Editar item");
    }

    private static List<AbstractButton> buttonsNamed(EditorView target) {
        List<AbstractButton> found = new ArrayList<>();
        for (java.awt.Component child : target.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && NAME.equals(button.getAction().getValue(Action.NAME))) {
                found.add(button);
            }
        }
        return found;
    }

    @Test
    void theToolbarButtonSitsRightAfterTheToolGroupAndIsPressedByDefault() {
        List<java.awt.Component> components = List.of(view.toolBar().getComponents());
        AbstractButton button = buttonsNamed(view).get(0);
        int index = components.indexOf(button);
        assertThat(((AbstractButton) components.get(index - 1)).getAction()).isSameAs(view.actions().zoomTool());
        assertThat(button.isSelected()).isTrue();
        assertThat(button.getToolTipText()).startsWith(NAME).contains("Mayús");
    }

    @Test
    void theEditMenuCheckboxAndTheToolbarButtonReflectThePreference() {
        assertThat(editItem(view).isSelected()).isTrue();
        preferences.setWiresFollow(false);
        assertThat(editItem(view).isSelected()).isFalse();
        assertThat(buttonsNamed(view).get(0).isSelected()).isFalse();
        preferences.setWiresFollow(true);
        assertThat(editItem(view).isSelected()).isTrue();
        assertThat(buttonsNamed(view).get(0).isSelected()).isTrue();
    }

    @Test
    void clickingInOneViewChangesThePreferenceAndTheOtherViewFollows() {
        buttonsNamed(view).get(0).doClick();
        assertThat(preferences.wiresFollow()).isFalse();
        assertThat(editItem(otherView).isSelected()).isFalse();
        assertThat(buttonsNamed(otherView).get(0).isSelected()).isFalse();

        editItem(otherView).doClick();
        assertThat(preferences.wiresFollow()).isTrue();
        assertThat(buttonsNamed(view).get(0).isSelected()).isTrue();
        assertThat(editItem(view).isSelected()).isTrue();
    }

    @Test
    void theActionIsRegisteredForShortcutsWithoutADefaultStroke() {
        assertThat(view.shortcutActions()).containsEntry("edit.wiresFollow", features.toggleAction());
        assertThat(ShortcutCatalog.find("edit.wiresFollow").orElseThrow().defaultStroke()).isEmpty();
    }

    @Test
    void theTooltipNamesTheConfiguredInvertKey() {
        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(buttonsNamed(view).get(0).getToolTipText()).contains("Alt");
        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        assertThat(buttonsNamed(view).get(0).getToolTipText()).isEqualTo(NAME);
    }

    @Test
    void disposeTakesBackTheButtonTheItemTheShortcutAndTheListener() {
        int listeners = preferences.listenerCount();
        features.dispose();
        assertThat(buttonsNamed(view)).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("edit.wiresFollow");
        assertThat(view.menu(MenuId.EDIT).getMenuComponents())
                .noneMatch(child -> child instanceof JMenuItem item && NAME.equals(item.getText()));
        assertThat(preferences.listenerCount()).isLessThan(listeners);
        assertThat(buttonsNamed(otherView)).hasSize(1);
        assertThat(otherView.shortcutActions()).containsKey("edit.wiresFollow");
    }
}
