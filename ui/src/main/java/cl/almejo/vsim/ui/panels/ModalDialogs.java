package cl.almejo.vsim.ui.panels;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;

/**
 * The modal dialogs whose close box does what their «Cancelar» does (the groups window, the memory editor, the
 * Karnaugh wizard): the dialog never closes by itself, the close box asks the owner, which cancels and disposes it.
 */
public final class ModalDialogs {

    private ModalDialogs() {
    }

    /**
     * A dialog titled {@code title} over {@code parent}'s window (none for a null parent), {@code modality} modal,
     * whose close box runs {@code closeBox}.
     */
    public static JDialog over(Component parent, String title, Dialog.ModalityType modality, Runnable closeBox) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title,
                modality);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(WindowEvents.onClosing(closeBox));
        return dialog;
    }
}
