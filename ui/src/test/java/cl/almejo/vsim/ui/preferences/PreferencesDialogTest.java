package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.ai.AiPreferencesPanel;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesDialogTest {

    private static PreferencesPanel panel(AppPreferences preferences) {
        return PreferencesDialog.panel(preferences, (parent, current) -> Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    @Test
    void theGroupsAreSixInOrderWithALineBeforeTheShortcuts() {
        PreferencesPanel panel = panel(AppPreferences.inMemory());

        assertThat(PreferencesDialog.GROUP_IDS).containsExactly("general", "appearance", "accessibility", "files",
                "shortcuts", "ai");
        assertThat(panel.visibleGroupIds()).isEqualTo(PreferencesDialog.GROUP_IDS);
        assertThat(panel.groups()).extracting(PreferencesGroup::separatorBefore)
                .containsExactly(false, false, false, false, true, false);
        assertThat(panel.groups()).extracting(group -> Messages.text(group.titleKey())).containsExactly("General",
                "Aspecto", "Accesibilidad", "Archivos", "Atajos de teclado", "IA");
        assertThat(panel.groups().get(4).page()).isInstanceOf(ShortcutsPanel.class);
        assertThat(panel.groups().get(5).page()).isInstanceOf(AiPreferencesPanel.class);
        panel.dispose();
    }

    @Test
    void closingTheDialogStopsEveryPageFollowingThePreferences() {
        AppPreferences preferences = AppPreferences.inMemory();
        int listeners = preferences.listenerCount();
        PreferencesPanel panel = panel(preferences);
        assertThat(preferences.listenerCount()).isGreaterThan(listeners);

        panel.dispose();

        assertThat(preferences.listenerCount()).isEqualTo(listeners);
    }
}
