package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;
import org.junit.jupiter.api.Test;

import javax.swing.JTabbedPane;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesDialogTest {

    @Test
    void thePreferencesHaveAGeneralTabAndAShortcutsTab() {
        Messages.initialize(Messages.SPANISH);
        JTabbedPane tabs = PreferencesDialog.tabs(AppPreferences.inMemory(), (parent, current) -> Optional.empty());
        assertThat(tabs.getTabCount()).isEqualTo(2);
        assertThat(tabs.getTitleAt(0)).isEqualTo("General");
        assertThat(tabs.getTitleAt(1)).isEqualTo("Atajos de teclado");
        assertThat(tabs.getComponentAt(0)).isInstanceOf(PreferencesForm.class);
        assertThat(tabs.getComponentAt(1)).isInstanceOf(ShortcutsPanel.class);
    }

    @Test
    void closingTheDialogStopsTheFormFollowingThePreferences() {
        Messages.initialize(Messages.SPANISH);
        AppPreferences preferences = AppPreferences.inMemory();
        int listeners = preferences.listenerCount();
        JTabbedPane tabs = PreferencesDialog.tabs(preferences, (parent, current) -> Optional.empty());
        assertThat(preferences.listenerCount()).isGreaterThan(listeners);

        PreferencesDialog.dispose(tabs);

        assertThat(preferences.listenerCount()).isEqualTo(listeners);
    }
}
