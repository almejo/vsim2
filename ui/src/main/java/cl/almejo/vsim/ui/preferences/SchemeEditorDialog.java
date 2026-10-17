package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JColorChooser;
import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Component;
import java.util.Optional;

/**
 * The modal «Personalizar esquema de colores» dialog around a {@link SchemeEditorPanel}, and the real colour chooser.
 * {@link PreferencesForm} takes both as injectable functions, so tests never show them.
 */
public final class SchemeEditorDialog {

    /** Shows the editor; true when the user pressed «Guardar». */
    @FunctionalInterface
    public interface Opener {
        boolean open(Component parent, SchemeEditorPanel panel);
    }

    private SchemeEditorDialog() {
    }

    public static boolean open(Component parent, SchemeEditorPanel panel) {
        Object[] options = {Messages.text("schemeEditor.save"), Messages.text("schemeEditor.cancel")};
        int answer = JOptionPane.showOptionDialog(parent, panel, Messages.text("schemeEditor.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        return answer == 0;
    }

    /** Swing's colour chooser, with the transparency controls (the schemes keep alpha). */
    public static Optional<Color> chooseColor(Component parent, String title, Color initial) {
        return Optional.ofNullable(JColorChooser.showDialog(parent, title, initial, true));
    }
}
