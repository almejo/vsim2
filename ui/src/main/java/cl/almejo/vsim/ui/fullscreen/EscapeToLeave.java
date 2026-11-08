package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JComboBox;
import javax.swing.JRootPane;
import javax.swing.MenuSelectionManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * Esc leaves presentation mode only when nothing else in the window wants it (agreed 2026-11-08): an open menu or
 * popup, the list of a combo box (the speed selector, which closes), a tool at work on the canvas (placing a part, a
 * wire being drawn, a guide being pulled), something shown over a pane that closes on Esc (the «Mirar adentro»
 * magnifier, {@link CircuitCanvas#addEscapeClaim}), a field or list that uses Esc (the find bar, the inline editor,
 * the palette's tree) and a dialog all get it first. On a canvas with
 * nothing going on, Esc leaves without clearing the selection. With a part that has the keyboard (the Terminal, a
 * Teclado) Esc still leaves, and the part's capture ends as it always does; Mayús+Esc still types the ESC byte.
 * <p>
 * Registered with the focus manager only while the mode is on: as a dispatcher it sees the key before the canvas
 * (the select tool would otherwise take Esc to clear the selection), as a post-processor after everyone else had
 * their turn. EDT only.
 */
final class EscapeToLeave implements KeyEventDispatcher, KeyEventPostProcessor {

    private static final int ANY_MODIFIER = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private final JRootPane rootPane;
    private final Runnable leave;
    /**
     * The Esc that found a menu open or a pane's claim when it was dispatched: it was theirs, even if what wanted it
     * closed meanwhile without consuming it.
     */
    private KeyEvent takenEscape;

    EscapeToLeave(JRootPane rootPane, Runnable leave) {
        this.rootPane = Objects.requireNonNull(rootPane, "rootPane");
        this.leave = Objects.requireNonNull(leave, "leave");
    }

    /** Before anyone: on an idle canvas Esc leaves here; with a captured keyboard it leaves and goes on to the part. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isPlainEscapePress(event) || !isInThisWindow(event.getComponent())) {
            return false;
        }
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            takenEscape = event;
            return false;
        }
        if (closeAnOpenComboList(rootPane)) {
            event.consume();
            return true;
        }
        if (!(event.getComponent() instanceof CircuitCanvas canvas)) {
            return false; // decided after the others, in postProcessKeyEvent
        }
        if (hasCapturedKeyboard(canvas)) {
            leave.run();
            return false; // the part's capture still ends with this Esc
        }
        if (canvas.isEscapeClaimed()) {
            takenEscape = event;
            return false;
        }
        if (wantsEscape(canvas)) {
            return false;
        }
        event.consume();
        leave.run();
        return true;
    }

    /** After everyone: an Esc nobody used, in this window and with no menu open when it came, leaves. */
    @Override
    public boolean postProcessKeyEvent(KeyEvent event) {
        if (!isPlainEscapePress(event) || event.isConsumed() || event == takenEscape
                || !isInThisWindow(event.getComponent())) {
            return false;
        }
        event.consume();
        leave.run();
        return true;
    }

    private static boolean isPlainEscapePress(KeyEvent event) {
        return event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE
                && (event.getModifiersEx() & ANY_MODIFIER) == 0;
    }

    /** In this window's root pane, or the frame itself when nothing in it has the focus; never a dialog. */
    private boolean isInThisWindow(Component component) {
        if (component instanceof RootPaneContainer container) {
            return container.getRootPane() == rootPane;
        }
        return component != null && SwingUtilities.getRootPane(component) == rootPane;
    }

    /**
     * Closes the list of a combo box of the window if one shows (its popup is not on the menu path, and opened with
     * the mouse it may not have the focus to close on Esc itself). True when it closed one.
     */
    private static boolean closeAnOpenComboList(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComboBox<?> combo && combo.isPopupVisible()) {
                combo.setPopupVisible(false);
                return true;
            }
            if (child instanceof Container inner && closeAnOpenComboList(inner)) {
                return true;
            }
        }
        return false;
    }

    /** A tool at work, or a gesture on an edge handle: Esc is theirs. */
    private static boolean wantsEscape(CircuitCanvas canvas) {
        return canvas.tool() != canvas.defaultTool() || canvas.tool().holdsGesture() || canvas.isEdgeGestureActive();
    }

    private static boolean hasCapturedKeyboard(CircuitCanvas canvas) {
        return SwingUtilities.getAncestorOfClass(EditorView.class, canvas) instanceof EditorView view
                && view.keyboardCapture().captured().isPresent();
    }
}
