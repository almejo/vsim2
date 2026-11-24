package cl.almejo.vsim.ui.panels;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.KeyEvent;

/**
 * The modal dialogs whose close box and Esc do what their «Cancelar» does (the groups window, the memory editor, the
 * Karnaugh wizard, the PDF export): the dialog never closes by itself, the close box and Esc ask the owner, which
 * cancels and disposes it. A dialog with no «Cancelar» of its own that Esc simply closes uses {@link #closeOnEscape}.
 */
public final class ModalDialogs {

    private static final KeyStroke ESCAPE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

    private ModalDialogs() {
    }

    /**
     * A dialog titled {@code title} over {@code parent}'s window (none for a null parent), {@code modality} modal,
     * whose close box and Esc run {@code cancel}.
     */
    public static JDialog over(Component parent, String title, Dialog.ModalityType modality, Runnable cancel) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title,
                modality);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(WindowEvents.onClosing(cancel));
        closeOnEscape(dialog.getRootPane(), cancel);
        return dialog;
    }

    /** Esc anywhere in the window of {@code rootPane} runs {@code action}. */
    public static void closeOnEscape(JRootPane rootPane, Runnable action) {
        rootPane.registerKeyboardAction(event -> action.run(), ESCAPE, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
