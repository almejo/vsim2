package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyBindingsTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final KeyBindings bindings = new KeyBindings(preferences);
    private final AtomicInteger changes = new AtomicInteger();

    private static KeyStroke control(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, CONTROL);
    }

    private static KeyStroke plain(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, 0);
    }

    private String stored(String id) {
        return preferences.shortcutsNode().get(id, null);
    }

    @Test
    void defaultsComeFromTheCatalog() {
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("edit.delete")).contains(plain(KeyEvent.VK_DELETE));
        assertThat(bindings.strokeFor("window.newView")).isEmpty();
        assertThat(bindings.strokeFor("not.an.action")).isEmpty();
    }

    @Test
    void anAssignedStrokeIsStoredInKeyStrokeFormNotifiesOnceAndSurvivesARestart() {
        preferences.addListener(changes::incrementAndGet);
        bindings.assign("file.save", control(KeyEvent.VK_G));
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_G));
        assertThat(stored("file.save")).isEqualTo("ctrl pressed G");
        assertThat(changes).hasValue(1);
        assertThat(new KeyBindings(preferences).strokeFor("file.save")).contains(control(KeyEvent.VK_G));
    }

    @Test
    void assigningTheDefaultAgainStoresNoOverride() {
        bindings.assign("file.save", control(KeyEvent.VK_G));
        bindings.assign("file.save", control(KeyEvent.VK_S));
        assertThat(stored("file.save")).isNull();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void clearingLeavesNoShortcutAndResetBringsTheDefaultBack() {
        bindings.clear("file.save");
        assertThat(bindings.strokeFor("file.save")).isEmpty();
        assertThat(stored("file.save")).isEmpty();
        bindings.reset("file.save");
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(stored("file.save")).isNull();
        bindings.clear("window.newView");
        assertThat(stored("window.newView")).isNull();
        assertThat(bindings.strokeFor("window.newView")).isEmpty();
    }

    @Test
    void conflictsNameTheOtherActionAndAssignReassigns() {
        assertThat(bindings.conflictFor(control(KeyEvent.VK_S), "edit.selectAll"))
                .hasValueSatisfying(entry -> assertThat(entry.id()).isEqualTo("file.save"));
        assertThat(bindings.conflictFor(control(KeyEvent.VK_S), "file.save")).isEmpty();
        assertThat(bindings.conflictFor(plain(KeyEvent.VK_F9), "file.save")).isEmpty();
        preferences.addListener(changes::incrementAndGet);
        bindings.assign("edit.selectAll", control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("edit.selectAll")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("file.save")).isEmpty();
        assertThat(changes).hasValue(1);
    }

    @Test
    void resetGivesTheDefaultBackEvenWhenAnotherActionTookIt() {
        bindings.assign("edit.selectAll", control(KeyEvent.VK_S));
        bindings.reset("file.save");
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("edit.selectAll")).isEmpty();
    }

    @Test
    void resetAllForgetsEveryOverride() throws Exception {
        bindings.assign("file.save", control(KeyEvent.VK_G));
        bindings.assign("view.wireTool", plain(KeyEvent.VK_W));
        bindings.clear("edit.undo");
        preferences.addListener(changes::incrementAndGet);
        bindings.resetAll();
        assertThat(preferences.shortcutsNode().keys()).isEmpty();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("view.wireTool")).isEmpty();
        assertThat(bindings.strokeFor("edit.undo")).contains(control(KeyEvent.VK_Z));
        assertThat(changes).hasValue(1);
    }

    @Test
    void reservedStrokesAreRefused() {
        int shift = InputEvent.SHIFT_DOWN_MASK;
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_ESCAPE))).isTrue();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_ENTER))).isTrue();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_SPACE))).isTrue();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_TAB))).isTrue();
        assertThat(KeyBindings.isReserved(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, shift))).isTrue();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_BACK_SPACE))).isTrue();
        assertThat(KeyBindings.isReserved(control(KeyEvent.VK_PLUS))).isTrue();
        assertThat(KeyBindings.isReserved(control(KeyEvent.VK_ADD))).isTrue();
        assertThat(KeyBindings.isReserved(control(KeyEvent.VK_SUBTRACT))).isTrue();
        assertThat(KeyBindings.isReserved(control(KeyEvent.VK_NUMPAD0))).isTrue();
        assertThat(KeyBindings.isReserved(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, CONTROL | shift))).isTrue();
        assertThat(KeyBindings.isReserved(control(KeyEvent.VK_S))).isFalse();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_R))).isFalse();
        assertThat(KeyBindings.isReserved(plain(KeyEvent.VK_F2))).isFalse();
        assertThatThrownBy(() -> bindings.assign("file.save", plain(KeyEvent.VK_SPACE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void badStoredValuesFallBackToTheDefault() {
        preferences.shortcutsNode().put("file.save", "not a stroke");
        preferences.shortcutsNode().put("file.open", "pressed SPACE");
        preferences.shortcutsNode().put("unknown.id", "ctrl pressed K");
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("file.open")).contains(control(KeyEvent.VK_O));
        assertThat(bindings.conflictFor(control(KeyEvent.VK_K), "file.save")).isEmpty();
    }

    @Test
    void unknownIdsAreRefusedForWriting() {
        assertThatThrownBy(() -> bindings.assign("not.an.action", plain(KeyEvent.VK_F9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bindings.clear("not.an.action")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describeUsesTheNamesSwingGivesKeysAndModifiers() {
        assertThat(KeyBindings.describe(control(KeyEvent.VK_S)))
                .isEqualTo(KeyEvent.getModifiersExText(CONTROL) + "+" + KeyEvent.getKeyText(KeyEvent.VK_S));
        assertThat(KeyBindings.describe(plain(KeyEvent.VK_F2))).isEqualTo(KeyEvent.getKeyText(KeyEvent.VK_F2));
    }
}
