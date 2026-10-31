package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
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
 * The context menu of a Terminal («Abrir en ventana», «Limpiar pantalla», and with an external character ROM «Releer
 * la ROM de caracteres»), of a Teclado («Pegar texto», «Vaciar cola») and of a UART 16550 with its integrated screen
 * (the Terminal's two and the Teclado's two). Clearing, rereading, pasting and emptying are simulation requests, never
 * edits (no undo step).
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
        if (TerminalParts.showsScreen(document, component)) {
            addScreenItems(menu, component);
        }
        if (component.typeId().equals(StandardComponentTypes.KEYBOARD)
                || TerminalParts.isIntegratedUart(document, component)) {
            addTypingItems(menu, component.id().value());
        }
    }

    private void addScreenItems(JPopupMenu menu, Component component) {
        menu.add(item("terminal.menu.open", () -> openWindow.accept(component)));
        menu.add(item("terminal.menu.clear", () -> document.clearTerminal(component.id())));
        if (TerminalTypes.externalGlyphs(document.registry().typeOf(component)
                .resolveParameters(component.parameters()))) {
            menu.add(item("terminal.menu.reread-glyphs", () -> document.rereadTerminalGlyphs(component.id())));
        }
    }

    private void addTypingItems(JPopupMenu menu, String componentId) {
        menu.add(item("keyboard.menu.paste", () -> KeyboardPaste.paste(document, componentId,
                clipboardText.get().orElse(""), notices)));
        menu.add(item("keyboard.menu.clear", () -> document.editTyping(componentId, TypingEdit.CLEAR)));
    }

    private static JMenuItem item(String key, Runnable behavior) {
        JMenuItem item = new JMenuItem(Actions.of(Messages.text(key), behavior));
        item.setName(key);
        return item;
    }
}
