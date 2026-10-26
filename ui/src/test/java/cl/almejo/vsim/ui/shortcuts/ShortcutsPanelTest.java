package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutsPanelTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;

    private final KeyBindings bindings = new KeyBindings(AppPreferences.inMemory());
    private ShortcutsPanel panel;

    @BeforeEach
    void create() {
        panel = new ShortcutsPanel(bindings);
    }

    private static KeyStroke control(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, CONTROL);
    }

    private int select(String id) {
        int row = panel.model().rowOf(id);
        panel.table().setRowSelectionInterval(row, row);
        return row;
    }

    private void press(int keyCode, int modifiers) {
        panel.capture().keyPressed(new KeyEvent(panel.table(), KeyEvent.KEY_PRESSED, 0, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void changeWaitsForAKeyInTheCellAndAssignsIt() {
        int row = select("file.save");
        panel.changeButton().doClick(0);
        assertThat(panel.table().getValueAt(row, 1)).isEqualTo("Presiona la combinación…");
        press(KeyEvent.VK_J, CONTROL);
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_J));
        assertThat(panel.table().getValueAt(row, 1)).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_J)));
        assertThat(panel.table().getSelectedRow()).isEqualTo(row);
    }

    @Test
    void escapeCancelsTheCapture() {
        int row = select("file.save");
        panel.changeButton().doClick(0);
        press(KeyEvent.VK_ESCAPE, 0);
        assertThat(panel.table().getValueAt(row, 1)).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void aTakenStrokeIsReassignedOnlyAfterReasignar() {
        select("edit.selectAll");
        assertThat(panel.reassignButton().isVisible()).isFalse();
        panel.changeButton().doClick(0);
        press(KeyEvent.VK_S, CONTROL);
        assertThat(panel.messageLabel().getText()).contains("«Guardar»");
        assertThat(panel.reassignButton().isVisible()).isTrue();
        assertThat(panel.cancelButton().isVisible()).isTrue();
        assertThat(panel.changeButton().isEnabled()).isFalse();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        panel.reassignButton().doClick(0);
        assertThat(bindings.strokeFor("edit.selectAll")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("file.save")).isEmpty();
        assertThat(panel.reassignButton().isVisible()).isFalse();
        assertThat(panel.messageLabel().getText()).isBlank();
    }

    @Test
    void aReservedStrokeShowsWhyItWasRefused() {
        select("file.save");
        panel.changeButton().doClick(0);
        press(KeyEvent.VK_SPACE, 0);
        assertThat(panel.messageLabel().getText())
                .isEqualTo(Messages.format("shortcuts.reserved", KeyBindings.describe(KeyStroke.getKeyStroke(
                        KeyEvent.VK_SPACE, 0))));
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void clearResetAndResetAllActOnTheSelectedAction() {
        select("file.save");
        panel.clearButton().doClick(0);
        assertThat(bindings.strokeFor("file.save")).isEmpty();
        panel.resetButton().doClick(0);
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        bindings.assign("file.open", control(KeyEvent.VK_K));
        panel.resetAllButton().doClick(0);
        assertThat(bindings.strokeFor("file.open")).contains(control(KeyEvent.VK_O));
    }

    @Test
    void theSearchFieldFiltersAndAMenuRowOffersNoChange() {
        panel.searchField().setText("guardar");
        assertThat(panel.table().getRowCount()).isEqualTo(3);
        panel.table().setRowSelectionInterval(0, 0);
        assertThat(panel.changeButton().isEnabled()).isFalse();
        assertThat(panel.clearButton().isEnabled()).isFalse();
        panel.table().setRowSelectionInterval(1, 1);
        assertThat(panel.changeButton().isEnabled()).isTrue();
    }

    private void assertCaptureCancelled(int row) {
        assertThat(panel.capture().isActive()).isFalse();
        assertThat(panel.model().isCapturing()).isFalse();
        assertThat(panel.table().getValueAt(row, 1)).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
        // a later key must not reach the model (it would throw IllegalStateException)
        press(KeyEvent.VK_J, CONTROL);
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void losingFocusCancelsTheCapture() {
        int row = select("file.save");
        panel.changeButton().doClick(0);
        for (java.awt.event.FocusListener listener : panel.table().getFocusListeners()) {
            listener.focusLost(new java.awt.event.FocusEvent(panel.table(), java.awt.event.FocusEvent.FOCUS_LOST));
        }
        assertCaptureCancelled(row);
    }

    @Test
    void changingTheSelectionCancelsTheCapture() {
        int row = select("file.save");
        panel.changeButton().doClick(0);
        select("file.open");
        assertCaptureCancelled(row);
    }

    @Test
    void searchingCancelsTheCapture() {
        select("file.save");
        panel.changeButton().doClick(0);
        panel.searchField().setText("guardar");
        assertThat(panel.capture().isActive()).isFalse();
        assertThat(panel.model().isCapturing()).isFalse();
        press(KeyEvent.VK_J, CONTROL);
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void clearResetAndResetAllCancelTheCapture() {
        for (int button = 0; button < 3; button++) {
            int row = select("file.save");
            panel.changeButton().doClick(0);
            switch (button) {
                case 0 -> panel.clearButton().doClick(0);
                case 1 -> panel.resetButton().doClick(0);
                default -> panel.resetAllButton().doClick(0);
            }
            assertThat(panel.capture().isActive()).isFalse();
            assertThat(panel.model().isCapturing()).isFalse();
            press(KeyEvent.VK_J, CONTROL);
            assertThat(bindings.strokeFor("file.save")).isNotEqualTo(java.util.Optional.of(control(KeyEvent.VK_J)));
            panel.resetButton().doClick(0);
            assertThat(panel.table().getValueAt(row, 1)).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
        }
    }
}
