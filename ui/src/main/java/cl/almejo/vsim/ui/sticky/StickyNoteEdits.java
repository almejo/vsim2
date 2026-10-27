package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.ChangeStickyNoteCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The edits of one sticky note that the gestures, its menu and Propiedades share. Each acts on the note as it is in
 * the document now, is one undo step, does nothing when it changes nothing, and is refused while a locked frame holds
 * the note ({@link StickyNoteLock}).
 */
public final class StickyNoteEdits {

    private final OpenDocument openDocument;

    public StickyNoteEdits(OpenDocument openDocument) {
        this.openDocument = openDocument;
    }

    /** The note as it is now, when it is still there and no locked frame holds it. */
    Optional<StickyNote> editable(ElementId id) {
        return openDocument.document().stickyNote(id).filter(note -> !StickyNoteLock.isLocked(openDocument, id));
    }

    /** {@code change} applied to the note as one {@link ChangeStickyNoteCommand}. */
    public void change(ElementId id, UnaryOperator<StickyNote> change) {
        editable(id).ifPresent(before -> {
            StickyNote after = change.apply(before);
            if (!after.equals(before)) {
                openDocument.execute(new ChangeStickyNoteCommand(before, after));
            }
        });
    }

    public void setColor(ElementId id, StickyColor color) {
        change(id, note -> note.withColor(color));
    }

    /** Free text ({@code checklist} false) or a checklist, by the conversion rules of {@code StickyContent}. */
    public void setKind(ElementId id, boolean checklist) {
        change(id, note -> note.withContent(checklist ? note.content().toChecklist() : note.content().toText()));
    }

    public void setFolded(ElementId id, boolean folded) {
        change(id, note -> note.withFolded(folded));
    }

    /** Ticks or unticks item {@code itemIndex} of a checklist note. */
    public void toggle(ElementId id, int itemIndex) {
        change(id, note -> note.content() instanceof StickyChecklist checklist
                && itemIndex >= 0 && itemIndex < checklist.items().size()
                ? note.withContent(checklist.toggled(itemIndex)) : note);
    }

    /** Asks {@code confirmation} and, on «Despegar», removes the note: one undo step. Cancelling changes nothing. */
    public void close(ElementId id, StickyCloseConfirmation confirmation, Component parent) {
        Optional<StickyNote> note = editable(id);
        if (note.isEmpty() || !confirmation.confirm(parent, note.get().content().firstLine())) {
            return;
        }
        if (openDocument.document().stickyNote(id).isPresent()) {
            openDocument.execute(new RemoveElementsCommand(List.of(id)));
        }
    }
}
