package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JOptionPane;
import java.awt.Component;

/** The Swing answers: the connections sheet, and the message boxes of the library and stale-copy cases. */
public final class SwingAppearancePrompts implements AppearancePrompts {
    @Override public boolean confirmConnections(Component owner, ConnectionsQuestion question) {
        return ConnectionsSheet.ask(owner, question);
    }

    @Override public boolean retryLibraryWrite(Component owner, boolean applied) {
        Object[] choices = {Messages.text("shape.retry"), Messages.text("shape.close")};
        return JOptionPane.showOptionDialog(owner, Messages.text(applied ? "shape.library.failedApplied"
                : "shape.library.failed"), Messages.text("shape.library.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, choices, choices[0]) == 0;
    }

    @Override public boolean reopenStale(Component owner) {
        Object[] choices = {Messages.text("shape.stale.reopen"), Messages.text("shape.stale.discard")};
        return JOptionPane.showOptionDialog(owner, Messages.text("shape.stale.message"), Messages.text("shape.stale.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]) == 0;
    }
}
