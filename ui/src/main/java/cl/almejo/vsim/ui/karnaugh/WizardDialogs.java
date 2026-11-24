package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.panels.DialogButtons;
import cl.almejo.vsim.ui.panels.ModalDialogs;

import javax.swing.JComponent;
import javax.swing.JDialog;
import java.awt.Component;
import java.awt.Dialog;

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
        JDialog dialog = ModalDialogs.over(parent, title, Dialog.ModalityType.DOCUMENT_MODAL, requestClose);
        dialog.setContentPane(DialogButtons.scrolling(content)); // a dialog cut to the screen scrolls, never cuts
        return dialog;
    }
}
