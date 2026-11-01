package cl.almejo.vsim.ui.terminal;

import javax.swing.JComponent;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Optional;

/**
 * Shows a Terminal window: owned by the editor window {@code owner} (it floats above it, minimizes and closes with it,
 * never blocks it), at {@code bounds} when they were remembered. {@code closedByUser} runs when the user closes it.
 * Injectable so tests never show a window.
 */
@FunctionalInterface
public interface TerminalWindowFactory {

    TerminalWindowHandle show(Optional<Window> owner, String title, JComponent content, Optional<Rectangle> bounds,
                              Runnable closedByUser);
}
