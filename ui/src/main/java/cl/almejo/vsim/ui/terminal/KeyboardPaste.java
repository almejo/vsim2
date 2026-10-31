package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Pasting a text into a Teclado (spec §10): Ctrl+V while it has the keyboard and its context menu's «Pegar texto».
 * The text is encoded with the Teclado's own parameters and queued whole characters at a time until the queue is
 * full; when not all of it fits, the status bar says how much went in. Not an edit.
 */
final class KeyboardPaste {

    private KeyboardPaste() {
    }

    /** Types {@code text} into the Teclado {@code componentId}; {@code notices} is told on the EDT if it did not fit. */
    static void paste(OpenDocument openDocument, String componentId, String text, Consumer<String> notices) {
        int total = KeyEncoding.characterCount(text);
        if (total == 0) {
            return;
        }
        List<byte[]> characters = encodingOf(openDocument, componentId).encodeText(text);
        openDocument.typeCharacters(componentId, characters).thenAccept(taken -> {
            if (taken < total) {
                SwingUtilities.invokeLater(() -> notices.accept(
                        Messages.format("keyboard.pasteIncomplete", taken, total)));
            }
        });
    }

    /** The encoding the parameters of {@code componentId} ask for; the Teclado's defaults when it is gone. */
    static KeyEncoding encodingOf(OpenDocument openDocument, String componentId) {
        Optional<Component> component = openDocument.document().component(new ElementId(componentId));
        if (component.isEmpty()) {
            return KeyEncoding.DEFAULT;
        }
        ComponentType type = openDocument.registry().typeOf(component.get());
        return KeyEncoding.forParameters(type.resolveParameters(component.get().parameters()));
    }
}
