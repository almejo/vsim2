package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The text of a note in Propiedades (IA.3): a text area that keeps its line breaks, where Enter confirms like a
 * field's Enter, Shift+Enter adds a line and Tab moves on to the next control. It grows with its lines.
 */
final class NoteTextArea extends JTextArea {

    private static final String CONFIRM = "note-confirm";
    private final List<Runnable> confirmListeners = new ArrayList<>();

    NoteTextArea(String text) {
        super(text);
        FragmentRefusingTransferHandler.install(this);
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CONFIRM);
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                DefaultEditorKit.insertBreakAction);
        getActionMap().put(CONFIRM, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                confirm();
            }
        });
        // Tab and Shift+Tab move the focus as in the other fields instead of typing a tab
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // a text area has no field border: borrow the field's, like the other rows (also after a theme change)
        setBorder(UIManager.getBorder("TextField.border"));
    }

    /** Runs {@code listener} when the user confirms the text (Enter). */
    void addConfirmListener(Runnable listener) {
        confirmListeners.add(listener);
    }

    /** What Enter does: tells the listeners the text is confirmed. */
    void confirm() {
        for (Runnable listener : List.copyOf(confirmListeners)) {
            listener.run();
        }
    }

    /** For tests: handles {@code event} as Swing does once the focus manager hands it to the focused area. */
    void deliver(KeyEvent event) {
        processKeyEvent(event);
    }
}
