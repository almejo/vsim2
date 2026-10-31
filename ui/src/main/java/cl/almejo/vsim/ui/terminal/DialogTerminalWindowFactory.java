package cl.almejo.vsim.ui.terminal;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

/**
 * The Terminal window as a {@code JDialog} owned by the editor window and {@link Dialog.ModalityType#MODELESS} (spec
 * §9): it floats above its editor, minimizes and closes with it, has no taskbar entry and never blocks the circuit.
 * Without a display (headless runs) nothing is shown.
 */
final class DialogTerminalWindowFactory implements TerminalWindowFactory {

    @Override
    public TerminalWindowHandle show(Optional<Window> owner, String title, JComponent content,
                                     Optional<Rectangle> bounds, Runnable closedByUser) {
        if (GraphicsEnvironment.isHeadless()) {
            return new Hidden(bounds.orElseGet(Rectangle::new));
        }
        JDialog dialog = new JDialog(owner.orElse(null), title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closedByUser.run();
            }
        });
        if (bounds.isPresent()) {
            dialog.setBounds(bounds.get());
        } else {
            dialog.pack();
            dialog.setLocationRelativeTo(owner.orElse(null));
        }
        dialog.setVisible(true);
        return new Shown(dialog);
    }

    /** A dialog on screen. */
    private record Shown(JDialog dialog) implements TerminalWindowHandle {

        @Override
        public void setTitle(String title) {
            dialog.setTitle(title);
        }

        @Override
        public void toFront() {
            dialog.setVisible(true);
            dialog.toFront();
        }

        @Override
        public Rectangle bounds() {
            return dialog.getBounds();
        }

        @Override
        public void close() {
            dialog.dispose();
        }
    }

    /** No display: a window that is never shown keeps its bounds. */
    private record Hidden(Rectangle remembered) implements TerminalWindowHandle {

        @Override
        public void setTitle(String title) {
            // nothing is shown
        }

        @Override
        public void toFront() {
            // nothing is shown
        }

        @Override
        public Rectangle bounds() {
            return new Rectangle(remembered);
        }

        @Override
        public void close() {
            // nothing is shown
        }
    }
}
