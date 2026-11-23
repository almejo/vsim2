package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.List;

/**
 * How the test mode asks the user what to do with its draft (leaving the tab, closing it, saving, replacing a machine
 * changed elsewhere): a dialog with one button per option. Tests answer with a script instead.
 */
@FunctionalInterface
interface MachineTestQuestions {

    /** Swing's option dialog over {@code parent}, its first option the default one. */
    MachineTestQuestions DIALOG = (parent, message, options) -> JOptionPane.showOptionDialog(parent, message,
            Messages.text("machine.test.question.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options.toArray(), options.getFirst());

    /**
     * Asks {@code message} with {@code options} as buttons; returns the index of the option chosen, or a negative
     * number when the question was closed without choosing (which means the last option, «Cancelar» or «Seguir
     * probando»).
     */
    int ask(Component parent, String message, List<String> options);
}
