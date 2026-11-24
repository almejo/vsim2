package cl.almejo.vsim.ui.panels;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** One listener for «the text changed», whatever the kind of change. */
public final class TextChanges {

    private TextChanges() {
    }

    /** Runs {@code listener} after every insertion, removal or attribute change of {@code component}'s text. */
    public static void onChange(JTextComponent component, Runnable listener) {
        component.getDocument().addDocumentListener(listener(listener));
    }

    /**
     * The document listener behind {@link #onChange}, for the callers that keep it to remove it later or to add it to
     * a document that is not the component's own.
     */
    public static DocumentListener listener(Runnable listener) {
        return new DocumentListener() {
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
        };
    }
}
