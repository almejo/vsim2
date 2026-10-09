package cl.almejo.vsim.ui.actions;

import javax.swing.JOptionPane;
import java.util.Optional;

/** Asks the user for one line of text. Tests use a fake; the application uses {@link #DIALOG}. */
@FunctionalInterface
public interface TextPrompt {

    /** Shows {@code message} with a field holding {@code initialText}; empty when the user cancels. */
    Optional<String> ask(java.awt.Component parent, String title, String message, String initialText);

    TextPrompt DIALOG = (parent, title, message, initialText) -> Optional.ofNullable((String) JOptionPane
            .showInputDialog(parent, message, title, JOptionPane.PLAIN_MESSAGE, null, null, initialText));
}
