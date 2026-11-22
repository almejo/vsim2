package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

/** The strip's «+» runs «Nueva pestaña vacía», so its tooltip shows that action's key, not Archivo ▸ Nuevo's. */
class NewEmptyTabShortcutTest {

    @Test
    void thePlusShowsTheKeyOfNewEmptyTabAndFollowsTheUsersBinding() {
        AppPreferences preferences = AppPreferences.inMemory();
        KeyBindings bindings = new KeyBindings(preferences);
        assertThat(CircuitWindow.newTabShortcut(bindings))
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
        KeyStroke other = KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        bindings.assign("window.newEmptyTab", other);
        assertThat(CircuitWindow.newTabShortcut(new KeyBindings(preferences))).contains(other);
    }
}
