package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesPanelTest {

    private static final KeyStroke ESCAPE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    private static final KeyStroke FIND = KeyStroke.getKeyStroke(KeyEvent.VK_F,
            ShortcutCatalog.menuMask());

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<PreferencesPanel> opened = new ArrayList<>();

    @AfterEach
    void close() {
        opened.forEach(PreferencesPanel::dispose);
    }

    private PreferencesPanel panel(Optional<String> group) {
        PreferencesPanel panel = PreferencesDialog.panel(preferences, (parent, current) -> Optional.empty(),
                Optional.empty(), group);
        opened.add(panel);
        return panel;
    }

    private static boolean pressEscapeIn(JComponent component) {
        Action action = component.getActionMap().get(component.getInputMap().get(ESCAPE));
        return SwingUtilities.notifyAction(action, ESCAPE,
                new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED),
                component, 0);
    }

    @Test
    void withoutAGroupItOpensOnGeneralWhenNothingWasSeen() {
        assertThat(panel(Optional.empty()).selectedGroupId()).isEqualTo("general");
    }

    @Test
    void withoutAGroupItOpensOnTheLastGroupSeen() {
        preferences.setLastPreferencesGroup("files");

        assertThat(panel(Optional.empty()).selectedGroupId()).isEqualTo("files");
    }

    @Test
    void anUnknownStoredGroupOpensOnGeneral() {
        preferences.setLastPreferencesGroup("zoom");

        assertThat(panel(Optional.empty()).selectedGroupId()).isEqualTo("general");
    }

    @Test
    void openingOnAGroupShowsItAndStoresItAsTheLastSeen() {
        assertThat(panel(Optional.of("shortcuts")).selectedGroupId()).isEqualTo("shortcuts");

        assertThat(preferences.lastPreferencesGroup()).contains("shortcuts");
    }

    @Test
    void selectingAGroupStoresIt() {
        PreferencesPanel panel = panel(Optional.empty());

        panel.select("ai");

        assertThat(panel.selectedGroupId()).isEqualTo("ai");
        assertThat(preferences.lastPreferencesGroup()).contains("ai");
    }

    @Test
    void searchingForADarkThemeLeavesOnlyAppearanceWithOneMatch() {
        PreferencesPanel panel = panel(Optional.empty());

        panel.searchField().setText("oscuro");

        assertThat(panel.visibleGroupIds()).containsExactly("appearance");
        assertThat(panel.countFor("appearance")).isEqualTo(1);
        assertThat(panel.selectedGroupId()).isEqualTo("appearance");
        assertThat(preferences.lastPreferencesGroup()).as("a search move is not a choice").isEmpty();
    }

    @Test
    void searchingIgnoresAccentsAndCaseAndFindsKeywords() {
        PreferencesPanel panel = panel(Optional.empty());

        panel.searchField().setText("MAYUS");
        assertThat(panel.visibleGroupIds()).containsExactly("general", "shortcuts");
        panel.searchField().setText("carpeta");
        assertThat(panel.visibleGroupIds()).containsExactly("files");
        panel.searchField().setText("animacion");
        assertThat(panel.visibleGroupIds()).containsExactly("accessibility");
    }

    @Test
    void whenNothingMatchesTheListIsEmptyAndTheRightSideSaysSo() {
        PreferencesPanel panel = panel(Optional.empty());

        panel.searchField().setText("zzz");

        assertThat(panel.visibleGroupIds()).isEmpty();
        assertThat(panel.noResultsText()).contains("Nada coincide con «zzz»");
    }

    @Test
    void clearingTheSearchShowsAllTheGroupsAndRowsAgain() {
        PreferencesPanel panel = panel(Optional.empty());
        panel.searchField().setText("oscuro");

        panel.searchField().setText("");

        assertThat(panel.visibleGroupIds()).isEqualTo(PreferencesDialog.GROUP_IDS);
        assertThat(panel.noResultsText()).isEmpty();
        for (PreferencesGroup group : panel.groups()) {
            if (group.page() instanceof PreferencesPage page) {
                assertThat(page.visibleRowIds()).hasSameSizeAs(page.sectionRows().values().stream()
                        .flatMap(List::stream).toList());
            }
        }
    }

    @Test
    void theShortcutsCountFollowsItsOwnFilterAndTheDialogSearchDrivesIt() {
        PreferencesPanel panel = panel(Optional.empty());
        ShortcutsPanel shortcuts = (ShortcutsPanel) panel.groups().get(4).page();

        panel.searchField().setText("abrir");

        assertThat(shortcuts.matchCount("abrir")).isPositive();
        assertThat(panel.countFor("shortcuts")).isEqualTo(shortcuts.matchCount("abrir"));
        assertThat(shortcuts.query()).isEqualTo("abrir");
        panel.searchField().setText("");
        assertThat(shortcuts.query()).isEmpty();
    }

    @Test
    void theAiGroupIsFoundByTheWordsOfItsLabels() {
        PreferencesPanel panel = panel(Optional.empty());

        panel.searchField().setText("puerto");

        assertThat(panel.visibleGroupIds()).contains("ai");
        assertThat(panel.countFor("ai")).isPositive();
        panel.searchField().setText("secreto");
        assertThat(panel.visibleGroupIds()).contains("ai");
    }

    @Test
    void escapeClearsTheSearchAndASecondEscapeIsLeftToTheDialog() {
        PreferencesPanel panel = panel(Optional.empty());
        panel.searchField().setText("tema");

        assertThat(pressEscapeIn(panel.searchField())).as("clears and consumes").isTrue();
        assertThat(panel.searchField().getText()).isEmpty();
        assertThat(pressEscapeIn(panel.searchField())).as("empty field: not consumed, the dialog closes").isFalse();
    }

    @Test
    void findFocusesTheSearchFieldAndSelectsItsText() {
        PreferencesPanel panel = panel(Optional.empty());
        panel.searchField().setText("tema");
        panel.searchField().select(0, 0);

        Action find = panel.getActionMap().get(panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(FIND));
        find.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "find"));

        assertThat(panel.searchField().getSelectedText()).isEqualTo("tema");
    }

    @Test
    void findIsLeftToTheShortcutCaptureWhileItRecordsAKey() {
        boolean[] capturing = {true};
        BooleanSupplier recording = () -> capturing[0];
        PreferencesPanel panel = new PreferencesPanel(preferences,
                List.of(new PreferencesGroup("general", "preferences.group.general", new JPanel(), false,
                        query -> 0)), Optional.empty(), recording, () -> { });
        panel.searchField().setText("tema");
        panel.searchField().select(0, 0);

        Action find = panel.getActionMap().get(panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(FIND));

        assertThat(find.isEnabled()).isFalse();
        capturing[0] = false;
        assertThat(find.isEnabled()).isTrue();
    }
}
