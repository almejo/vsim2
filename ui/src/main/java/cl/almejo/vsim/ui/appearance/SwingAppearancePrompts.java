package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.List;

/** Swing connections sheet, keeping foreign contacts separate from pins left for manual connection. */
public final class SwingAppearancePrompts implements AppearancePrompts {
    @Override public boolean confirmConnections(Component owner, List<String> disconnected, List<String> kept,
                                                 boolean blockedInstance) {
        String text = (disconnected.isEmpty() ? "" : Messages.text("shape.connections.disconnected")
                + "\n" + String.join("\n", disconnected) + "\n\n")
                + (kept.isEmpty() ? "" : Messages.text("shape.connections.kept") + "\n" + String.join("\n", kept));
        if (blockedInstance) {
            JOptionPane.showMessageDialog(owner, text, Messages.text("shape.connections.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Object[] choices = {Messages.text("shape.connections.apply"), Messages.text("shape.connections.back")};
        return JOptionPane.showOptionDialog(owner, text, Messages.text("shape.connections.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]) == 0;
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
