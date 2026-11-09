package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Editar ▸ «Dibujar cables» (the 2026-11-09 change): two radio items right after «Los cables siguen al mover», one
 * mode for the whole app kept in the preferences, and no Cable tool left anywhere.
 */
class WireModeMenuTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private EditorFixture fixture;
    private EditorView view;
    private EditorView otherView;
    private WiresFollowFeatures features;
    private WiresFollowFeatures otherFeatures;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = EditorFixture.create(preferences);
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

    private static JMenu drawWires(EditorView target) {
        for (Component child : target.menu(MenuId.EDIT).getMenuComponents()) {
            if (child instanceof JMenu submenu && "Dibujar cables".equals(submenu.getText())) {
                return submenu;
            }
        }
        throw new AssertionError("no Editar ▸ Dibujar cables");
    }

    private static List<JRadioButtonMenuItem> modes(EditorView target) {
        List<JRadioButtonMenuItem> items = new ArrayList<>();
        for (Component child : drawWires(target).getMenuComponents()) {
            if (child instanceof JRadioButtonMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }

    @Test
    void theSubmenuComesRightAfterWiresFollowWithBothModesAndContinuousChosen() {
        Component[] items = view.menu(MenuId.EDIT).getMenuComponents();
        int submenuIndex = List.of(items).indexOf(drawWires(view));
        assertThat(items[submenuIndex - 1]).isInstanceOfSatisfying(JMenuItem.class,
                item -> assertThat(item.getText()).isEqualTo("Los cables siguen al mover"));

        assertThat(modes(view)).extracting(JMenuItem::getText).containsExactly(
                "Continuo — cada clic fija una esquina", "Por trozos — un arrastre por tramo");
        assertThat(modes(view)).extracting(JMenuItem::isSelected).containsExactly(true, false);
        assertThat(modes(view)).allSatisfy(item -> assertThat(item.getIcon()).isNotNull());
        assertThat(preferences.wireMode()).isEqualTo(WireMode.CONTINUOUS);
    }

    @Test
    void choosingAModeRemembersItAndTheOtherWindowFollows() {
        modes(view).get(1).doClick();

        assertThat(preferences.wireMode()).isEqualTo(WireMode.PIECES);
        assertThat(modes(view)).extracting(JMenuItem::isSelected).containsExactly(false, true);
        assertThat(modes(otherView)).extracting(JMenuItem::isSelected).containsExactly(false, true);

        modes(otherView).get(0).doClick();
        assertThat(preferences.wireMode()).isEqualTo(WireMode.CONTINUOUS);
        assertThat(modes(view)).extracting(JMenuItem::isSelected).containsExactly(true, false);
    }

    @Test
    void aModeChangedElsewhereShowsInTheItems() {
        preferences.setWireMode(WireMode.PIECES);

        assertThat(modes(view)).extracting(JMenuItem::isSelected).containsExactly(false, true);
    }

    @Test
    void bothModesAreOfferedToTheShortcutEditorWithNoKey() {
        assertThat(view.shortcutActions()).containsKeys("edit.wireModeContinuous", "edit.wireModePieces");
        assertThat(ShortcutCatalog.find("edit.wireModeContinuous").orElseThrow().defaultStroke()).isEmpty();
        assertThat(ShortcutCatalog.find("edit.wireModePieces").orElseThrow().defaultStroke()).isEmpty();
    }

    @Test
    void disposingTakesTheSubmenuAway() {
        features.dispose();

        assertThat(view.menu(MenuId.EDIT).getMenuComponents())
                .noneMatch(child -> child instanceof JMenu submenu && "Dibujar cables".equals(submenu.getText()));
        assertThat(view.shortcutActions()).doesNotContainKeys("edit.wireModeContinuous", "edit.wireModePieces");
    }

    @Test
    void theWindowHasNoCableToolAnyMore() {
        assertThat(ShortcutCatalog.find("view.wireTool")).isEmpty();
        assertThat(ShortcutCatalog.find("edit.wireContinuous")).isEmpty();
        assertThat(view.menu(MenuId.VIEW).getMenuComponents())
                .noneMatch(child -> child instanceof JMenuItem item && "Cable".equals(item.getText()));
        assertThat(splitButtons(view.toolBar())).isEmpty();
    }

    private static List<SplitButton> splitButtons(Component component) {
        List<SplitButton> found = new ArrayList<>();
        if (component instanceof SplitButton button) {
            found.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(splitButtons(child));
            }
        }
        return found;
    }
}
