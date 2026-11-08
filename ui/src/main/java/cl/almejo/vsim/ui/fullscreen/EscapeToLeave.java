package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JRootPane;
import javax.swing.MenuSelectionManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * Esc leaves presentation mode only when nothing else in the window wants it (agreed 2026-11-08): an open menu or
 * popup, a tool at work on the canvas (placing a part, a wire being drawn, a guide being pulled), a field or list
 * that uses Esc (the find bar, the inline editor, the palette's tree) and a dialog all get it first. On a canvas with
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
    /** The Esc that found a menu open when it was dispatched: the menu took it, even if it closed meanwhile. */
    private KeyEvent menuEscape;

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
            menuEscape = event;
            return false;
        }
        if (!(event.getComponent() instanceof CircuitCanvas canvas)) {
            return false; // decided after the others, in postProcessKeyEvent
        }
        if (hasCapturedKeyboard(canvas)) {
            leave.run();
            return false; // the part's capture still ends with this Esc
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
        if (!isPlainEscapePress(event) || event.isConsumed() || event == menuEscape
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

    /** A tool at work, or a gesture on an edge handle: Esc is theirs. */
    private static boolean wantsEscape(CircuitCanvas canvas) {
        return canvas.tool() != canvas.defaultTool() || canvas.tool().holdsGesture() || canvas.isEdgeGestureActive();
    }

    private static boolean hasCapturedKeyboard(CircuitCanvas canvas) {
        return SwingUtilities.getAncestorOfClass(EditorView.class, canvas) instanceof EditorView view
                && view.keyboardCapture().captured().isPresent();
    }
}
