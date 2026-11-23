package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.actions.Actions;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * The test table's keys, as the Karnaugh practice binds them ({@code PracticeKeyboard}): ← and → choose the column
 * (the diagram follows), ↑ and ↓ move between the editable rows, Mayús with an arrow stretches a block, 0 and 1 fill
 * the block, Espacio changes each of its cells as a click would, and Supr or Retroceso set its «esperado» cells to
 * «no importa», and Insert adds a column at the end (the «+» after the last column, for those who do not point).
 * Each edit is one step of the draft.
 */
final class MachineTestKeyboard {

    private final MachineTestTableGrid grid;
    private final MachineTestTable table;

    MachineTestKeyboard(MachineTestTableGrid grid, MachineTestTable table) {
        this.grid = grid;
        this.table = table;
    }

    void bindKeys() {
        bind("testLeft", () -> grid.moveTo(grid.model().chosenColumn() - 1, false), KeyEvent.VK_LEFT, 0);
        bind("testRight", () -> grid.moveTo(grid.model().chosenColumn() + 1, false), KeyEvent.VK_RIGHT, 0);
        bind("testUp", () -> grid.moveRow(-1, false), KeyEvent.VK_UP, 0);
        bind("testDown", () -> grid.moveRow(1, false), KeyEvent.VK_DOWN, 0);
        bind("testStretchLeft", () -> grid.moveTo(grid.model().chosenColumn() - 1, true), KeyEvent.VK_LEFT,
                InputEvent.SHIFT_DOWN_MASK);
        bind("testStretchRight", () -> grid.moveTo(grid.model().chosenColumn() + 1, true), KeyEvent.VK_RIGHT,
                InputEvent.SHIFT_DOWN_MASK);
        bind("testStretchUp", () -> grid.moveRow(-1, true), KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK);
        bind("testStretchDown", () -> grid.moveRow(1, true), KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        bind("testZero", () -> table.fill(grid.block(), false), KeyEvent.VK_0, 0);
        bind("testZero", () -> table.fill(grid.block(), false), KeyEvent.VK_NUMPAD0, 0);
        bind("testOne", () -> table.fill(grid.block(), true), KeyEvent.VK_1, 0);
        bind("testOne", () -> table.fill(grid.block(), true), KeyEvent.VK_NUMPAD1, 0);
        bind("testAdvance", () -> table.advance(grid.block()), KeyEvent.VK_SPACE, 0);
        bind("testNothingExpected", () -> table.clearExpected(grid.block()), KeyEvent.VK_DELETE, 0);
        bind("testNothingExpected", () -> table.clearExpected(grid.block()), KeyEvent.VK_BACK_SPACE, 0);
        bind("testAddColumn", table::addColumn, KeyEvent.VK_INSERT, 0);
    }

    private void bind(String name, Runnable action, int keyCode, int modifiers) {
        grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        grid.getActionMap().put(name, Actions.unnamed(action));
    }
}
