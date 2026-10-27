package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Asks before a sticky note is closed with its ✕ or «Despegar…» (spec §3 «Close»): true unsticks it. The dialog
 * offers «Cancelar» / «Despegar», with «Despegar» as the default answer and Escape cancelling. Tests inject a fake so
 * no dialog is shown.
 */
@FunctionalInterface
public interface StickyCloseConfirmation {

    /** Characters of the note's first line the question quotes before cutting it with «…». */
    int QUOTED_LENGTH = 40;

    /** True to close the note whose summary line is {@code firstLine} (possibly empty). */
    boolean confirm(Component parent, String firstLine);

    /** The real dialog. */
    static StickyCloseConfirmation dialog() {
        return (parent, firstLine) -> {
            String unstick = Messages.text("stickyNotes.close.confirm");
            String cancel = Messages.text("stickyNotes.close.cancel");
            int answer = JOptionPane.showOptionDialog(parent, question(firstLine),
                    Messages.text("stickyNotes.close.title"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, new Object[] {cancel, unstick}, unstick);
            return answer == 1;
        };
    }

    /** The question for a note whose summary line is {@code firstLine}: quoted and cut, or without a quote. */
    static String question(String firstLine) {
        String line = firstLine.strip();
        if (line.isEmpty()) {
            return Messages.text("stickyNotes.close.messageUntitled");
        }
        String quoted = line.length() > QUOTED_LENGTH ? line.substring(0, QUOTED_LENGTH).stripTrailing() + "…" : line;
        return Messages.format("stickyNotes.close.message", quoted);
    }
}
