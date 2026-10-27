package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Comparator;
import java.util.List;

/** One row of the «Notas» tab: the swatch colour and the text a sticky note shows there. */
public record StickyNoteRow(ElementId id, StickyColor color, String text) {

    /** Top to bottom, then left to right. */
    private static final Comparator<StickyNote> READING_ORDER = Comparator
            .comparingInt((StickyNote note) -> note.position().y())
            .thenComparingInt(note -> note.position().x())
            .thenComparing(note -> note.id().value());

    /** The rows of {@code notes} in reading order. */
    public static List<StickyNoteRow> of(List<StickyNote> notes) {
        return notes.stream().sorted(READING_ORDER).map(StickyNoteRow::of).toList();
    }

    public static StickyNoteRow of(StickyNote note) {
        String text = rowText(note.content());
        if (text.isEmpty()) {
            text = Messages.text("stickyNotes.rowEmpty");
        }
        if (note.folded()) {
            text = text + " " + Messages.text("stickyNotes.rowFolded");
        }
        return new StickyNoteRow(note.id(), note.color(), text);
    }

    private static String rowText(StickyContent content) {
        if (!(content instanceof StickyChecklist checklist) || checklist.items().isEmpty()) {
            return content.firstLine();
        }
        List<StickyItem> items = checklist.items();
        if (checklist.doneCount() == items.size()) {
            return Messages.text("stickyNotes.rowAllDone");
        }
        return Messages.format("stickyNotes.rowProgress", content.firstLine(), checklist.doneCount(), items.size());
    }
}
