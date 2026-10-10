package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutsTableModelTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;

    private final KeyBindings bindings = new KeyBindings(AppPreferences.inMemory());
    private ShortcutsTableModel model;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new ShortcutsTableModel(bindings);
    }

    private static KeyStroke control(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, CONTROL);
    }

    private List<String> column(int column) {
        List<String> values = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            values.add((String) model.getValueAt(row, column));
        }
        return values;
    }

    private List<String> groups() {
        List<String> names = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            if (model.isGroupRow(row)) {
                names.add((String) model.getValueAt(row, 0));
            }
        }
        return names;
    }

    private String shortcutOf(String id) {
        return (String) model.getValueAt(model.rowOf(id), 1);
    }

    @Test
    void groupsFollowTheMenusAndEveryActionShowsItsShortcut() {
        assertThat(model.getColumnCount()).isEqualTo(2);
        assertThat(model.getColumnName(0)).isEqualTo("Acción");
        assertThat(model.getColumnName(1)).isEqualTo("Atajo");
        assertThat(groups()).containsExactly("Archivo", "Editar", "Ver", "Simulación", "Ventana");
        assertThat(model.isGroupRow(0)).isTrue();
        assertThat(model.entryAt(0)).isEmpty();
        assertThat(model.getValueAt(1, 0)).isEqualTo("Nuevo");
        assertThat(model.entryAt(1)).hasValueSatisfying(entry -> assertThat(entry.id()).isEqualTo("file.new"));
        assertThat(shortcutOf("file.save")).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
        assertThat(shortcutOf("file.preferences")).isEmpty();
        assertThat(model.isCellEditable(1, 1)).isFalse();
    }

    @Test
    void searchKeepsMatchingActionsUnderTheirMenuIgnoringCaseAndAccents() {
        model.setFilter("guar");
        assertThat(column(0)).containsExactly("Archivo", "Guardar", "Guardar como…");
        model.setFilter("SELECCION");
        assertThat(column(0)).containsExactly("Editar", "Seleccionar todo", "Ver", "Selección");
        model.setFilter("zzz");
        assertThat(model.getRowCount()).isZero();
        assertThat(model.rowOf("file.save")).isEqualTo(-1);
        model.setFilter("");
        assertThat(model.rowOf("file.save")).isPositive();
    }

    @Test
    void capturingShowsThePromptAndAFreeStrokeIsAssigned() {
        model.beginCapture("file.save");
        assertThat(model.isCapturing()).isTrue();
        assertThat(shortcutOf("file.save")).isEqualTo("Presiona la combinación…");
        assertThat(model.offer(control(KeyEvent.VK_G))).isEqualTo(ShortcutsTableModel.Outcome.ASSIGNED);
        assertThat(model.isCapturing()).isFalse();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_G));
        assertThat(shortcutOf("file.save")).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_G)));
        assertThat(model.message()).isEmpty();
    }

    @Test
    void cancellingTheCaptureChangesNothing() {
        model.beginCapture("file.save");
        model.cancelCapture();
        assertThat(model.isCapturing()).isFalse();
        assertThat(shortcutOf("file.save")).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
    }

    @Test
    void aTakenStrokeAsksBeforeReassigning() {
        model.beginCapture("edit.selectAll");
        assertThat(model.offer(control(KeyEvent.VK_S))).isEqualTo(ShortcutsTableModel.Outcome.CONFLICT);
        assertThat(model.message())
                .isEqualTo(Messages.format("shortcuts.conflict", KeyBindings.describe(control(KeyEvent.VK_S)),
                        "Guardar"))
                .contains("ya lo usa «Guardar».");
        assertThat(model.pendingConflict()).hasValueSatisfying(entry ->
                assertThat(entry.id()).isEqualTo("file.save"));
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        model.confirmReassign();
        assertThat(bindings.strokeFor("edit.selectAll")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("file.save")).isEmpty();
        assertThat(model.pendingConflict()).isEmpty();
        assertThat(model.message()).isEmpty();
    }

    @Test
    void cancellingAConflictChangesNothing() {
        model.beginCapture("edit.selectAll");
        model.offer(control(KeyEvent.VK_S));
        model.cancelReassign();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        assertThat(bindings.strokeFor("edit.selectAll")).contains(control(KeyEvent.VK_A));
        assertThat(model.pendingConflict()).isEmpty();
        assertThat(model.message()).isEmpty();
    }

    @Test
    void reservedStrokesAreRefusedWithAMessage() {
        for (KeyStroke reserved : List.of(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), control(KeyEvent.VK_PLUS))) {
            model.beginCapture("file.save");
            assertThat(model.offer(reserved)).isEqualTo(ShortcutsTableModel.Outcome.REFUSED);
            assertThat(model.message())
                    .isEqualTo(Messages.format("shortcuts.reserved", KeyBindings.describe(reserved)));
        }
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void clearResetAndResetAll() {
        model.clear("file.save");
        assertThat(shortcutOf("file.save")).isEmpty();
        model.reset("file.save");
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
        bindings.assign("file.open", control(KeyEvent.VK_K));
        bindings.clear("edit.undo");
        model.resetAll();
        assertThat(bindings.strokeFor("file.open")).contains(control(KeyEvent.VK_O));
        assertThat(bindings.strokeFor("edit.undo")).contains(control(KeyEvent.VK_Z));
    }

    @Test
    void searchAndTheRowButtonsCancelAnInProgressCapture() {
        model.beginCapture("file.save");
        model.setFilter("guar");
        assertThat(model.isCapturing()).isFalse();
        model.setFilter("");

        model.beginCapture("file.save");
        model.clear("file.open");
        assertThat(model.isCapturing()).isFalse();

        model.beginCapture("file.save");
        model.reset("file.open");
        assertThat(model.isCapturing()).isFalse();

        model.beginCapture("file.save");
        model.resetAll();
        assertThat(model.isCapturing()).isFalse();
        assertThat(shortcutOf("file.save")).isEqualTo(KeyBindings.describe(control(KeyEvent.VK_S)));
    }

    @Test
    void aStrokeOfferedAfterTheCaptureWasCancelledIsRejected() {
        model.beginCapture("file.save");
        model.setFilter("x");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> model.offer(control(KeyEvent.VK_G)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theEditorNeverAssignsAReservedStrokeEvenAfterAConflictQuestion() {
        model.beginCapture("file.save");
        model.offer(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        model.confirmReassign();
        assertThat(bindings.strokeFor("file.save")).contains(control(KeyEvent.VK_S));
    }

    @Test
    void groupNamesAreTheMenuTitles() {
        assertThat(ShortcutsTableModel.groupName(cl.almejo.vsim.ui.window.MenuId.SIMULATION)).isEqualTo("Simulación");
    }
}
