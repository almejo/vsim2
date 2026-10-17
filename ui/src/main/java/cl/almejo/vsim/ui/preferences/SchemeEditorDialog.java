package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
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

    /** The share of the screen the editor's content may take before it scrolls. */
    static final double SCREEN_SHARE = 0.85;

    private SchemeEditorDialog() {
    }

    /**
     * Shows the editor in a resizable dialog; its content scrolls when the screen is too small for the 20 colours and
     * the preview (a low screen, a large UI scale).
     */
    public static boolean open(Component parent, SchemeEditorPanel panel) {
        Object[] options = {Messages.text("schemeEditor.save"), Messages.text("schemeEditor.cancel")};
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        JOptionPane pane = new JOptionPane(scrollable(panel, screen.getSize()), JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
        JDialog dialog = pane.createDialog(parent, Messages.text("schemeEditor.title"));
        dialog.setResizable(true);
        dialog.setVisible(true);
        dialog.dispose();
        return options[0].equals(pane.getValue());
    }

    /**
     * {@code panel} in a scroll pane as large as the panel wants, but at most {@link #SCREEN_SHARE} of {@code screen}
     * (the room left for the buttons and the title bar of the dialog).
     */
    static JScrollPane scrollable(SchemeEditorPanel panel, Dimension screen) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension wanted = panel.getPreferredSize();
        int scrollBar = scroll.getVerticalScrollBar().getPreferredSize().width;
        int maximumWidth = (int) (screen.width * SCREEN_SHARE);
        int maximumHeight = (int) (screen.height * SCREEN_SHARE);
        int width = Math.min(wanted.width + (wanted.height > maximumHeight ? scrollBar : 0), maximumWidth);
        int height = Math.min(wanted.height + (wanted.width > maximumWidth ? scrollBar : 0), maximumHeight);
        scroll.setPreferredSize(new Dimension(width, height));
        return scroll;
    }

    /** Swing's colour chooser, with the transparency controls (the schemes keep alpha). */
    public static Optional<Color> chooseColor(Component parent, String title, Color initial) {
        return Optional.ofNullable(JColorChooser.showDialog(parent, title, initial, true));
    }
}
