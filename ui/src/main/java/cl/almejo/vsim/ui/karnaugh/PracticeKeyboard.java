package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.ui.actions.Actions;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/**
 * The practice keyboard of a {@link KarnaughMapView}: binds the keys and moves the cursor and the stretched rectangle,
 * which stay in the view.
 */
final class PracticeKeyboard {

    private final KarnaughMapView view;

    PracticeKeyboard(KarnaughMapView view) {
        this.view = view;
    }

    void bindKeys() {
        bind("practiceUp", () -> moveCursor(-1, 0), KeyEvent.VK_UP, 0);
        bind("practiceDown", () -> moveCursor(1, 0), KeyEvent.VK_DOWN, 0);
        bind("practiceLeft", () -> moveCursor(0, -1), KeyEvent.VK_LEFT, 0);
        bind("practiceRight", () -> moveCursor(0, 1), KeyEvent.VK_RIGHT, 0);
        bind("practiceStretchUp", () -> stretchRectangle(-1, 0), KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchDown", () -> stretchRectangle(1, 0), KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchLeft", () -> stretchRectangle(0, -1), KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
        bind("practiceStretchRight", () -> stretchRectangle(0, 1), KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        // Enter adds only while the keyboard is in use (the cursor shows); after a click it reaches «Siguiente».
        bindKeys("practiceAdd", this::addKeyboardRectangle, () -> view.keyboardActive || view.stretch != null,
                KeyEvent.VK_ENTER);
        bindKeys("practiceDelete", this::deleteSelected, () -> view.model.practice().selected().isPresent(),
                KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE);
        bindKeys("practiceCancel", this::cancelKeyboardRectangle,
                () -> view.stretch != null || view.model.practice().selected().isPresent(), KeyEvent.VK_ESCAPE);
    }

    private void bind(String name, Runnable action, int keyCode, int modifiers) {
        view.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        putAction(name, action, () -> true);
    }

    /** Binds {@code keyCodes} (no modifiers) to {@code action}, on only while practising and {@code condition}. */
    private void bindKeys(String name, Runnable action, BooleanSupplier condition, int... keyCodes) {
        for (int keyCode : keyCodes) {
            view.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        }
        putAction(name, action, condition);
    }

    /**
     * Off outside the practice mode (and when there is nothing to do), so the keys reach the step and the window as
     * before: Escape still lets a pinned group go or closes the wizard.
     */
    private void putAction(String name, Runnable action, BooleanSupplier condition) {
        BooleanSupplier active = () -> view.editable() && condition.getAsBoolean();
        view.getActionMap().put(name, Actions.unnamed(active, () -> {
            if (active.getAsBoolean()) {
                view.keyboardActive = true;
                action.run();
                view.updateAnimation();
                view.repaint();
            }
        }));
    }

    private void moveCursor(int rowDelta, int columnDelta) {
        fitCursor();
        KarnaughLayout layout = view.karnaughLayout();
        view.stretch = null;
        view.cursor = new KarnaughLayout.GridCell(Math.floorMod(view.cursor.row() + rowDelta, layout.rows()),
                Math.floorMod(view.cursor.column() + columnDelta, layout.columns()));
    }

    private void stretchRectangle(int rowDelta, int columnDelta) {
        fitCursor();
        KarnaughLayout layout = view.karnaughLayout();
        if (view.stretch == null) {
            view.stretch = new int[2];
        }
        view.stretch[0] = Math.clamp(view.stretch[0] + rowDelta, 1 - layout.rows(), layout.rows() - 1);
        view.stretch[1] = Math.clamp(view.stretch[1] + columnDelta, 1 - layout.columns(), layout.columns() - 1);
    }

    /** Enter: adds the stretched rectangle, or the cursor's cell when none is stretched. */
    private void addKeyboardRectangle() {
        DrawnGroup group = view.pendingRectangle()
                .orElse(view.karnaughLayout().drawn(view.cursor.row(), view.cursor.column(), 1, 1));
        view.stretch = null;
        view.addGroup(group);
    }

    /** Esc: drops the stretched rectangle, else lets the selected group go (only then Esc reaches the window). */
    private void cancelKeyboardRectangle() {
        if (view.stretch != null) {
            view.stretch = null;
        } else {
            view.model.practice().clearSelection();
        }
    }

    /**
     * Fewer inputs make a smaller map while this view stays: a cursor left outside it goes back to the first cell and
     * the stretched rectangle goes.
     */
    void fitCursor() {
        KarnaughLayout layout = view.karnaughLayout();
        if (view.cursor.row() >= layout.rows() || view.cursor.column() >= layout.columns()) {
            view.cursor = new KarnaughLayout.GridCell(0, 0);
            view.stretch = null;
        }
    }

    private void deleteSelected() {
        OptionalInt selected = view.model.practice().selected();
        if (selected.isPresent()) {
            view.model.practice().remove(view.output, view.model.options().form(), selected.getAsInt());
        }
    }
}
