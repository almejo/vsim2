package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.inline.ProblemBadge;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;

import javax.swing.AbstractAction;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * The text field of the find bar and of the component tree's filter: a magnifier, a placeholder, no circuit
 * fragments pasted in, and Ctrl+Z / Ctrl+Shift+Z kept from reaching the circuit's undo while typing (like the
 * palette's search box).
 */
public final class SearchField extends JTextField {

    public SearchField(String placeholder) {
        putClientProperty("JTextField.placeholderText", placeholder);
        putClientProperty("JTextField.leadingIcon", Icons.get(FindIcons.FIND, Icons.MENU_SIZE));
        FragmentRefusingTransferHandler.install(this);
        swallowUndoKeystrokes();
    }

    /** Runs {@code listener} after every change of the text. */
    public void onTextChange(Runnable listener) {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                listener.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                listener.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                listener.run();
            }
        });
    }

    /** How much of the theme's error red tints the background of a field in error. */
    private static final double ERROR_TINT = 0.12;

    /** FlatLaf's error outline and a red-tinted background, or the normal look. */
    public void showError(boolean error) {
        putClientProperty("JComponent.outline", error ? "error" : null);
        repaint();
    }

    /** In error, the theme's background tinted with its error red, both read now (the theme may change). */
    @Override
    public Color getBackground() {
        Color background = super.getBackground();
        if (background == null || !showsError()) {
            return background;
        }
        Color red = ProblemBadge.themeErrorColor();
        return new Color(mix(background.getRed(), red.getRed()), mix(background.getGreen(), red.getGreen()),
                mix(background.getBlue(), red.getBlue()));
    }

    private static int mix(int base, int tint) {
        return (int) Math.round(base + (tint - base) * ERROR_TINT);
    }

    public boolean showsError() {
        return "error".equals(getClientProperty("JComponent.outline"));
    }

    /**
     * For tests: handles {@code event} as Swing does once the focus manager hands it to the focused field (a headless
     * field never owns the focus, so {@code dispatchEvent} would drop it).
     */
    public void deliver(KeyEvent event) {
        processKeyEvent(event);
    }

    private void swallowUndoKeystrokes() {
        int mask = ShortcutCatalog.menuMask();
        String actionKey = "search.ignoreUndo";
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), actionKey);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK),
                actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // intentionally empty: consuming the keystroke is the point
            }
        });
    }
}
