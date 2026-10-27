package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;

import java.util.List;

public final class SetLabelCommand implements Command {

    private final ElementId id;
    private final String text;
    private DocumentElement previousElement;
    private String descriptionKey = "command.label";

    public SetLabelCommand(ElementId id, String text) {
        this.id = id;
        this.text = text == null ? "" : text;
    }

    @Override
    public void apply(CircuitDocument document) {
        DocumentElement element = document.element(id).orElseThrow();
        previousElement = element;
        descriptionKey = descriptionKeyFor(element);
        document.replace(withText(element, text));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(previousElement);
    }

    private static DocumentElement withText(DocumentElement element, String newText) {
        return switch (element) {
            case Component component -> component.withLabel(newText);
            case TextLabel label -> label.withText(newText);
            case Frame frame -> frame.withTitle(newText);
            case StickyNote note -> note.withContent(stickyContent(note, newText));
            case Wire wire -> throw new IllegalArgumentException("wires have no label: " + wire.id());
        };
    }

    private static String descriptionKeyFor(DocumentElement element) {
        return switch (element) {
            case Frame ignored -> "command.renameFrame";
            case StickyNote ignored -> "command.editStickyNote";
            default -> "command.label";
        };
    }

    /** A free-text note takes the text as is; a checklist takes one item per line, keeping the ticks of unchanged lines. */
    private static StickyContent stickyContent(StickyNote note, String newText) {
        return switch (note.content()) {
            case StickyText ignored -> new StickyText(newText);
            case StickyChecklist checklist -> checklist.edited(List.of(newText.split("\n", -1)));
        };
    }

    @Override
    public String description() {
        return descriptionKey;
    }
}
