package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The context menu of a Terminal («Abrir en ventana», «Limpiar pantalla») and of a Teclado («Pegar texto», «Vaciar
 * cola»). Clearing, pasting and emptying are simulation requests, never edits (no undo step).
 */
final class TerminalContextMenu implements ContextMenuContributor {

    private final OpenDocument document;
    private final Consumer<Component> openWindow;
    private final Supplier<Optional<String>> clipboardText;
    private final Consumer<String> notices;

    TerminalContextMenu(OpenDocument document, Consumer<Component> openWindow,
                        Supplier<Optional<String>> clipboardText, Consumer<String> notices) {
        this.document = Objects.requireNonNull(document, "document");
        this.openWindow = Objects.requireNonNull(openWindow, "openWindow");
        this.clipboardText = Objects.requireNonNull(clipboardText, "clipboardText");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Override
    public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                           Point2D gridPosition) {
        if (selection.ids().size() > 1 || target.isEmpty() || !(target.get() instanceof Component component)) {
            return;
        }
        String componentId = component.id().value();
        switch (component.typeId()) {
            case StandardComponentTypes.TERMINAL -> {
                menu.add(item("terminal.menu.open", () -> openWindow.accept(component)));
                menu.add(item("terminal.menu.clear", () -> document.clearTerminal(component.id())));
            }
            case StandardComponentTypes.KEYBOARD -> {
                menu.add(item("keyboard.menu.paste", () -> KeyboardPaste.paste(document, componentId,
                        clipboardText.get().orElse(""), notices)));
                menu.add(item("keyboard.menu.clear", () -> document.editTyping(componentId, TypingEdit.CLEAR)));
            }
            default -> {
                // not a part of this feature
            }
        }
    }

    private static JMenuItem item(String key, Runnable behavior) {
        JMenuItem item = new JMenuItem(Actions.of(Messages.text(key), behavior));
        item.setName(key);
        return item;
    }
}
