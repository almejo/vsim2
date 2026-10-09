package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.RemoveWireSegmentCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Label edits of one editor: component labels, free texts and cutting wire stretches. Each one is one undo step. */
public final class LabelActions {

    private final OpenDocument openDocument;
    private final Selection selection;
    private final java.awt.Component parent;
    private final TextPrompt prompt;
    private final Action editLabel;

    public LabelActions(OpenDocument openDocument, Selection selection, java.awt.Component parent, TextPrompt prompt) {
        this.openDocument = openDocument;
        this.selection = selection;
        this.parent = parent;
        this.prompt = prompt;
        editLabel = new AbstractAction(Messages.text("action.label")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                singleLabelled().ifPresent(LabelActions.this::editLabel);
            }
        };
        editLabel.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.label"));
        selection.addListener(this::refresh);
        refresh();
    }

    /** "Etiqueta…" for the single selected component or free text (F2, Edit menu). */
    public Action editLabel() {
        return editLabel;
    }

    /** Asks for a component's label (blank removes it) or a free text's text (blank is ignored). */
    public void editLabel(DocumentElement element) {
        Optional<DocumentElement> current = openDocument.document().element(element.id());
        if (current.isEmpty()) {
            return;
        }
        switch (current.get()) {
            case Component component -> prompt.ask(parent, Messages.text("dialog.label.title"),
                            Messages.format("dialog.label.message",
                                    Messages.componentName(openDocument.registry().require(component.typeId()))),
                            component.label())
                    .map(String::strip)
                    .filter(text -> !text.equals(component.label()))
                    .ifPresent(text -> openDocument.execute(new SetLabelCommand(component.id(), text)));
            case TextLabel label -> prompt.ask(parent, Messages.text("dialog.text.title"),
                            Messages.text("dialog.text.message"), label.text())
                    .map(String::strip)
                    .filter(text -> !text.isEmpty() && !text.equals(label.text()))
                    .ifPresent(text -> openDocument.execute(new SetLabelCommand(label.id(), text)));
            case Wire wire -> {
            }
        }
    }

    /** Asks for a text and adds it as a free text at {@code point}, selected; blank adds nothing. */
    public void addTextAt(GridPoint point) {
        prompt.ask(parent, Messages.text("dialog.addText.title"), Messages.text("dialog.text.message"), "")
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> {
                    TextLabel label = new TextLabel(openDocument.document().newLabelId(), point, text);
                    openDocument.execute(new AddElementsCommand(List.of(label)));
                    selection.select(label.id());
                });
    }

    /** Removes the stretch of {@code wire} around {@code near} ("Borrar tramo"). */
    public void removeWireSegment(Wire wire, GridPoint near) {
        if (openDocument.document().wire(wire.id()).isPresent()) {
            openDocument.execute(new RemoveWireSegmentCommand(wire.id(), near, openDocument.registry()));
        }
    }

    private void refresh() {
        editLabel.setEnabled(singleLabelled().isPresent());
    }

    private Optional<DocumentElement> singleLabelled() {
        Set<ElementId> ids = selection.ids();
        if (ids.size() != 1) {
            return Optional.empty();
        }
        return openDocument.document().element(ids.iterator().next()).filter(element -> !(element instanceof Wire));
    }
}
