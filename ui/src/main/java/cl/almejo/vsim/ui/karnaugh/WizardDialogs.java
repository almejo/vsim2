package cl.almejo.vsim.ui.karnaugh;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * The dialog of the wizard and of the table import. Every way of closing it (Escape, the window's close box, and the
 * panel's own «Cerrar») goes through the panel's {@code requestClose}, which cancels work in progress before the
 * dialog goes away. It is document modal: it blocks the circuit window it came from, but a «Circuito nuevo» window
 * opened from it is a window of its own and can be used, and come to the front, even while the dialog is still open
 * (a successful «Crear» closes it; one that failed leaves it open to show why).
 */
final class WizardDialogs {

    private WizardDialogs() {
    }

    /** A dialog over {@code parent}'s window showing {@code content}; closing it runs {@code requestClose}. */
    static JDialog create(Component parent, String title, JComponent content, Runnable requestClose) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(closeBox(requestClose));
        dialog.setContentPane(content);
        bindEscape(dialog.getRootPane(), requestClose);
        return dialog;
    }

    /** The window's close box runs {@code requestClose} (the dialog does nothing on close by itself). */
    static WindowListener closeBox(Runnable requestClose) {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestClose.run();
            }
        };
    }

    /** Escape anywhere in the window runs {@code requestClose}. */
    static void bindEscape(JRootPane rootPane, Runnable requestClose) {
        rootPane.registerKeyboardAction(event -> requestClose.run(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
