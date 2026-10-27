package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;

import java.util.List;
import java.util.Objects;

/** Replaces a sticky note by a changed version of itself; revert restores it exactly. */
public final class ChangeStickyNoteCommand extends BeforeAfterCommand<StickyNote> {

    public ChangeStickyNoteCommand(StickyNote before, StickyNote after) {
        super(Objects.requireNonNull(before, "before"), Objects.requireNonNull(after, "after"));
        if (!before.id().equals(after.id())) {
            throw new IllegalArgumentException("sticky notes differ in id: " + before.id() + " and " + after.id());
        }
    }

    @Override
    protected void set(CircuitDocument document, StickyNote note) {
        document.replace(note);
    }

    @Override
    public String description() {
        if (before.content().getClass() != after.content().getClass()) {
            return "command.stickyNoteKind";
        }
        if (!before.content().equals(after.content())) {
            return contentDescription();
        }
        if (before.color() != after.color()) {
            return "command.colorStickyNote";
        }
        if (before.folded() != after.folded()) {
            return after.folded() ? "command.foldStickyNote" : "command.unfoldStickyNote";
        }
        return "command.resizeStickyNote";
    }

    private String contentDescription() {
        if (before.content() instanceof StickyChecklist oldList && after.content() instanceof StickyChecklist newList
                && isSingleToggle(oldList.items(), newList.items())) {
            return newList.doneCount() > oldList.doneCount() ? "command.tickStickyItem" : "command.untickStickyItem";
        }
        return "command.editStickyNote";
    }

    private static boolean isSingleToggle(List<StickyItem> oldItems, List<StickyItem> newItems) {
        if (oldItems.size() != newItems.size()) {
            return false;
        }
        int flipped = 0;
        for (int index = 0; index < oldItems.size(); index++) {
            StickyItem oldItem = oldItems.get(index);
            StickyItem newItem = newItems.get(index);
            if (!oldItem.text().equals(newItem.text())) {
                return false;
            }
            if (oldItem.done() != newItem.done()) {
                flipped++;
            }
        }
        return flipped == 1;
    }
}
