package cl.almejo.vsim.ui.grid;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class GridFeaturesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorFixture fixture;
    private EditorView view;
    private EditorView otherView;
    private GridFeatures features;
    private GridFeatures otherFeatures;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        view = fixture.view();
        otherView = fixture.otherView();
        features = new GridFeatures(view);
        otherFeatures = new GridFeatures(otherView);
    }

    @AfterEach
    void close() {
        features.dispose();
        otherFeatures.dispose();
        fixture.close();
    }

    private static Optional<JMenu> gridMenu(EditorView target) {
        for (java.awt.Component child : target.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenu menu && "Cuadrícula".equals(menu.getText())) {
                return Optional.of(menu);
            }
        }
        return Optional.empty();
    }

    private static JRadioButtonMenuItem radio(EditorView target, int index) {
        return (JRadioButtonMenuItem) gridMenu(target).orElseThrow().getMenuComponent(index);
    }

    private static List<Boolean> selected(EditorView target) {
        return List.of(radio(target, 0).isSelected(), radio(target, 1).isSelected(), radio(target, 2).isSelected());
    }

    @Test
    void theGridSubmenuComesAfterCentrarCircuitoWithThreeStylesAndTheCycle() {
        assertThat(texts(view.menu(MenuId.VIEW))).containsSubsequence("Zoom 100 %", "Centrar circuito", "---",
                "Cuadrícula");
        List<String> viewTexts = texts(view.menu(MenuId.VIEW));
        assertThat(viewTexts.get(viewTexts.indexOf("Centrar circuito") + 1)).isEqualTo("---");
        assertThat(viewTexts.get(viewTexts.indexOf("Centrar circuito") + 2)).isEqualTo("Cuadrícula");
        assertThat(texts(gridMenu(view).orElseThrow())).containsExactly("Puntos", "Líneas", "Nada", "---",
                "Separación", "---", "Cambiar el estilo de la cuadrícula");
        assertThat(selected(view)).containsExactly(true, false, false);
        assertThat(view.canvas().gridStyle()).isEqualTo(GridStyle.DOTS);
    }

    private static JMenu spacingMenu(EditorView target) {
        return (JMenu) gridMenu(target).orElseThrow().getMenuComponent(4);
    }

    private static List<Boolean> spacingSelected(EditorView target) {
        List<Boolean> selected = new ArrayList<>();
        for (java.awt.Component child : spacingMenu(target).getMenuComponents()) {
            selected.add(((JRadioButtonMenuItem) child).isSelected());
        }
        return selected;
    }

    @Test
    void theSpacingSubmenuOffersTwoFourFiveAndTenUnitsWithTwoChosen() {
        assertThat(texts(spacingMenu(view))).containsExactly("2 unidades", "4 unidades", "5 unidades",
                "10 unidades");
        assertThat(spacingSelected(view)).containsExactly(true, false, false, false);
        assertThat(view.canvas().gridLineSpacing()).isEqualTo(2);
    }

    @Test
    void choosingASpacingChangesThePreferenceAndEveryViewAndItsCanvas() {
        ((JRadioButtonMenuItem) spacingMenu(view).getMenuComponent(3)).doClick();

        assertThat(preferences.gridLineSpacing()).isEqualTo(10);
        assertThat(spacingSelected(view)).containsExactly(false, false, false, true);
        assertThat(spacingSelected(otherView)).containsExactly(false, false, false, true);
        assertThat(view.canvas().gridLineSpacing()).isEqualTo(10);
        assertThat(otherView.canvas().gridLineSpacing()).isEqualTo(10);
        assertThat(preferences.gridStyle()).as("the style is not touched").isEqualTo(GridStyle.DOTS);

        preferences.setGridLineSpacing(5);
        assertThat(spacingSelected(otherView)).containsExactly(false, false, true, false);
        assertThat(otherView.canvas().gridLineSpacing()).isEqualTo(5);
    }

    @Test
    void choosingLinesChangesThePreferenceAndEveryView() {
        radio(view, 1).doClick();

        assertThat(preferences.gridStyle()).isEqualTo(GridStyle.LINES);
        assertThat(selected(view)).containsExactly(false, true, false);
        assertThat(selected(otherView)).containsExactly(false, true, false);
        assertThat(view.canvas().gridStyle()).isEqualTo(GridStyle.LINES);
        assertThat(otherView.canvas().gridStyle()).isEqualTo(GridStyle.LINES);
    }

    @Test
    void aPreferenceChangedElsewhereShowsInTheItemsAndTheCanvas() {
        preferences.setGridStyle(GridStyle.NONE);

        assertThat(selected(view)).containsExactly(false, false, true);
        assertThat(view.canvas().gridStyle()).isEqualTo(GridStyle.NONE);
    }

    @Test
    void aStyleStoredEarlierIsShownWhenTheViewOpens() {
        preferences.setGridStyle(GridStyle.LINES);
        EditorView third = new EditorView(openDocument, view.services(), NO_WINDOW);
        GridFeatures thirdFeatures = new GridFeatures(third);
        try {
            assertThat(selected(third)).containsExactly(false, true, false);
            assertThat(third.canvas().gridStyle()).isEqualTo(GridStyle.LINES);
        } finally {
            thirdFeatures.dispose();
            third.dispose();
        }
    }

    @Test
    void theCycleGoesDotsLinesNothingAndBackWithItsShortcutIdAndNoDefaultKey() {
        assertThat(view.shortcutActions()).containsEntry("view.cycleGrid", features.cycleAction());
        assertThat(ShortcutCatalog.find("view.cycleGrid")).contains(new ShortcutEntry("view.cycleGrid",
                "action.cycleGrid", MenuId.VIEW, Scope.WINDOW, Optional.empty()));

        List<GridStyle> styles = new ArrayList<>();
        for (int step = 0; step < 3; step++) {
            features.cycleAction().actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
            styles.add(preferences.gridStyle());
        }

        assertThat(styles).containsExactly(GridStyle.LINES, GridStyle.NONE, GridStyle.DOTS);
    }

    @Test
    void disposingTakesBackTheSubmenuTheShortcutTheListenerAndTheStyle() {
        int listeners = preferences.listenerCount();
        preferences.setGridStyle(GridStyle.LINES);
        preferences.setGridLineSpacing(4);
        List<String> before = texts(view.menu(MenuId.VIEW));

        features.dispose();
        features.dispose();

        assertThat(gridMenu(view)).isEmpty();
        assertThat(texts(view.menu(MenuId.VIEW))).hasSize(before.size() - 2);
        assertThat(view.shortcutActions()).doesNotContainKey("view.cycleGrid");
        assertThat(preferences.listenerCount()).isEqualTo(listeners - 1);
        assertThat(view.canvas().gridStyle()).isEqualTo(GridStyle.DOTS);
        assertThat(view.canvas().gridLineSpacing()).isEqualTo(2);
        assertThat(otherView.canvas().gridStyle()).isEqualTo(GridStyle.LINES);
        assertThat(otherView.canvas().gridLineSpacing()).isEqualTo(4);
    }
}
